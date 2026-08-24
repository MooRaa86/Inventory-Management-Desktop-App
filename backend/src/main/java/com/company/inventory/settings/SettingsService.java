package com.company.inventory.settings;

import com.company.inventory.common.error.BusinessRuleException;
import com.company.inventory.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key/value application settings stored in the system_settings table with an
 * in-memory read cache. Settings are business data: they live in the database
 * (and therefore in every backup), not in config files.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String COMPANY_NAME = "company.name";
    public static final String COMPANY_CURRENCY = "company.currency";
    public static final String APP_NAME = "app.name";
    public static final String BACKUP_ENABLED = "backup.enabled";
    public static final String BACKUP_TIME = "backup.time";
    public static final String BACKUP_RETENTION_COUNT = "backup.retention.count";
    public static final String BACKUP_AUTO_CLEANUP = "backup.auto.cleanup";
    public static final String BACKUP_VERIFICATION = "backup.verification";
    public static final String SESSION_TIMEOUT_MINUTES = "session.timeout.minutes";
    public static final String INVENTORY_ALLOW_NEGATIVE = "inventory.allow_negative";

    private static final Set<String> EDITABLE_KEYS = Set.of(
            COMPANY_NAME, COMPANY_CURRENCY, APP_NAME,
            BACKUP_ENABLED, BACKUP_TIME, BACKUP_RETENTION_COUNT, BACKUP_AUTO_CLEANUP,
            BACKUP_VERIFICATION,
            SESSION_TIMEOUT_MINUTES,
            INVENTORY_ALLOW_NEGATIVE);

    private final SystemSettingRepository repository;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Transactional
    public String get(String key) {
        return cache.computeIfAbsent(key, k -> repository.findById(k)
                .map(SystemSetting::getValue)
                .orElse(""));
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v.equalsIgnoreCase("true") || v.equals("1");
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional
    public void set(String key, String value, boolean validate) {
        if (!EDITABLE_KEYS.contains(key)) {
            throw new BusinessRuleException("UNKNOWN_SETTING", "Unknown setting key: " + key);
        }
        if (validate) {
            validateValue(key, value);
        }
        SystemSetting setting = repository.findById(key).orElseGet(() -> {
            SystemSetting s = new SystemSetting();
            s.setKey(key);
            return s;
        });
        setting.setValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(currentUsername());
        repository.save(setting);
        cache.put(key, value);
    }

    public Map<String, String> getAllEditable() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : EDITABLE_KEYS.stream().sorted().toList()) {
            result.put(key, get(key));
        }
        return result;
    }

    public void evictCache() {
        cache.clear();
    }

    private void validateValue(String key, String value) {
        switch (key) {
            case BACKUP_ENABLED, BACKUP_AUTO_CLEANUP, BACKUP_VERIFICATION, INVENTORY_ALLOW_NEGATIVE ->
                    requireBool(key, value);
            case BACKUP_RETENTION_COUNT -> {
                int n = parseIntOrThrow(value);
                if (n < 1 || n > 365) {
                    throw new BusinessRuleException("INVALID_SETTING",
                            "Retention count must be between 1 and 365.");
                }
            }
            case SESSION_TIMEOUT_MINUTES -> {
                int n = parseIntOrThrow(value);
                if (n < 5 || n > 1440) {
                    throw new BusinessRuleException("INVALID_SETTING",
                            "Session timeout must be between 5 and 1440 minutes.");
                }
            }
            case BACKUP_TIME -> {
                if (!value.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                    throw new BusinessRuleException("INVALID_SETTING",
                            "Backup time must use HH:mm (24h) format.");
                }
            }
            case COMPANY_CURRENCY -> {
                if (!value.matches("^[A-Z]{3}$")) {
                    throw new BusinessRuleException("INVALID_SETTING",
                            "Currency must be a 3-letter ISO code (e.g. USD).");
                }
            }
            case COMPANY_NAME -> {
                if (value.isBlank() || value.length() > 200) {
                    throw new BusinessRuleException("INVALID_SETTING",
                            "Company name must be 1-200 characters.");
                }
            }
            default -> {
            }
        }
    }

    private void requireBool(String key, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BusinessRuleException("INVALID_SETTING", key + " must be true or false.");
        }
    }

    private int parseIntOrThrow(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("INVALID_SETTING", "Value must be a whole number.");
        }
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u.username();
        }
        return "system";
    }
}
