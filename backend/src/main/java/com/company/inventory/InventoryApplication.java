package com.company.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InventoryApplication {

    public static void main(String[] args) {
        System.setProperty("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        SpringApplication.run(InventoryApplication.class, args);
    }
}
