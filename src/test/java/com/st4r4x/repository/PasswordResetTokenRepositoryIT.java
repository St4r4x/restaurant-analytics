package com.st4r4x.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.st4r4x.config.MongoClientFactory;
import com.st4r4x.entity.PasswordResetTokenEntity;
import com.st4r4x.entity.UserEntity;

/**
 * Integration test for PasswordResetTokenRepository.
 * Uses Testcontainers postgres:15-alpine and mongo:7.0.
 * No live PostgreSQL or MongoDB required.
 * Run with: mvn failsafe:integration-test -Dit.test=PasswordResetTokenRepositoryIT
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = PasswordResetTokenRepositoryIT.Initializer.class)
@TestExecutionListeners(listeners = {
    DependencyInjectionTestExecutionListener.class,
    DirtiesContextTestExecutionListener.class
}, mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
public class PasswordResetTokenRepositoryIT {

    public static PostgreSQLContainer<?> pgContainer =
        new PostgreSQLContainer<>("postgres:15-alpine");

    public static MongoDBContainer mongoContainer =
        new MongoDBContainer("mongo:7.0");

    static {
        pgContainer.start();
        mongoContainer.start();
        // MongoClientFactory is a JVM-wide static singleton (see RestaurantDAOIT/
        // AnalyticsDAOIT) — a client left over from an earlier IT class in this same
        // fork would otherwise be reused here, still pointing at that class's
        // (possibly already-stopped) container instead of ours.
        MongoClientFactory.closeInstance();
        System.setProperty("mongodb.uri", mongoContainer.getConnectionString());
        // ResendEmailService's bean creation calls AppConfig.getResendApiKey() eagerly at
        // context startup (per Task 3) — without this, the full @SpringBootTest context
        // fails to start with IllegalStateException before any test method runs.
        System.setProperty("resend.api.key", "test-key-not-a-real-resend-key");
    }

    @AfterAll
    public static void tearDownContainers() {
        // Reset the singleton so it doesn't outlive this class's container and get
        // reused (pointing at a now-dead connection) by whichever IT class runs next.
        MongoClientFactory.closeInstance();
        System.clearProperty("mongodb.uri");
        System.clearProperty("resend.api.key");
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
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @BeforeEach
    public void cleanDatabase() {
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    public void testSaveAndFindByTokenHash() {
        UserEntity user = new UserEntity(
            "resetuser", "resetuser@example.com", "hashedpwd", "ROLE_CUSTOMER");
        userRepository.save(user);
        UserEntity saved = userRepository.findByUsername("resetuser").orElseThrow();

        Date expiresAt = new Date(System.currentTimeMillis() + 3_600_000L);
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(saved, "abc123hash", expiresAt);
        passwordResetTokenRepository.save(token);

        Optional<PasswordResetTokenEntity> found = passwordResetTokenRepository.findByTokenHash("abc123hash");
        assertTrue(found.isPresent(), "Token should be found by hash");
        assertEquals(saved.getId(), found.get().getUser().getId());
        assertNull(found.get().getUsedAt(), "usedAt should be null for a fresh token");
    }

    @Test
    public void testFindByTokenHash_NotFound() {
        Optional<PasswordResetTokenEntity> found = passwordResetTokenRepository.findByTokenHash("nonexistent");
        assertFalse(found.isPresent());
    }

    @Test
    public void testMarkTokenAsUsed() {
        UserEntity user = new UserEntity(
            "resetuser2", "resetuser2@example.com", "hashedpwd", "ROLE_CUSTOMER");
        userRepository.save(user);
        UserEntity saved = userRepository.findByUsername("resetuser2").orElseThrow();

        Date expiresAt = new Date(System.currentTimeMillis() + 3_600_000L);
        PasswordResetTokenEntity token = new PasswordResetTokenEntity(saved, "def456hash", expiresAt);
        passwordResetTokenRepository.save(token);

        PasswordResetTokenEntity persisted = passwordResetTokenRepository.findByTokenHash("def456hash").orElseThrow();
        persisted.setUsedAt(new Date());
        passwordResetTokenRepository.save(persisted);

        PasswordResetTokenEntity reloaded = passwordResetTokenRepository.findByTokenHash("def456hash").orElseThrow();
        assertNotNull(reloaded.getUsedAt(), "usedAt should be set after marking as used");
    }
}
