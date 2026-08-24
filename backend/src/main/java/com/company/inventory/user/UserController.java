package com.company.inventory.user;

import com.company.inventory.audit.AuditActions;
import com.company.inventory.common.error.ApiException;
import com.company.inventory.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final com.company.inventory.audit.AuditService auditService;
    private final com.company.inventory.security.AuthenticatedUserAccessor accessor;

    public record UserDto(Long id, String username, String email, String fullName,
                          boolean active, boolean mustChangePassword,
                          LocalDateTime lastLoginAt, LocalDateTime createdAt,
                          List<String> roles) {

        static UserDto from(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
                    u.isActive(), u.isMustChangePassword(), u.getLastLoginAt(), u.getCreatedAt(),
                    u.getRoles().stream().map(Role::getName).sorted().toList());
        }
    }

    public record CreateUserRequest(String username, String email, String fullName,
                                    String password, List<String> roles) {
    }

    public record UpdateUserRequest(String email, String fullName) {
    }

    public record SetPasswordRequest(String newPassword, boolean mustChangePassword) {
    }

    public record SetRolesRequest(List<String> roles) {
    }

    public record RoleDto(String name, String description, List<String> permissions) {
        static RoleDto from(Role r) {
            return new RoleDto(r.getName(), r.getDescription(),
                    r.getPermissions().stream().map(p ->
                            p.getCode()).sorted().collect(Collectors.toList()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public PageResponse<UserDto> search(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) Boolean active,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        List<User> all = userRepository.findAll(Sort.by(Sort.Direction.ASC, "username"));
        List<UserDto> filtered = all.stream()
                .filter(u -> search == null || search.isBlank()
                        || u.getUsername().toLowerCase().contains(search.toLowerCase())
                        || u.getFullName().toLowerCase().contains(search.toLowerCase())
                        || u.getEmail().toLowerCase().contains(search.toLowerCase()))
                .filter(u -> active == null || u.isActive() == active)
                .map(UserDto::from)
                .toList();
        int safeSize = Math.max(size, 1);
        int from = Math.min(Math.max(page, 0) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        return new PageResponse<>(filtered.subList(from, to), Math.max(page, 0),
                safeSize, filtered.size(),
                (int) Math.ceil((double) filtered.size() / safeSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserDto get(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        return UserDto.from(user);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public UserDto create(@RequestBody CreateUserRequest req) {
        validateNew(req);
        if (userRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new ApiException(422, "DUPLICATE_USERNAME", "Username already exists.");
        }
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw new ApiException(422, "DUPLICATE_EMAIL", "Email already exists.");
        }
        User user = new User();
        applyCommon(user, req.username(), req.email(), req.fullName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setMustChangePassword(true);
        user.setRoles(resolveRoles(req.roles()));
        User saved = userRepository.save(user);
        auditService.log(AuditActions.USER_CREATE, "user",
                saved.getId(), "Created user " + saved.getUsername(), null);
        return UserDto.from(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserDto update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        if (req.email() != null && !req.email().isBlank()) {
            if (!EMAIL.matcher(req.email()).matches()) {
                throw new ApiException(422, "INVALID_EMAIL", "Email format is invalid.");
            }
            userRepository.findByEmailIgnoreCase(req.email())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ApiException(422, "DUPLICATE_EMAIL", "Email already in use.");
                    });
            user.setEmail(req.email());
        }
        if (req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName().trim());
        }
        User saved = userRepository.save(user);
        auditService.log(AuditActions.USER_UPDATE, "user",
                saved.getId(), "Updated profile of " + saved.getUsername(), null);
        return UserDto.from(saved);
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserDto setPassword(@PathVariable Long id, @RequestBody SetPasswordRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        validatePassword(req.newPassword());
        transactionTemplate.executeWithoutResult(s -> {
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            user.setMustChangePassword(req.mustChangePassword());
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });
        auditService.log(AuditActions.USER_PASSWORD_RESET, "user",
                user.getId(), "Password reset for " + user.getUsername(), null);
        return UserDto.from(userRepository.findById(id).orElseThrow());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public UserDto setRoles(@PathVariable Long id, @RequestBody SetRolesRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        assertAdminNotDemoted(user, req.roles());
        user.setRoles(resolveRoles(req.roles()));
        User saved = userRepository.save(user);
        auditService.log(AuditActions.ROLE_ASSIGN, "user",
                saved.getId(), "Roles for " + saved.getUsername() + " set to "
                        + String.join(",", req.roles()), null);
        return UserDto.from(saved);
    }

    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('USER_DISABLE')")
    public UserDto setActive(@PathVariable Long id, @RequestParam boolean value) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        com.company.inventory.security.AuthenticatedUser current = accessor.currentUser();
        if (current != null && current.id().equals(user.getId()) && !value) {
            throw new ApiException(422, "CANNOT_DISABLE_SELF",
                    "You cannot deactivate your own account.");
        }
        if (!value && user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()))) {
            assertAdminNotDemoted(user, List.of());
        }
        user.setActive(value);
        if (!value) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        User saved = userRepository.save(user);
        auditService.log(value ? AuditActions.USER_ENABLE : AuditActions.USER_DISABLE,
                "user", saved.getId(),
                (value ? "Enabled" : "Disabled") + " user " + saved.getUsername());
        return UserDto.from(saved);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_VIEW') || hasAuthority('ROLE_ASSIGN')")
    public List<RoleDto> roles() {
        return roleRepository.findAll(Sort.by("name")).stream().map(RoleDto::from).toList();
    }

    private void assertAdminNotDemoted(User user, List<String> newRoles) {
        boolean currentlyAdmin = user.getRoles().stream()
                .anyMatch(r -> "ADMIN".equals(r.getName()));
        if (currentlyAdmin && (newRoles == null || !newRoles.contains("ADMIN"))) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.isActive())
                    .filter(u -> u.getRoles().stream()
                            .anyMatch(r -> "ADMIN".equals(r.getName())))
                    .count();
            if (adminCount <= 1) {
                throw new ApiException(422, "LAST_ADMIN",
                        "Cannot remove the last active ADMIN role assignment.");
            }
        }
    }

    private void validateNew(CreateUserRequest req) {
        if (req.username() == null || !USERNAME.matcher(req.username()).matches()) {
            throw new ApiException(422, "INVALID_USERNAME",
                    "Username must be 3-50 chars: letters, digits, dot, dash, underscore.");
        }
        if (req.email() == null || !EMAIL.matcher(req.email()).matches()) {
            throw new ApiException(422, "INVALID_EMAIL", "Email format is invalid.");
        }
        if (req.fullName() == null || req.fullName().isBlank()) {
            throw new ApiException(422, "INVALID_NAME", "Full name is required.");
        }
        validatePassword(req.password());
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            throw new ApiException(422, "WEAK_PASSWORD",
                    "Password must be at least 8 characters and contain letters and digits.");
        }
    }

    private void applyCommon(User user, String username, String email, String fullName) {
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setFullName(fullName.trim());
        user.setActive(true);
    }

    private Set<Role> resolveRoles(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new ApiException(422, "ROLES_REQUIRED", "At least one role is required.");
        }
        Set<Role> roles = names.stream().distinct()
                .map(n -> roleRepository.findByNameIgnoreCase(n.toUpperCase())
                        .orElseThrow(() -> new ApiException(422, "ROLE_NOT_FOUND",
                                "Unknown role: " + n)))
                .collect(Collectors.toSet());
        if (roles.isEmpty()) {
            throw new ApiException(422, "ROLES_REQUIRED", "At least one role is required.");
        }
        return roles;
    }
}
