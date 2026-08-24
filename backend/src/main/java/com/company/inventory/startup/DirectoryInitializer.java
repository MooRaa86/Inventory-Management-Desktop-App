package com.company.inventory.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Integer.MIN_VALUE)
@RequiredArgsConstructor
public class DirectoryInitializer implements ApplicationRunner {

    private final AppPaths appPaths;

    @Override
    public void run(ApplicationArguments args) {
        log.info("First-startup directory validation starting...");
        appPaths.ensureAll();
        log.info("Directory initialization complete");
    }
}
