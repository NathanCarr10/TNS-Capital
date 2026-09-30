package com.neueda.leap.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Abstract base class for all integration tests.
 * Provides containerized PostgreSQL database for testing with Spring Boot
 * application context. The container loads the schema and seed data from db/
 * the same way the db/ image does, since Hibernate only validates the schema.
 * Enables security testing with a mock user context.
 *
 * The container is shared by every integration test class and started once.
 * Spring caches one application context across the classes, so a container
 * per class would leave later classes pointing at a stopped database.
 * Testcontainers removes the container when the test JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@WithMockUser(username = "testuser", roles = "USER")
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("tns_capital_test")
            .withUsername("test_user")
            .withPassword("test_password")
            // Same layout as db/Dockerfile: the init script runs sql/tables then sql/data
            .withCopyFileToContainer(MountableFile.forHostPath("db/docker-entrypoint-init.sh", 0755),
                    "/docker-entrypoint-initdb.d/00-run-sql.sh")
            .withCopyFileToContainer(MountableFile.forHostPath("db/tables"),
                    "/docker-entrypoint-initdb.d/sql/tables")
            .withCopyFileToContainer(MountableFile.forHostPath("db/data"),
                    "/docker-entrypoint-initdb.d/sql/data");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // No default secret is committed; tests use @WithMockUser, so any value works
        registry.add("jwt.shared-secret", () -> "integration-test-secret-at-least-32-bytes");
    }
}
