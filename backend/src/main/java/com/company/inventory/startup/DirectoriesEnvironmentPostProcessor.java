package com.company.inventory.startup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DirectoriesEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String[][] REQUIRED_DIRS = {
            {"app.root", "."},
            {"app.dir.data", "data"},
            {"app.dir.files", "data/files"},
            {"app.dir.backups", "backups"},
            {"app.dir.backups-automatic", "backups/automatic"},
            {"app.dir.backups-manual", "backups/manual"},
            {"app.dir.backups-safety", "backups/safety"},
            {"app.dir.temp", "data/tmp"},
            {"app.dir.config", "config"},
            {"app.logs-dir", "logs"},
            {"app.dir.exports", "exports"},
            {"app.dir.exports-reports", "exports/reports"},
            {"app.dir.exports-backups", "exports/backups"},
            {"app.dir.documents", "documents"}
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String root = env.getProperty("app.root");
        if (root == null || root.isBlank()) {
            String envRoot = System.getenv("APP_ROOT");
            root = (envRoot != null && !envRoot.isBlank()) ? envRoot : ".";
        }
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        for (String[] entry : REQUIRED_DIRS) {
            String rel = env.getProperty(entry[0], entry[1]);
            Path dir = rootPath.resolve(rel).normalize();
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create required directory: " + dir, e);
            }
        }
        System.setProperty("APP_ROOT_RESOLVED", rootPath.toString());
    }
}
