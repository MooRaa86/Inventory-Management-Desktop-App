package com.company.inventory.security;

import com.company.inventory.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record AuthenticatedUser(Long id, String username, String fullName, boolean active,
                                Set<String> roles, Set<String> permissions) implements UserDetails {

    public static AuthenticatedUser of(User user, Set<String> permissionCodes) {
        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toSet());
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getFullName(),
                user.isActive(), Set.copyOf(roles), Set.copyOf(permissionCodes));
    }

    public boolean hasPermission(String code) {
        return permissions.contains(code);
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
