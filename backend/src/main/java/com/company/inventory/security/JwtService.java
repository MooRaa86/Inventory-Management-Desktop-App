package com.company.inventory.security;

import com.company.inventory.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class JwtService {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_PERMS = "perms";

    private final JwtSecretProvider secretProvider;
    private final UserRepository userRepository;
    private final long defaultTimeoutMinutes;

    public JwtService(JwtSecretProvider secretProvider,
                      UserRepository userRepository,
                      @Value("${app.security.session-timeout-minutes:30}") long defaultTimeoutMinutes) {
        this.secretProvider = secretProvider;
        this.userRepository = userRepository;
        this.defaultTimeoutMinutes = defaultTimeoutMinutes;
    }

    public String issueToken(Long userId, String username, List<String> permissions, Duration ttl) {
        SecretKey key = secretProvider.secretKey();
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_PERMS, permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public String issueToken(Long userId, String username, List<String> permissions) {
        return issueToken(userId, username, permissions,
                Duration.ofMinutes(defaultTimeoutMinutes));
    }

    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretProvider.secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Re-derives the current permission set from the database for the token subject.
     * Ensures revoked roles/permissions and disabled users take effect immediately
     * without waiting for token expiry.
     */
    public boolean isUserStillValid(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(u -> u.isActive())
                .orElse(false);
    }
}
