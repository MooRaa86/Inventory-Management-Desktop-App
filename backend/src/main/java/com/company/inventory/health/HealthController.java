package com.company.inventory.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final String appVersion;

    public HealthController(JdbcTemplate jdbcTemplate,
                            @Value("${app.version:1.0.0}") String appVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.appVersion = appVersion;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("version", appVersion);
        body.put("time", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> dbHealth() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("status", result != null && result == 1 ? "UP" : "DEGRADED");
            body.put("database", "SQLite");
        } catch (Exception e) {
            log.warn("Database health check failed", e);
            body.put("status", "DOWN");
        }
        body.put("time", Instant.now().toString());
        return ResponseEntity.ok(body);
    }
}
