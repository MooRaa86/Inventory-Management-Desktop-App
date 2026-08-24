package com.company.inventory.auth;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public record LoginResponse(String token, long expiresInMinutes, UserSummary user) {

    public record UserSummary(Long id, String username, String fullName, String email,
                              boolean mustChangePassword, Set<String> roles, List<String> permissions) {
    }
}
