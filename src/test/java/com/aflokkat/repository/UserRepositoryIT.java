package com.aflokkat.repository;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.aflokkat.entity.BookmarkEntity;
import com.aflokkat.entity.UserEntity;

/**
 * Integration tests for UserRepository and BookmarkRepository.
 * Uses Testcontainers postgres:15-alpine and mongo:7.0.
 * No live PostgreSQL or MongoDB required.
 * Run with: mvn failsafe:integration-test -Dit.test=UserRepositoryIT
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = UserRepositoryIT.Initializer.class)
@TestExecutionListeners(listeners = {
    DependencyInjectionTestExecutionListener.class,
    DirtiesContextTestExecutionListener.class
}, mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
public class UserRepositoryIT {

    // Both containers are @ClassRule so TC manages their lifecycle.
    // They must be started BEFORE the Initializer runs (Spring context creation).
    // The static block ensures they are started during class loading, which
    // precedes Spring context creation.

    @ClassRule
    public static PostgreSQLContainer<?> pgContainer =
        new PostgreSQLContainer<>("postgres:15-alpine");

    @ClassRule
    public static MongoDBContainer mongoContainer =
        new MongoDBContainer("mongo:7.0");

    static {
        // Start containers now — before Spring context initialization.
        // @ClassRule alone does not guarantee containers are started before
        // the ApplicationContextInitializer runs.
        pgContainer.start();
        mongoContainer.start();

        // Inject TC MongoDB URI as JVM system property (dotted key) so that
        // AppConfig.getProperty("mongodb.uri") tier-0 picks it up BEFORE
        // MongoClientFactory.getInstance() is called during Spring context startup.
        // AppConfig checks System.getProperty(key) first (tier-0, added in Plan 01).
        // Without this line, AppConfig falls through to application-test.properties
        // (localhost:27017) and the Spring context fails to start in CI.
        System.setProperty("mongodb.uri", mongoContainer.getConnectionString());
    }

    @AfterClass
    public static void tearDownClass() {
        System.clearProperty("mongodb.uri");
    }

    /**
     * Injects TC container URIs into the Spring Environment before the datasource
     * connection pool is created. This is the ONLY safe injection point for
     * @SpringBootTest — @BeforeClass fires too late (Spring context is already built).
     * The mongodb.uri System.setProperty in the static block is the canonical injection
     * for AppConfig tier-0; this additionally covers Spring Environment consumers.
     */
    public static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                "spring.datasource.url=" + pgContainer.getJdbcUrl(),
                "spring.datasource.username=" + pgContainer.getUsername(),
                "spring.datasource.password=" + pgContainer.getPassword(),
                // Override MongoDB URI in Spring Environment for completeness.
                // AppConfig tier-0 is already covered by System.setProperty in static block.
                "mongodb.uri=" + mongoContainer.getConnectionString()
            ).applyTo(ctx.getEnvironment());
        }
    }

    // RestaurantCacheService is wired by @SpringBootTest full context but is not
    // needed by UserRepositoryIT. Lettuce (the Redis client) connects lazily, so
    // no Redis connection attempt is made during context creation or during these
    // JPA-only tests. No @MockBean needed — and @MockBean(RestaurantCacheService)
    // causes java.lang.VerifyError on Java 25 via Mockito inline instrumentation.
    //
    // @TestExecutionListeners with REPLACE_DEFAULTS excludes MockitoTestExecutionListener
    // and ResetMocksTestExecutionListener, which cause StackOverflowError on Java 25
    // via Mockito 5 + byte-buddy instrumentation even with no @MockBean fields.

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Before
    public void cleanDatabase() {
        // Use deleteAllInBatch() — issues a single DELETE statement without loading entities first.
        // This avoids Hibernate proxy-based entity loading which causes StackOverflowError via
        // byte-buddy proxy infinite recursion on Java 25 (Hibernate 5.6.15 + byte-buddy 1.16.x).
        // Standard deleteAll() calls findAll() first, triggering proxy chains that overflow.
        bookmarkRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    public void testSaveAndFindUser() {
        UserEntity user = new UserEntity(
            "testuser", "testuser@example.com", "hashedpwd", "CUSTOMER");
        userRepository.save(user);

        Optional<UserEntity> found = userRepository.findByUsername("testuser");
        assertTrue("User should be found by username", found.isPresent());
        assertEquals("testuser@example.com", found.get().getEmail());
        assertEquals("CUSTOMER", found.get().getRole());
    }

    @Test
    public void testFindUser_NotFound() {
        Optional<UserEntity> found = userRepository.findByUsername("nonexistentuser");
        assertFalse("Should return empty Optional for unknown username", found.isPresent());
    }

    @Test
    public void testSaveAndFindBookmark() {
        // Create and persist a user first (BookmarkEntity has FK to UserEntity)
        UserEntity user = new UserEntity(
            "bookmarkuser", "bookmarkuser@example.com", "hashedpwd2", "CUSTOMER");
        userRepository.save(user);
        // Re-fetch to get the DB-assigned id
        UserEntity saved = userRepository.findByUsername("bookmarkuser").orElseThrow();

        BookmarkEntity bookmark = new BookmarkEntity(saved, "R0001");
        bookmarkRepository.save(bookmark);

        List<BookmarkEntity> bookmarks = bookmarkRepository.findByUserId(saved.getId());
        assertFalse("Bookmarks list should not be empty", bookmarks.isEmpty());
        assertEquals("R0001", bookmarks.get(0).getRestaurantId());
    }

    @Test
    public void testBookmark_CountByUserId() {
        UserEntity user = new UserEntity(
            "countuser", "countuser@example.com", "hashedpwd3", "CUSTOMER");
        userRepository.save(user);
        UserEntity saved = userRepository.findByUsername("countuser").orElseThrow();

        bookmarkRepository.save(new BookmarkEntity(saved, "R0002"));
        bookmarkRepository.save(new BookmarkEntity(saved, "R0003"));

        long count = bookmarkRepository.countByUserId(saved.getId());
        assertEquals("Should count 2 bookmarks for user", 2L, count);
    }
}
