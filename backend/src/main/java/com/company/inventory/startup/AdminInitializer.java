package com.company.inventory.startup;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.user.Role;
import com.company.inventory.user.RoleRepository;
import com.company.inventory.user.User;
import com.company.inventory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Set;

/**
 * Creates the initial ADMIN account on first run.
 * Credential precedence: environment variables > config/application.properties
 * > launcher-generated random password (written once to config/initial-admin-credentials.txt).
 * Never hardcodes credentials in source code.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AppPaths appPaths;

    @Value("${app.admin.username:}")
    private String configuredUsername;

    @Value("${app.admin.email:}")
    private String configuredEmail;

    @Value("${app.admin.password:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (userRepository.countAdmins() > 0) {
            log.info("Admin account already exists; skipping bootstrap");
            return;
        }

        String username = resolve(System.getenv("ADMIN_USERNAME"), configuredUsername,
                DEFAULT_ADMIN_USERNAME);
        if (username.isBlank()) {
            username = DEFAULT_ADMIN_USERNAME;
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            log.warn("Bootstrap skipped: user '{}' already exists but has no ADMIN role", username);
            return;
        }
        String email = resolve(System.getenv("ADMIN_EMAIL"), configuredEmail, "");
        if (email.isBlank()) {
            email = username + "@localhost.local";
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            email = "admin." + System.currentTimeMillis() + "@localhost.local";
        }

        String envPassword = System.getenv("ADMIN_PASSWORD");
        boolean generated = false;
        String password;
        if (envPassword != null && !envPassword.isBlank()) {
            password = envPassword.trim();
        } else if (!configuredPassword.isBlank()) {
            password = configuredPassword.trim();
        } else {
            password = generatePassword();
            generated = true;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setFullName("System Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setActive(true);
        admin.setMustChangePassword(generated);
        Role adminRole = roleRepository.findByNameIgnoreCase("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing from database"));
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        auditService.log(AuditActions.ADMIN_BOOTSTRAP, "USER", admin.getId(),
                "Initial administrator account '" + username + "' created");

        Path credFile = appPaths.config().resolve("initial-admin-credentials.txt");
        Files.writeString(credFile, """
                        Company Inventory - Initial Administrator Account
                        =================================================
                        Username: %s
                        Password: %s

                        SECURITY: Please log in and change this password if desired.
                        This file can be deleted after the password has been changed.
                        """.formatted(username, password),
                StandardCharsets.UTF_8);
        log.info("Admin credentials written to {}", credFile);
    }

    private String resolve(String env, String configured, String fallback) {
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return fallback;
    }

    private String generatePassword() {
        final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String lower = "abcdefghijkmnopqrstuvwxyz";
        final String digits = "23456789";
        final String all = upper + lower + digits + "!@#$%&*";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        for (int i = 3; i < 16; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        return sb.toString();
    }
}
