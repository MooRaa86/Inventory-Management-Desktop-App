package com.company.inventory.auth;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.common.error.ApiException;
import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.security.JwtService;
import com.company.inventory.startup.AppPaths;
import com.company.inventory.user.PermissionRepository;
import com.company.inventory.user.User;
import com.company.inventory.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 5;

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AppPaths appPaths;
    private final long sessionTimeoutMinutes;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    public AuthService(UserRepository userRepository,
                       PermissionRepository permissionRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       AppPaths appPaths,
                       @Value("${app.security.session-timeout-minutes:30}") long sessionTimeoutMinutes) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.appPaths = appPaths;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    @Transactional
    public LoginResponse login(String usernameOrEmail, String rawPassword) {
        User user = userRepository.findByUsernameIgnoreCase(usernameOrEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(usernameOrEmail))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            if (user != null) {
                registerFailedAttempt(user);
            }
            log.info("Failed login attempt");
            throw new ApiException(401, "BAD_CREDENTIALS", "Invalid username or password.");
        }

        if (!user.isActive()) {
            throw new ApiException(403, "ACCOUNT_DISABLED",
                    "This account has been disabled. Contact an administrator.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(423, "ACCOUNT_LOCKED",
                    "Account temporarily locked due to repeated failed attempts. Try again later.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userRepository.save(user);

        List<String> permissions = permissionCodes(user.getUsername());
        String token = jwtService.issueToken(user.getId(), user.getUsername(), permissions,
                Duration.ofMinutes(sessionTimeoutMinutes));

        auditService.log(AuditActions.LOGIN, "USER", user.getId(),
                "User logged in", Map.of("username", user.getUsername()));

        return new LoginResponse(token, sessionTimeoutMinutes, toSummary(user));
    }

    @Transactional
    public Map<String, String> resetAdmin() {
        User admin = userRepository.findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new ApiException(404, "ADMIN_NOT_FOUND",
                        "Default administrator account '" + adminUsername + "' not found."));

        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setActive(true);
        admin.setFailedLoginAttempts(0);
        admin.setLockedUntil(null);
        admin.setMustChangePassword(false);
        admin.setLastLoginAt(null);
        userRepository.save(admin);

        auditService.log(AuditActions.ADMIN_PASSWORD_RESET, "USER", admin.getId(),
                "Administrator credentials reset to factory defaults.",
                Map.of("target", admin.getUsername(),
                        "generatedPassword", "REDACTED"));

        try {
            Path credFile = appPaths.config().resolve("reset-admin-credentials.txt");
            Files.writeString(credFile, """
                            Company Inventory - Reset Administrator Credentials
                            ===================================================
                            Username: %s
                            Password: %s

                            SECURITY: Please log in and change this password if desired.
                            This file can be deleted after the password has been changed.
                            """.formatted(admin.getUsername(), adminPassword),
                    StandardCharsets.UTF_8);
            log.info("Reset admin credentials written to {}", credFile);
        } catch (Exception e) {
            log.error("Failed to write reset admin credentials file", e);
        }

        return Map.of("username", admin.getUsername(),
                "email", admin.getEmail(),
                "message", "Administrator credentials have been reset to factory defaults. Contact the administrator at mr.omarmedhat@gmail.com to obtain the new credentials.");
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("WRONG_CURRENT_PASSWORD", "Current password is incorrect.");
        }
        validateNewPassword(user, newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        auditService.log(AuditActions.PASSWORD_CHANGED, "USER", user.getId(),
                "Password changed for user '" + user.getUsername() + "'");
    }

    public void validateNewPassword(User targetUser, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new BusinessRuleException("WEAK_PASSWORD", "Password must be 8-72 characters long.");
        }
        if (newPassword.toLowerCase().contains(targetUser.getUsername().toLowerCase())) {
            throw new BusinessRuleException("WEAK_PASSWORD", "Password must not contain the username.");
        }
        boolean hasLetter = newPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessRuleException("WEAK_PASSWORD",
                    "Password must contain at least one letter and one digit.");
        }
    }

    public LoginResponse.UserSummary toSummary(User user) {
        return new LoginResponse.UserSummary(user.getId(), user.getUsername(), user.getFullName(),
                user.getEmail(), user.isMustChangePassword(),
                user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet()),
                permissionCodes(user.getUsername()));
    }

    @Transactional(readOnly = true)
    public List<String> permissionCodes(String username) {
        return permissionRepository.findCodesByUsername(username);
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            user.setFailedLoginAttempts(0);
            log.warn("Account temporarily locked after repeated failed attempts");
        }
        userRepository.save(user);
    }
}
