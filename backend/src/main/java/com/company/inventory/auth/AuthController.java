package com.company.inventory.auth;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.audit.AuditService;
import com.company.inventory.common.error.ApiException;
import com.company.inventory.security.AuthenticatedUser;
import com.company.inventory.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required") String newPassword) {
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.usernameOrEmail(), request.password()));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserSummary> me() {
        AuthenticatedUser principal = currentPrincipal();
        var user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ApiException(401, "UNAUTHORIZED", "Session user no longer exists."));
        return ResponseEntity.ok(authService.toSummary(user));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser principal = currentPrincipal();
        authService.changePassword(principal.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        AuthenticatedUser principal = currentPrincipal();
        auditService.log(AuditActions.LOGOUT, "USER", principal.id(), "User logged out");
        return ResponseEntity.ok(Map.of("message", "Logged out."));
    }

    private AuthenticatedUser currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new ApiException(401, "UNAUTHORIZED", "Authentication required.");
        }
        return principal;
    }
}
