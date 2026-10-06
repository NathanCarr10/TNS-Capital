package com.neueda.leap.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Abstract base class for all integration tests.
 * Provides a containerized PostgreSQL database and Kafka broker for testing
 * with the Spring Boot application context. The database container loads the
 * schema and seed data from db/ the same way the db/ image does, since
 * Hibernate only validates the schema.
 * Enables security testing with a mock user context.
 *
 * The containers are shared by every integration test class and started once.
 * Spring caches one application context across the classes, so containers
 * per class would leave later classes pointing at stopped containers.
 * Testcontainers removes the containers when the test JVM exits.
 *
 * Each test starts from empty tables: the seed data from db/data is cleared
 * before every test so tests only see the rows they create.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@WithMockUser(username = "testuser", roles = "ADMIN")
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            // The init script connects to tns_capital as $DB_USER
            .withDatabaseName("tns_capital")
            .withUsername("test_user")
            .withPassword("test_password")
            .withEnv("DB_USER", "test_user")
            // Same layout as db/Dockerfile: the init script runs tables, then views, then data
            .withCopyFileToContainer(MountableFile.forHostPath("db/docker-entrypoint-initdb.sh", 0755),
                    "/docker-entrypoint-initdb.d/00-run-sql.sh")
            .withCopyFileToContainer(MountableFile.forHostPath("db/tables"),
                    "/docker-entrypoint-initdb.d/tables")
            .withCopyFileToContainer(MountableFile.forHostPath("db/views"),
                    "/docker-entrypoint-initdb.d/views")
            .withCopyFileToContainer(MountableFile.forHostPath("db/data"),
                    "/docker-entrypoint-initdb.d/data");

    @SuppressWarnings("resource")
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static {
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // No default secret is committed; tests use @WithMockUser, so any value works
        registry.add("jwt.shared-secret", () -> "integration-test-secret-at-least-32-bytes");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // CASCADE also clears orders, positions and executions, which reference these tables
    @BeforeEach
    void clearTables() {
        jdbcTemplate.execute("TRUNCATE accounts, instruments, dlq_messages RESTART IDENTITY CASCADE");
    }
}
