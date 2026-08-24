package com.company.inventory.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
@Order(Integer.MIN_VALUE + 100)
@RequiredArgsConstructor
public class SqlitePragmasVerifier implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=ON");

            int fkValue = -1;
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                if (rs.next()) {
                    fkValue = rs.getInt(1);
                }
            }
            String journalMode = "";
            try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) {
                    journalMode = rs.getString(1);
                }
            }

            if (fkValue != 1) {
                throw new IllegalStateException("SQLite PRAGMA foreign_keys is not ON (got " + fkValue + ")");
            }
            log.info("SQLite ready: journal_mode={}, foreign_keys=ON", journalMode);
        }
    }
}
