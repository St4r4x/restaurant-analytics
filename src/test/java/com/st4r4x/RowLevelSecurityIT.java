package com.st4r4x;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.st4r4x.config.MongoClientFactory;

/**
 * Boots against a brand-new Postgres container (no prior state) to prove that
 * schema.sql re-enables Row-Level Security on every table Hibernate's ddl-auto
 * creates — the exact "regeneration" scenario (restore drill, fresh env, dropped
 * table) that silently strips RLS if it's only ever turned on by hand.
 * Run with: mvn failsafe:integration-test -Dit.test=RowLevelSecurityIT
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = RowLevelSecurityIT.Initializer.class)
@TestExecutionListeners(listeners = {
    DependencyInjectionTestExecutionListener.class,
    DirtiesContextTestExecutionListener.class
}, mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
public class RowLevelSecurityIT {

    private static final String[] TABLES = {
        "users", "bookmarks", "password_reset_tokens", "inspection_reports", "audit_log"
    };

    public static PostgreSQLContainer<?> pgContainer =
        new PostgreSQLContainer<>("postgres:15-alpine");

    public static MongoDBContainer mongoContainer =
        new MongoDBContainer("mongo:7.0");

    static {
        pgContainer.start();
        mongoContainer.start();
        MongoClientFactory.closeInstance();
        System.setProperty("mongodb.uri", mongoContainer.getConnectionString());
        System.setProperty("resend.api.key", "test-key-not-a-real-resend-key");
        System.setProperty("jwt.secret", "a_very_long_32_bytes_minimum_secret_with_extra_chars_123456");
    }

    @AfterAll
    public static void tearDownContainers() {
        MongoClientFactory.closeInstance();
        System.clearProperty("mongodb.uri");
        System.clearProperty("resend.api.key");
        System.clearProperty("jwt.secret");
        if (pgContainer != null && pgContainer.isRunning()) pgContainer.stop();
        if (mongoContainer != null && mongoContainer.isRunning()) mongoContainer.stop();
    }

    public static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                "spring.datasource.url=" + pgContainer.getJdbcUrl(),
                "spring.datasource.username=" + pgContainer.getUsername(),
                "spring.datasource.password=" + pgContainer.getPassword(),
                "mongodb.uri=" + mongoContainer.getConnectionString()
            ).applyTo(ctx.getEnvironment());
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void allTablesHaveRowLevelSecurityEnabled() {
        for (String table : TABLES) {
            Boolean rlsEnabled = jdbcTemplate.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE oid = to_regclass(?)",
                Boolean.class, "public." + table);
            assertTrue(Boolean.TRUE.equals(rlsEnabled), table + " should have RLS enabled after startup");
        }
    }
}
