package com.company.inventory.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Small helper for controllers/services that need the acting user's identity
 * (for audit metadata) without depending on web-layer request objects.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedUserAccessor {

    public AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    public Long userId() {
        AuthenticatedUser user = currentUser();
        return user == null ? null : user.id();
    }

    public String username() {
        AuthenticatedUser user = currentUser();
        return user == null ? "system" : user.getUsername();
    }
}
