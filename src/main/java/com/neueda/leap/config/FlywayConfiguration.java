package com.neueda.leap.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.PostConstruct;

import javax.sql.DataSource;

/**
 * Flyway configuration for database migrations.
 * Explicitly configures and runs Flyway migrations before application startup.
 */
@Configuration
public class FlywayConfiguration {

    private final DataSource dataSource;
    
    public FlywayConfiguration(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @PostConstruct
    public void migrateDatabaseSchema() {
        System.out.println(">>> [Flyway] Starting database migrations...");
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .outOfOrder(false)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
        
        try {
            var result = flyway.migrate();
            System.out.println(">>> [Flyway] Migration completed: " + result.migrationsExecuted + " migrations executed");
        } catch (Exception e) {
            System.out.println(">>> [Flyway] Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
