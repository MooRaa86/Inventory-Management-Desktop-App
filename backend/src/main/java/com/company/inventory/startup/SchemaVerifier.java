package com.company.inventory.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Replaces Hibernate's schema validation (unusable against SQLite type affinity)
 * with three concrete guarantees:
 * 1. Flyway migration checksum validation - catches any drift/tampering.
 * 2. SQLite integrity_check on the database file.
 * 3. Presence of all required tables.
 */
@Slf4j
@Component
@Order(Integer.MIN_VALUE + 50)
@RequiredArgsConstructor
public class SchemaVerifier implements ApplicationRunner {

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "users", "roles", "permissions", "user_roles", "role_permissions",
            "categories", "units", "suppliers", "products",
            "purchases", "purchase_items", "issues", "issue_items",
            "stock_movements", "audit_logs", "backups", "system_settings");

    private final DataSource dataSource;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        validateFlyway();
        verifyIntegrity();
        verifyTables();
        log.info("Schema verification passed");
    }

    private void validateFlyway() {
        FluentConfiguration config = Flyway.configure()
                .dataSource(dataSource)
                .locations(environment.getProperty("spring.flyway.locations", "classpath:db/migration")
                        .split(","));
        config.load().validate();
    }

    private void verifyIntegrity() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            String result = rs.next() ? rs.getString(1) : "";
            if (!"ok".equalsIgnoreCase(result)) {
                throw new IllegalStateException("SQLite integrity check failed: " + result);
            }
        }
    }

    private void verifyTables() throws Exception {
        Set<String> found = new LinkedHashSet<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (rs.next()) {
                found.add(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        var missing = REQUIRED_TABLES.stream().filter(t -> !found.contains(t)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Database schema is missing required tables: " + missing);
        }
    }
}
