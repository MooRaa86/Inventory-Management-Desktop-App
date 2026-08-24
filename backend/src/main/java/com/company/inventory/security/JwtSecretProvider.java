package com.company.inventory.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Component
public class JwtSecretProvider {

    private final Path secretFile;
    private final String envSecret;

    private String cachedSecret;

    public JwtSecretProvider(@Value("${app.root:.}") String root,
                             @Value("${app.security.jwt-secret-env:INVENTORY_JWT_SECRET}") String envVarName) {
        this.secretFile = Path.of(root).toAbsolutePath().normalize()
                .resolve("config").resolve("jwt-secret.key");
        String env = System.getenv(envVarName);
        this.envSecret = (env != null && !env.isBlank()) ? env.trim() : null;
    }

    public synchronized String getSecret() {
        if (cachedSecret != null) {
            return cachedSecret;
        }
        if (envSecret != null) {
            log.info("JWT secret loaded from environment variable");
            cachedSecret = normalize(envSecret);
            return cachedSecret;
        }
        try {
            if (Files.exists(secretFile)) {
                String content = Files.readString(secretFile, StandardCharsets.UTF_8).trim();
                if (content.length() < 32) {
                    throw new IllegalStateException("JWT secret file is too short; regenerate it by deleting " + secretFile);
                }
                log.info("JWT secret loaded from {}", secretFile);
                cachedSecret = normalize(content);
                return cachedSecret;
            }
            byte[] bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            String generated = HexFormat.of().formatHex(bytes);
            Files.writeString(secretFile, generated, StandardCharsets.UTF_8);
            restrictPermissions(secretFile);
            log.info("Generated new JWT secret at {}", secretFile);
            cachedSecret = normalize(generated);
            return cachedSecret;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read or create JWT secret file: " + secretFile, e);
        }
    }

    private String normalize(String raw) {
        try {
            return Base64.getDecoder().decode(raw).length >= 32 ? raw : Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    public javax.crypto.SecretKey secretKey() {
        byte[] material = getSecret().getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new javax.crypto.spec.SecretKeySpec(digest.digest(material), "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @SuppressWarnings("unused")
    private void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, java.util.Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows filesystems do not support POSIX permissions.
        }
    }
}
