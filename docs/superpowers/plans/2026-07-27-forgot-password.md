# Forgot Password (Resend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a password-recovery flow — a user who forgets their password can request a reset link by email (via Resend) and set a new password through it.

**Architecture:** A new `PasswordResetTokenEntity`/`PasswordResetTokenRepository` (JPA, mirroring `BookmarkEntity`'s existing `@ManyToOne` pattern) stores hashed, single-use, 1-hour-expiry tokens. Two new `AuthController` endpoints — `POST /api/auth/forgot-password` (always returns a generic success response, anti-enumeration) and `POST /api/auth/reset-password` (validates the token, applies the existing password-complexity rule, updates the password) — sit behind a new `PasswordResetService` that owns the token lifecycle and calls a new `EmailService` abstraction (interface + `ResendEmailService` impl, mirroring the existing `JwtService`/`JwtUtil` pattern) to actually send the email. `login.html` gets a "Forgot password?" link; a new `reset-password.html` page (reusing the password-complexity-checklist/confirmation UI already built for registration) completes the flow.

**Tech Stack:** Java 25, Spring Boot 4, Spring Data JPA (new entity/repository), `com.resend:resend-java:4.4.0` (new Maven dependency), `java.security.MessageDigest`/`SecureRandom` (token generation/hashing, no new dependency needed), JUnit 5 + Mockito, vanilla JS (existing template pattern).

## Global Constraints

- Reset token: 32 random bytes (`SecureRandom`) base64url-encoded (no padding) as the raw token given to the user; SHA-256 hex digest of the raw token is what's persisted (`token_hash` column) — the raw token is never stored.
- Token lifetime: 1 hour from creation. Single-use: a `used_at` timestamp (nullable, set on successful reset) makes a token permanently unusable once consumed, independent of whether it's still within its 1-hour window.
- `POST /api/auth/forgot-password` always returns `200 {"status": "success"}` regardless of whether the email exists in `users` — no behavioral or message difference between the two cases (anti-enumeration, per the design spec).
- `POST /api/auth/reset-password` applies the exact same password rule as registration: `ValidationUtil.requireValidPassword(newPassword)` (10+ chars, 1 uppercase, 1 digit) — already implemented in this codebase, do not reimplement.
- No session/refresh-token revocation on password reset — consistent with this app's existing stateless-JWT model (confirmed: no revocation mechanism exists anywhere else in the codebase either).
- Both new endpoints fall under the existing `RateLimitFilter`'s `/api/auth/**` bucket automatically (confirmed: `RateLimitFilter.java`'s `shouldNotFilter()` matches on `uri.startsWith("/api/auth/")`) — no filter code changes needed.
- Resend API key: new config key `resend.api.key`, read via a new `AppConfig.getResendApiKey()` method following the exact fail-fast pattern of the existing `AppConfig.getJwtSecret()` (throws `IllegalStateException` at first use if missing/blank — not at JVM boot, since `AppConfig` has no static validation hook, but the `ResendEmailService` constructor calls it eagerly at Spring context startup, which achieves the same "fail fast, not on first real usage" effect).
- Sender address: sandbox `onboarding@resend.dev` — hardcoded as a constant in `ResendEmailService`, not a config key (per the design spec: switching to a verified domain later is a one-line code change, not worth a config key for a value that won't change during this project's lifetime).
- Commits: English, imperative mood, conventional-commits prefix (`feat|fix|docs|chore|refactor|test|ci|style`), subject ≤72 chars, no trailing period.
- Work happens on branch `feature/forgot-password` (already created, based on `feature/jwt-httponly-cookies`, and pushed with the design spec) — never commit this work directly to `main`.

---

### Task 1: Add the Resend dependency and `AppConfig.getResendApiKey()`

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/st4r4x/config/AppConfig.java`
- Modify: `src/main/resources/application.properties`
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AppConfig.getResendApiKey()` (`String`, throws `IllegalStateException` if missing/blank) — Task 3's `ResendEmailService` constructor calls this.

- [ ] **Step 1: Add the Resend Maven dependency**

In `pom.xml`, find the `<dependencies>` section and add this dependency (placed alphabetically near other third-party SDK dependencies — check the existing file for where similar single-purpose SDK dependencies like the JWT library are declared, and follow that placement convention):

```xml
    <dependency>
      <groupId>com.resend</groupId>
      <artifactId>resend-java</artifactId>
      <version>4.4.0</version>
    </dependency>
```

- [ ] **Step 2: Add `getResendApiKey()` to `AppConfig`**

In `src/main/java/com/st4r4x/config/AppConfig.java`, add this method right after the existing `getJwtSecret()` method:

```java
    public static String getResendApiKey() {
        String key = getProperty("resend.api.key", null);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                "RESEND_API_KEY environment variable is not set. " +
                "Set it in your .env file or environment to enable password-reset emails."
            );
        }
        return key;
    }
```

- [ ] **Step 3: Add the config key to `application.properties`**

In `src/main/resources/application.properties`, add this line right after `app.cookie.secure=true` (added in the JWT-cookie-migration sub-project):

```properties
resend.api.key=
```

(Left blank in the tracked file — populated via environment variable in every real environment, same pattern as `jwt.secret`.)

- [ ] **Step 4: Add `RESEND_API_KEY` to `docker-compose.yml`**

In `docker-compose.yml`, in the `app` service's `environment:` block, add this line right after `APP_COOKIE_SECURE: "false"`:

```yaml
      RESEND_API_KEY: ${RESEND_API_KEY:-}
```

(Matches the existing `CONTROLLER_SIGNUP_CODE`/`ADMIN_SIGNUP_CODE` pattern of `${VAR:-}` — empty default, populated from the host's `.env` file when present.)

- [ ] **Step 5: Verify the project still builds**

```bash
mvn compile -q
```

Expected: no output, exit code 0 (the new dependency resolves and `AppConfig` compiles).

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/com/st4r4x/config/AppConfig.java src/main/resources/application.properties docker-compose.yml
git commit -m "feat(auth): add Resend dependency and RESEND_API_KEY config

Not yet used anywhere — AppConfig.getResendApiKey() follows the same
fail-fast-if-missing pattern as getJwtSecret(). Wired into an actual
email-sending service in the next task."
```

---

### Task 2: Create `PasswordResetTokenEntity` and `PasswordResetTokenRepository`

**Files:**
- Create: `src/main/java/com/st4r4x/entity/PasswordResetTokenEntity.java`
- Create: `src/main/java/com/st4r4x/repository/PasswordResetTokenRepository.java`
- Create: `src/test/java/com/st4r4x/repository/PasswordResetTokenRepositoryIT.java`

**Interfaces:**
- Consumes: `UserEntity` (existing).
- Produces: `PasswordResetTokenEntity` with fields `id` (`Long`), `user` (`UserEntity`), `tokenHash` (`String`), `expiresAt` (`Date`), `usedAt` (`Date`, nullable), `createdAt` (`Date`). `PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long>` with method `Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash)`. Task 4's `PasswordResetService` consumes both.

- [ ] **Step 1: Create `PasswordResetTokenEntity`**

Create `src/main/java/com/st4r4x/entity/PasswordResetTokenEntity.java`:

```java
package com.st4r4x.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_tokens",
       indexes = @Index(name = "idx_password_reset_tokens_token_hash", columnList = "token_hash"))
public class PasswordResetTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

    @Column(name = "used_at")
    private Date usedAt;

    @Column(name = "created_at", nullable = false)
    private Date createdAt = new Date();

    public PasswordResetTokenEntity() {}

    public PasswordResetTokenEntity(UserEntity user, String tokenHash, Date expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

    public Date getUsedAt() { return usedAt; }
    public void setUsedAt(Date usedAt) { this.usedAt = usedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: Create `PasswordResetTokenRepository`**

Create `src/main/java/com/st4r4x/repository/PasswordResetTokenRepository.java`:

```java
package com.st4r4x.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.st4r4x.entity.PasswordResetTokenEntity;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
}
```

- [ ] **Step 3: Write the integration test**

Create `src/test/java/com/st4r4x/repository/PasswordResetTokenRepositoryIT.java`, following the exact Testcontainers setup pattern of the existing `UserRepositoryIT.java` (read that file first if anything below is unclear — this test reuses its container-startup approach verbatim):

```java
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
        System.setProperty("mongodb.uri", mongoContainer.getConnectionString());
        // ResendEmailService's bean creation calls AppConfig.getResendApiKey() eagerly at
        // context startup (per Task 3) — without this, the full @SpringBootTest context
        // fails to start with IllegalStateException before any test method runs.
        System.setProperty("resend.api.key", "test-key-not-a-real-resend-key");
    }

    @AfterAll
    public static void tearDownContainers() {
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
```

- [ ] **Step 4: Run the integration test**

```bash
mvn test-compile failsafe:integration-test failsafe:verify -Dit.test=PasswordResetTokenRepositoryIT
```

Expected: `BUILD SUCCESS`, 3 tests pass. (This requires Docker running locally for Testcontainers — same requirement as the existing `UserRepositoryIT`.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/entity/PasswordResetTokenEntity.java src/main/java/com/st4r4x/repository/PasswordResetTokenRepository.java src/test/java/com/st4r4x/repository/PasswordResetTokenRepositoryIT.java
git commit -m "feat(auth): add PasswordResetTokenEntity and repository

New password_reset_tokens table (auto-created via ddl-auto=update,
confirmed the existing project convention). Stores a SHA-256 hash of
the reset token, never the raw value — same principle as a password
hash. Not yet wired into any service or endpoint."
```

---

### Task 3: Create `EmailService` interface and `ResendEmailService` implementation

**Files:**
- Create: `src/main/java/com/st4r4x/service/EmailService.java`
- Create: `src/main/java/com/st4r4x/service/ResendEmailService.java`
- Create: `src/test/java/com/st4r4x/service/ResendEmailServiceTest.java`

**Interfaces:**
- Consumes: `AppConfig.getResendApiKey()` from Task 1.
- Produces: `EmailService` interface with `void sendPasswordResetEmail(String toEmail, String resetLink)`. Task 5's `PasswordResetService` consumes this interface (not the concrete class) via constructor injection, so it can be mocked in tests without a real Resend API call.

- [ ] **Step 1: Create the `EmailService` interface**

Create `src/main/java/com/st4r4x/service/EmailService.java`:

```java
package com.st4r4x.service;

/**
 * Abstraction over the email-sending provider. Exists so PasswordResetService
 * can be tested without a real network call — mirrors the JwtService/JwtUtil
 * interface-plus-implementation pattern already used in this codebase.
 */
public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
```

- [ ] **Step 2: Write the failing test for `ResendEmailService`**

Create `src/test/java/com/st4r4x/service/ResendEmailServiceTest.java`:

```java
package com.st4r4x.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

@ExtendWith(MockitoExtension.class)
class ResendEmailServiceTest {

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    @Test
    void sendPasswordResetEmail_callsResendWithCorrectFields() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(mock(CreateEmailResponse.class));

        ResendEmailService service = new ResendEmailService(resend);
        service.sendPasswordResetEmail("alice@example.com", "https://example.com/reset-password?token=abc123");

        var captor = org.mockito.ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(captor.capture());
        CreateEmailOptions sent = captor.getValue();
        assertEquals("onboarding@resend.dev", sent.getFrom());
        assertEquals(java.util.List.of("alice@example.com"), sent.getTo());
        assertTrue(sent.getHtml().contains("https://example.com/reset-password?token=abc123"));
    }

    @Test
    void sendPasswordResetEmail_wrapsResendExceptionAsRuntimeException() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("API error", 500, null));

        ResendEmailService service = new ResendEmailService(resend);

        assertThrows(RuntimeException.class, () ->
            service.sendPasswordResetEmail("alice@example.com", "https://example.com/reset-password?token=abc123"));
    }
}
```

(If `CreateEmailOptions.getFrom()`/`getTo()`/`getHtml()` getter names differ from this guess once you inspect the actual `com.resend.services.emails.model.CreateEmailOptions` class on the classpath after Task 1's dependency is added — check via your IDE/javap or by reading the resolved jar's decompiled source — adjust the test's assertions to match the real getter names. The builder call chain (`.from(...).to(...).subject(...).html(...).build()`) is confirmed correct from the official README; only exact getter names on the built object are unconfirmed.)

- [ ] **Step 3: Run the test to verify it fails**

```bash
mvn test -Dtest=ResendEmailServiceTest -q
```

Expected: compile error — `ResendEmailService` does not exist yet.

- [ ] **Step 4: Implement `ResendEmailService`**

Create `src/main/java/com/st4r4x/service/ResendEmailService.java`:

```java
package com.st4r4x.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.st4r4x.config.AppConfig;

@Service
public class ResendEmailService implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String FROM_ADDRESS = "onboarding@resend.dev";

    private final Resend resend;

    public ResendEmailService() {
        this.resend = new Resend(AppConfig.getResendApiKey());
    }

    // Constructor for test injection
    ResendEmailService(Resend resend) {
        this.resend = resend;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        CreateEmailOptions params = CreateEmailOptions.builder()
            .from(FROM_ADDRESS)
            .to(toEmail)
            .subject("Reset your password — NYC Restaurant Inspector")
            .html("<p>We received a request to reset your password.</p>"
                + "<p><a href=\"" + resetLink + "\">Click here to reset your password</a></p>"
                + "<p>This link expires in 1 hour. If you didn't request this, you can ignore this email.</p>")
            .build();

        try {
            resend.emails().send(params);
        } catch (ResendException e) {
            logger.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
mvn test -Dtest=ResendEmailServiceTest -q
```

Expected: `BUILD SUCCESS`. If a getter-name mismatch surfaces (per Step 2's note), fix the test's assertions to match the real `CreateEmailOptions` API and re-run.

- [ ] **Step 6: Run the full unit-test suite (not integration tests — those need Docker)**

```bash
mvn test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/st4r4x/service/EmailService.java src/main/java/com/st4r4x/service/ResendEmailService.java src/test/java/com/st4r4x/service/ResendEmailServiceTest.java
git commit -m "feat(auth): add EmailService abstraction with Resend implementation

Interface exists so PasswordResetService (next task) can be tested
without a real network call, mirroring the existing JwtService/JwtUtil
split. Sandbox sender (onboarding@resend.dev) — real-recipient
delivery is limited to the Resend account owner's own address until
a verified custom domain is configured, a config-only change for
later, not addressed here. Not yet wired into any controller."
```

---

### Task 4: Create `PasswordResetService` (token generation, validation, password update)

**Files:**
- Create: `src/main/java/com/st4r4x/service/PasswordResetService.java`
- Create: `src/test/java/com/st4r4x/service/PasswordResetServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository` (existing), `PasswordResetTokenRepository` (Task 2), `EmailService` (Task 3), `PasswordEncoder` (existing Spring bean, already used by `AuthService`), `ValidationUtil.requireValidPassword` (existing).
- Produces: `PasswordResetService` with two public methods: `void requestReset(String email, String appBaseUrl)` (always succeeds silently, no return value, no exception for "email not found" — the anti-enumeration behavior lives here) and `void resetPassword(String token, String newPassword)` (throws `IllegalArgumentException` with a specific message for invalid/expired/used token or a weak password). Task 6 (`AuthController`) calls both methods.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/st4r4x/service/PasswordResetServiceTest.java`:

```java
package com.st4r4x.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.st4r4x.entity.PasswordResetTokenEntity;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.PasswordResetTokenRepository;
import com.st4r4x.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, emailService, passwordEncoder);
    }

    // ── requestReset ─────────────────────────────────────────────────────────

    @Test
    void requestReset_sendsEmail_whenUserExists() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.requestReset("alice@example.com", "https://example.com");

        verify(tokenRepository).save(any(PasswordResetTokenEntity.class));
        verify(emailService).sendPasswordResetEmail(eq("alice@example.com"), contains("https://example.com/reset-password?token="));
    }

    @Test
    void requestReset_doesNothing_whenUserDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        service.requestReset("unknown@example.com", "https://example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestReset_doesNotThrow_whenUserDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestReset("unknown@example.com", "https://example.com"));
    }

    @Test
    void requestReset_storesHashNotRawToken() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        ArgumentCaptor<PasswordResetTokenEntity> captor = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        service.requestReset("alice@example.com", "https://example.com");
        verify(tokenRepository).save(captor.capture());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(any(), linkCaptor.capture());
        String rawTokenFromLink = linkCaptor.getValue().substring(linkCaptor.getValue().indexOf("token=") + 6);

        assertNotEquals(rawTokenFromLink, captor.getValue().getTokenHash(),
            "The stored tokenHash must not equal the raw token sent to the user");
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPassword_updatesPassword_whenTokenValid() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "oldHash", "ROLE_CUSTOMER");
        user.setId(1L);
        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity(
            user, "irrelevant-in-this-test-since-we-mock-the-lookup", new Date(System.currentTimeMillis() + 3_600_000L));

        // The service hashes the raw token internally and looks up by that hash — since we
        // don't know the exact hash the service will compute ahead of time, mock findByTokenHash
        // to match ANY string and return our token, then verify the raw token round-trips.
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenEntity));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("newHash");

        service.resetPassword("some-raw-token", "NewPassword123");

        assertEquals("newHash", user.getPasswordHash());
        assertNotNull(tokenEntity.getUsedAt(), "Token must be marked used after a successful reset");
        verify(userRepository).save(user);
        verify(tokenRepository).save(tokenEntity);
    }

    @Test
    void resetPassword_throws_whenTokenNotFound() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resetPassword("bad-token", "NewPassword123"));
        assertTrue(ex.getMessage().contains("Invalid or expired"));
    }

    @Test
    void resetPassword_throws_whenTokenExpired() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "oldHash", "ROLE_CUSTOMER");
        PasswordResetTokenEntity expiredToken = new PasswordResetTokenEntity(
            user, "hash", new Date(System.currentTimeMillis() - 1000L)); // expired 1 second ago
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resetPassword("some-token", "NewPassword123"));
        assertTrue(ex.getMessage().contains("Invalid or expired"));
    }

    @Test
    void resetPassword_throws_whenTokenAlreadyUsed() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "oldHash", "ROLE_CUSTOMER");
        PasswordResetTokenEntity usedToken = new PasswordResetTokenEntity(
            user, "hash", new Date(System.currentTimeMillis() + 3_600_000L));
        usedToken.setUsedAt(new Date());
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usedToken));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resetPassword("some-token", "NewPassword123"));
        assertTrue(ex.getMessage().contains("already been used"));
    }

    @Test
    void resetPassword_throws_whenPasswordTooWeak() {
        UserEntity user = new UserEntity("alice", "alice@example.com", "oldHash", "ROLE_CUSTOMER");
        PasswordResetTokenEntity validToken = new PasswordResetTokenEntity(
            user, "hash", new Date(System.currentTimeMillis() + 3_600_000L));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(validToken));

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("some-token", "weak"));
        verify(userRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=PasswordResetServiceTest -q
```

Expected: compile error — `PasswordResetService` does not exist yet.

- [ ] **Step 3: Implement `PasswordResetService`**

Create `src/main/java/com/st4r4x/service/PasswordResetService.java`:

```java
package com.st4r4x.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.st4r4x.entity.PasswordResetTokenEntity;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.PasswordResetTokenRepository;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.util.ValidationUtil;

@Service
public class PasswordResetService {

    private static final long TOKEN_VALIDITY_MS = 3_600_000L; // 1 hour
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                 EmailService emailService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Always succeeds with no observable difference between "email exists" and
     * "email doesn't exist" — anti-enumeration by design. Never throws for a
     * missing email; only a genuinely unexpected failure (e.g. email delivery
     * error) propagates.
     */
    public void requestReset(String email, String appBaseUrl) {
        Optional<UserEntity> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return;
        }
        UserEntity user = userOpt.get();

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Date expiresAt = new Date(System.currentTimeMillis() + TOKEN_VALIDITY_MS);

        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity(user, tokenHash, expiresAt);
        tokenRepository.save(tokenEntity);

        String resetLink = appBaseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);
        PasswordResetTokenEntity tokenEntity = tokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));

        if (tokenEntity.getUsedAt() != null) {
            throw new IllegalArgumentException("This reset link has already been used");
        }
        if (tokenEntity.getExpiresAt().before(new Date())) {
            throw new IllegalArgumentException("Invalid or expired reset link");
        }

        ValidationUtil.requireValidPassword(newPassword);

        UserEntity user = tokenEntity.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenEntity.setUsedAt(new Date());
        tokenRepository.save(tokenEntity);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JEP 249 / every JDK since 8) — unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
mvn test -Dtest=PasswordResetServiceTest -q
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Run the full unit-test suite**

```bash
mvn test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/st4r4x/service/PasswordResetService.java src/test/java/com/st4r4x/service/PasswordResetServiceTest.java
git commit -m "feat(auth): add PasswordResetService with token lifecycle

requestReset() never reveals whether an email exists — same behavior
(no exception, no return value) for both cases, anti-enumeration by
design. resetPassword() validates token existence, used-state, and
expiry before applying the existing password-complexity rule. Not
yet wired into any controller endpoint."
```

---

### Task 5: Add `POST /api/auth/forgot-password` and `POST /api/auth/reset-password` to `AuthController`

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/AuthController.java`
- Create: `src/main/java/com/st4r4x/dto/ForgotPasswordRequest.java`
- Create: `src/main/java/com/st4r4x/dto/ResetPasswordRequest.java`
- Modify: `src/test/java/com/st4r4x/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `PasswordResetService.requestReset(String, String)`/`resetPassword(String, String)` from Task 4.
- Produces: `POST /api/auth/forgot-password` (body `{"email": "..."}`, always `200 {"status": "success"}`), `POST /api/auth/reset-password` (body `{"token": "...", "newPassword": "..."}`, `200 {"status": "success"}` or `400` with the service's error message). Task 7 (`login.html`, `reset-password.html`) calls both.

- [ ] **Step 1: Create the two request DTOs**

Create `src/main/java/com/st4r4x/dto/ForgotPasswordRequest.java`:

```java
package com.st4r4x.dto;

public class ForgotPasswordRequest {
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
```

Create `src/main/java/com/st4r4x/dto/ResetPasswordRequest.java`:

```java
package com.st4r4x.dto;

public class ResetPasswordRequest {
    private String token;
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
```

- [ ] **Step 2: Write the failing tests**

Add to `src/test/java/com/st4r4x/controller/AuthControllerTest.java`, in a new section right after the `check-email` tests (before the closing `}` of the class):

```java
    // ── forgot-password / reset-password ────────────────────────────────────

    @Test
    void forgotPassword_returns200_always() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("alice@example.com");

        ResponseEntity<?> response = authController.forgotPassword(req, new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("status", "success"), response.getBody());
        verify(passwordResetService).requestReset(eq("alice@example.com"), any());
    }

    @Test
    void forgotPassword_returns200_evenWhenServiceThrowsUnexpectedError() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("alice@example.com");
        doThrow(new RuntimeException("email provider down")).when(passwordResetService)
            .requestReset(any(), any());

        ResponseEntity<?> response = authController.forgotPassword(req, new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value(),
            "Must still return the generic success response even if the email service fails — "
            + "a caller must never learn anything from the response about what happened server-side");
    }

    @Test
    void resetPassword_returns200_onValidToken() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("valid-token");
        req.setNewPassword("NewPassword123");

        ResponseEntity<?> response = authController.resetPassword(req);

        assertEquals(200, response.getStatusCode().value());
        verify(passwordResetService).resetPassword("valid-token", "NewPassword123");
    }

    @Test
    void resetPassword_returns400_onInvalidToken() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("bad-token");
        req.setNewPassword("NewPassword123");
        doThrow(new IllegalArgumentException("Invalid or expired reset link")).when(passwordResetService)
            .resetPassword("bad-token", "NewPassword123");

        ResponseEntity<?> response = authController.resetPassword(req);

        assertEquals(400, response.getStatusCode().value());
    }
```

Add the required imports and mock field at the top of the file — add these imports:

```java
import com.st4r4x.dto.ForgotPasswordRequest;
import com.st4r4x.dto.ResetPasswordRequest;
import com.st4r4x.service.PasswordResetService;
```

And add this mock field alongside the existing `@Mock private AuthService authService;`:

```java
    @Mock
    private PasswordResetService passwordResetService;
```

(Check `AuthControllerTest`'s current `@InjectMocks`/`@Mock` setup — Mockito's `@InjectMocks` auto-wires every `@Mock` field into the class under test's constructor/setters, so adding this mock field is sufficient; no other wiring change needed there.)

- [ ] **Step 3: Run tests to verify they fail**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: compile error — `forgotPassword`/`resetPassword` methods and `PasswordResetService` field don't exist on `AuthController` yet.

- [ ] **Step 4: Add the two endpoints to `AuthController`**

Add the `PasswordResetService` field (alongside the existing `@Autowired private AuthService authService;`):

```java
    @Autowired
    private PasswordResetService passwordResetService;
```

Add the two endpoints after the existing `check-email` endpoint, before the private `errorResponse` helper:

```java
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            String appBaseUrl = httpRequest.getScheme() + "://" + httpRequest.getServerName()
                + (httpRequest.getServerPort() == 80 || httpRequest.getServerPort() == 443 ? "" : ":" + httpRequest.getServerPort());
            passwordResetService.requestReset(request.getEmail(), appBaseUrl);
        } catch (Exception e) {
            // Deliberately swallowed — the response must be identical whether the email exists,
            // the send succeeded, or anything else failed server-side. Anti-enumeration by design.
            logger.warn("forgot-password request processing failed (response is unaffected): {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(errorResponse(e));
        }
    }
```

Add the required imports and a logger field (check first whether `AuthController` already has a logger — it likely does not, since it currently has no logging; if absent, add both the import and the field):

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.st4r4x.dto.ForgotPasswordRequest;
import com.st4r4x.dto.ResetPasswordRequest;
import com.st4r4x.service.PasswordResetService;
```

```java
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
mvn test -Dtest=AuthControllerTest -q
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 6: Run the full unit-test suite**

```bash
mvn test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/st4r4x/controller/AuthController.java src/main/java/com/st4r4x/dto/ForgotPasswordRequest.java src/main/java/com/st4r4x/dto/ResetPasswordRequest.java src/test/java/com/st4r4x/controller/AuthControllerTest.java
git commit -m "feat(auth): expose POST /api/auth/forgot-password and reset-password

forgot-password always returns 200, even if PasswordResetService
throws — swallowing any exception here is deliberate, not an
oversight, since the response must carry zero information about
whether the email exists or whether sending succeeded. Both
endpoints fall under the existing /api/auth/** rate-limit bucket
automatically, no filter change needed."
```

---

### Task 6: Create `ViewController` route and `reset-password.html` template

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/ViewController.java`
- Create: `src/main/resources/templates/reset-password.html`

**Interfaces:**
- Consumes: `POST /api/auth/reset-password` from Task 5.
- Produces: `GET /reset-password` route rendering `reset-password.html`. No other task consumes this — it's the final user-facing page.

- [ ] **Step 1: Add the route to `ViewController`**

In `src/main/java/com/st4r4x/controller/ViewController.java`, add this method after the existing `login()` method:

```java
    @GetMapping("/reset-password")
    public String resetPassword() {
        return "reset-password";
    }
```

- [ ] **Step 2: Create `reset-password.html`**

Create `src/main/resources/templates/reset-password.html`, reusing `login.html`'s visual style (`.auth-card`, `.field`, checklist colors) and its password-complexity-checklist/confirmation JS (copied and adapted, not shared via a fragment — `login.html` doesn't currently expose these as a reusable fragment, and introducing one is out of scope for this task; follow the existing pattern of each auth-related page being self-contained):

```html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Reset Password — NYC Restaurant Inspector</title>
    <th:block th:replace="fragments/ux-utils :: ux-utils"></th:block>
    <style>
      body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
      .auth-wrap { width: 100%; max-width: 420px; padding: 24px 16px; }
      .auth-brand { text-align: center; margin-bottom: 28px; }
      .auth-brand-name { font-family: 'Playfair Display', serif; font-weight: 900; font-size: 1.5em; color: #1a1a1a; line-height: 1.1; }
      .auth-brand-sub { font-size: 0.7em; font-weight: 700; letter-spacing: 0.12em; color: #c0392b; text-transform: uppercase; margin-top: 4px; }
      .auth-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #c0392b; padding: 28px 28px 24px; }
      .field { margin-bottom: 14px; }
      .field label { display: block; font-size: 0.8rem; font-weight: 600; color: #555; margin-bottom: 5px; letter-spacing: 0.02em; text-transform: uppercase; }
      .field input { width: 100%; padding: 10px 12px; background: white; border: 1px solid #e8e0d8; color: #1a1a1a; font-size: 0.9rem; font-family: 'Inter', sans-serif; outline: none; border-radius: 0; transition: border-color .15s; }
      .field input:focus { border-color: #c0392b; }
      .field input::placeholder { color: #aaa; }
      .hint { font-size: 0.75rem; color: #aaa; margin-top: 3px; }
      .submit-btn { width: 100%; padding: 11px; margin-top: 6px; background: #c0392b; border: none; color: #fff; font-size: 0.88rem; font-weight: 700; font-family: 'Inter', sans-serif; letter-spacing: 0.06em; text-transform: uppercase; cursor: pointer; border-radius: 0; transition: background .15s; }
      .submit-btn:hover { background: #a93226; }
      .submit-btn:disabled { background: #e8e0d8; cursor: default; color: #aaa; }
      .error-msg { margin-top: 12px; padding: 9px 12px; background: #ffebee; border: 1px solid #ef9a9a; color: #c62828; font-size: 0.82rem; display: none; }
    </style>
  </head>
  <body>
    <div class="auth-wrap">
      <div class="auth-brand">
        <div class="auth-brand-name">NYC Restaurant<br>Inspector</div>
        <div class="auth-brand-sub">Health Department Data</div>
      </div>
      <div class="auth-card">
        <div class="field">
          <label for="newPassword">New Password</label>
          <input id="newPassword" type="password" placeholder="••••••••" autocomplete="new-password" />
          <ul id="pwChecklist" style="list-style:none;padding:0;margin:6px 0 0;font-size:0.75rem;">
            <li id="pwCheckLength" style="color:#aaa;">— At least 10 characters</li>
            <li id="pwCheckUpper" style="color:#aaa;">— At least 1 uppercase letter</li>
            <li id="pwCheckDigit" style="color:#aaa;">— At least 1 digit</li>
          </ul>
        </div>
        <div class="field">
          <label for="confirmPassword">Confirm New Password</label>
          <input id="confirmPassword" type="password" placeholder="••••••••" autocomplete="new-password" />
          <div id="passwordMismatch" class="hint" style="color:#c62828;display:none;">Passwords do not match</div>
        </div>
        <button class="submit-btn" id="resetBtn">Reset Password</button>
        <div class="error-msg" id="resetError"></div>
      </div>
    </div>

    <script>
      const token = new URLSearchParams(window.location.search).get("token");
      if (!token) {
        document.getElementById("resetError").style.display = "block";
        document.getElementById("resetError").textContent = "No reset token found. Please use the link from your email.";
        document.getElementById("resetBtn").disabled = true;
      }

      document.getElementById("newPassword").addEventListener("input", (e) => {
        const value = e.target.value;
        const checks = [
          { id: "pwCheckLength", pass: value.length >= 10 },
          { id: "pwCheckUpper", pass: /[A-Z]/.test(value) },
          { id: "pwCheckDigit", pass: /[0-9]/.test(value) },
        ];
        checks.forEach(({ id, pass }) => {
          const el = document.getElementById(id);
          el.style.color = pass ? "#2e7d32" : "#c62828";
          el.textContent = (pass ? "✓ " : "✗ ") + el.textContent.replace(/^[✓✗—] /, "");
        });
      });

      function checkPasswordsMatch() {
        const password = document.getElementById("newPassword").value;
        const confirm = document.getElementById("confirmPassword").value;
        const mismatchEl = document.getElementById("passwordMismatch");
        if (confirm.length === 0) {
          mismatchEl.style.display = "none";
          return;
        }
        mismatchEl.style.display = password !== confirm ? "block" : "none";
      }

      document.getElementById("newPassword").addEventListener("input", checkPasswordsMatch);
      document.getElementById("confirmPassword").addEventListener("input", checkPasswordsMatch);

      document.getElementById("resetBtn").addEventListener("click", () => {
        const newPassword = document.getElementById("newPassword").value;
        const confirmPassword = document.getElementById("confirmPassword").value;
        const errorEl = document.getElementById("resetError");
        errorEl.style.display = "none";

        if (newPassword !== confirmPassword) {
          document.getElementById("passwordMismatch").style.display = "block";
          return;
        }

        if (!newPassword) {
          errorEl.style.display = "block";
          errorEl.textContent = "Please enter a new password.";
          return;
        }

        const btn = document.getElementById("resetBtn");
        btn.disabled = true;
        btn.textContent = "Resetting…";

        fetch("/api/auth/reset-password", {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ token, newPassword }),
        })
          .then((res) => res.json().then((data) => ({ status: res.status, body: data })))
          .then((result) => {
            if (result.status === 200) {
              window.location.href = "/login?reset=success";
            } else {
              errorEl.style.display = "block";
              errorEl.textContent = result.body.message || "Could not reset password.";
              btn.disabled = false;
              btn.textContent = "Reset Password";
            }
          })
          .catch(() => {
            errorEl.style.display = "block";
            errorEl.textContent = "Network error, please try again.";
            btn.disabled = false;
            btn.textContent = "Reset Password";
          });
      });
    </script>
  </body>
</html>
```

- [ ] **Step 3: Verify manually (no JS test suite for this project's frontend)**

Start the app (`docker compose up -d --build`), then visit `http://localhost:8080/reset-password` with no `?token=` query param — expected: the reset button is disabled and an error message about a missing token is shown. Visit `http://localhost:8080/reset-password?token=sometoken` — expected: the form renders normally, typing in the password field updates the checklist live, and mismatched confirm shows the red warning.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/st4r4x/controller/ViewController.java src/main/resources/templates/reset-password.html
git commit -m "feat(auth): add /reset-password page

Reuses login.html's password-complexity-checklist and confirmation
JS pattern (copied, not shared via a fragment — login.html doesn't
expose one, and introducing one is out of scope here). Missing token
query param disables the form with an explanatory message rather
than allowing a submit that would just 400."
```

---

### Task 7: Add "Forgot password?" link and success banner to `login.html`

**Files:**
- Modify: `src/main/resources/templates/login.html`

**Interfaces:**
- Consumes: `POST /api/auth/forgot-password` from Task 5.
- Produces: nothing consumed by other tasks — final UI piece.

- [ ] **Step 1: Add the "Forgot password?" link and inline form**

In `src/main/resources/templates/login.html`, find the login section's password field and submit button (currently):

```html
          <div class="field">
            <label for="loginPassword">Password</label>
            <input id="loginPassword" type="password" placeholder="••••••••" autocomplete="current-password" />
          </div>
          <button class="submit-btn" id="loginBtn">Sign In</button>
          <div class="error-msg" id="loginError"></div>
        </div>
```

Replace it with:

```html
          <div class="field">
            <label for="loginPassword">Password</label>
            <input id="loginPassword" type="password" placeholder="••••••••" autocomplete="current-password" />
            <div class="hint"><a href="#" id="forgotPasswordLink" style="color:#c0392b;">Forgot password?</a></div>
          </div>
          <button class="submit-btn" id="loginBtn">Sign In</button>
          <div class="error-msg" id="loginError"></div>
          <div class="success-msg" id="loginSuccess"></div>

          <div id="forgotPasswordSection" style="display:none;margin-top:16px;padding-top:16px;border-top:1px solid #e8e0d8;">
            <div class="field">
              <label for="forgotEmail">Email</label>
              <input id="forgotEmail" type="email" placeholder="you@example.com" autocomplete="email" />
            </div>
            <button class="submit-btn" id="forgotPasswordBtn" type="button">Send Reset Link</button>
            <div class="success-msg" id="forgotPasswordSuccess"></div>
          </div>
        </div>
```

- [ ] **Step 2: Add the JS for the forgot-password link and form**

In the `<script>` block, right before the line `document.getElementById("regPassword").addEventListener("input", (e) => {` (the password-complexity-checklist listener), insert:

```javascript
      document.getElementById("forgotPasswordLink").addEventListener("click", (e) => {
        e.preventDefault();
        const section = document.getElementById("forgotPasswordSection");
        section.style.display = section.style.display === "none" ? "block" : "none";
      });

      document.getElementById("forgotPasswordBtn").addEventListener("click", () => {
        const email = document.getElementById("forgotEmail").value.trim();
        const successEl = document.getElementById("forgotPasswordSuccess");
        successEl.style.display = "none";

        if (!email) return;

        const btn = document.getElementById("forgotPasswordBtn");
        btn.disabled = true;
        btn.textContent = "Sending…";

        fetch("/api/auth/forgot-password", {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email }),
        })
          .then(() => {
            // Always show the same message, whether or not the email exists —
            // anti-enumeration, mirrored from the server's always-200 behavior.
            successEl.style.display = "block";
            successEl.textContent = "If an account exists with that email, a reset link has been sent.";
            btn.disabled = false;
            btn.textContent = "Send Reset Link";
          })
          .catch(() => {
            successEl.style.display = "block";
            successEl.textContent = "If an account exists with that email, a reset link has been sent.";
            btn.disabled = false;
            btn.textContent = "Send Reset Link";
          });
      });
```

(Note the `.catch()` shows the SAME success message as the `.then()` — a network-level failure between the browser and this app's own server is treated identically to a normal response, for the same anti-enumeration reasoning: there's no legitimate reason to tell a caller that something server-side went differently in one case vs. another.)

- [ ] **Step 3: Show a success banner after a successful reset**

In the `<script>` block, find the "Redirect if already logged in" IIFE at the top:

```javascript
      // Redirect if already logged in
      (function() {
        fetch("/api/auth/me", { credentials: "same-origin" })
          .then((res) => (res.ok ? res.json() : null))
          .then((data) => {
            if (!data) return;
            window.location.href = data.role === "ROLE_CONTROLLER" ? "/dashboard" : data.role === "ROLE_ADMIN" ? "/admin" : "/";
          })
          .catch(() => {});
      })();
```

Add this right after it (still before `function switchTab(tab) {`):

```javascript
      // Show a success banner if redirected here after a successful password reset
      if (new URLSearchParams(window.location.search).get("reset") === "success") {
        const successEl = document.getElementById("loginSuccess");
        successEl.style.display = "block";
        successEl.textContent = "Password reset — please sign in with your new password.";
      }
```

- [ ] **Step 4: Verify manually**

With the app running, go to `/login`, click "Forgot password?" — expected: the inline form toggles open/closed. Submit with any email (existing or not) — expected: same generic success message either way, verifiable by checking the Network tab shows one `POST /api/auth/forgot-password` call with a `200` response both times. Navigate to `/login?reset=success` directly — expected: the green success banner appears above the form.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/login.html
git commit -m "feat(auth): add forgot-password link and reset-success banner to login.html

The link toggles an inline form (consistent with this page's existing
tab-based single-page pattern rather than a separate page). Both the
success and network-error paths show the identical generic message —
mirrors the server's always-200 anti-enumeration behavior at the UI
layer too."
```

---

### Task 8: Push branch and open PR

**Files:** none (git/GitHub operations only).

**Interfaces:**
- Consumes: all commits from Tasks 1-7 on `feature/forgot-password`.
- Produces: an open PR. Since this branch is based on `feature/jwt-httponly-cookies` (PR #17, open at the time this plan was written), target that branch, matching the pattern already used for `feature/signup-availability-check` (PR #15/#16) when it depended on an unmerged predecessor.

- [ ] **Step 1: Check whether `feature/jwt-httponly-cookies` (PR #17) has merged to `main` in the meantime**

```bash
gh pr view 17 --repo St4r4x/restaurant-analytics --json state,mergedAt
```

- [ ] **Step 2a: If PR #17 has merged** — rebase this branch onto `main` before opening the PR, so the diff shown is scoped to just this sub-project:

```bash
git fetch origin main
git rebase --onto origin/main $(git merge-base origin/feature/jwt-httponly-cookies HEAD) feature/forgot-password
git push --force-with-lease origin feature/forgot-password
```

Then open the PR against `main`:

```bash
gh pr create --repo St4r4x/restaurant-analytics --base main --title "feat(auth): add forgot-password flow via Resend" --body "$(cat <<'EOF'
## Summary
- New password_reset_tokens table — hashed, single-use, 1-hour-expiry reset tokens.
- POST /api/auth/forgot-password (always 200, anti-enumeration) and POST /api/auth/reset-password (validates token, applies existing password-complexity rule).
- New EmailService abstraction + ResendEmailService (sandbox sender onboarding@resend.dev — real delivery limited to the Resend account owner's address until a verified domain is configured, a config-only follow-up).
- New /reset-password page reusing the password-checklist/confirmation UI already built for registration.
- "Forgot password?" link + reset-success banner added to login.html.
- Design spec: docs/superpowers/specs/2026-07-27-forgot-password-design.md
- Implementation plan: docs/superpowers/plans/2026-07-27-forgot-password.md

## Setup required before merge
- [ ] Create a Resend account and API key, store as RESEND_API_KEY in Infisical (both dev and prod environments — dev is what CI actually reads per Infisical/secrets-action in ci.yml) and as a local .env value for dev-container testing.

## Test plan
- [x] PasswordResetTokenRepositoryIT covers save/find-by-hash/mark-as-used against a real Postgres Testcontainer
- [x] ResendEmailServiceTest covers the Resend API call shape (mocked, no real network call) and exception wrapping
- [x] PasswordResetServiceTest covers: email-exists sends email, email-doesn't-exist is silent and doesn't throw, raw token never equals stored hash, valid/expired/used/not-found token paths, weak-password rejection
- [x] AuthControllerTest covers both new endpoints including the always-200 behavior even when the service throws
- [x] Manual verification: forgot-password link toggle, generic success message for both real and fake emails, reset-password page's missing-token guard, full checklist/confirmation UI reuse
EOF
)"
```

- [ ] **Step 2b: If PR #17 has NOT merged** — open the PR against `feature/jwt-httponly-cookies` instead:

```bash
git push origin feature/forgot-password
gh pr create --repo St4r4x/restaurant-analytics --base feature/jwt-httponly-cookies --title "feat(auth): add forgot-password flow via Resend" --body "$(cat <<'EOF'
## Summary
- New password_reset_tokens table — hashed, single-use, 1-hour-expiry reset tokens.
- POST /api/auth/forgot-password (always 200, anti-enumeration) and POST /api/auth/reset-password (validates token, applies existing password-complexity rule).
- New EmailService abstraction + ResendEmailService (sandbox sender onboarding@resend.dev — real delivery limited to the Resend account owner's address until a verified domain is configured, a config-only follow-up).
- New /reset-password page reusing the password-checklist/confirmation UI already built for registration.
- "Forgot password?" link + reset-success banner added to login.html.
- Base branch is feature/jwt-httponly-cookies (PR #17, not yet merged) — this PR should be merged/rebased onto main once #17 lands, same pattern used for the signup-availability-check sub-project earlier in this series.
- Design spec: docs/superpowers/specs/2026-07-27-forgot-password-design.md
- Implementation plan: docs/superpowers/plans/2026-07-27-forgot-password.md

## Setup required before merge
- [ ] Create a Resend account and API key, store as RESEND_API_KEY in Infisical (both dev and prod environments — dev is what CI actually reads per Infisical/secrets-action in ci.yml) and as a local .env value for dev-container testing.

## Test plan
- [x] PasswordResetTokenRepositoryIT covers save/find-by-hash/mark-as-used against a real Postgres Testcontainer
- [x] ResendEmailServiceTest covers the Resend API call shape (mocked, no real network call) and exception wrapping
- [x] PasswordResetServiceTest covers: email-exists sends email, email-doesn't-exist is silent and doesn't throw, raw token never equals stored hash, valid/expired/used/not-found token paths, weak-password rejection
- [x] AuthControllerTest covers both new endpoints including the always-200 behavior even when the service throws
- [x] Manual verification: forgot-password link toggle, generic success message for both real and fake emails, reset-password page's missing-token guard, full checklist/confirmation UI reuse
EOF
)"
```

- [ ] **Step 3: Wait for CI, verify green**

```bash
gh pr checks --repo St4r4x/restaurant-analytics --watch --interval 20
```

Expected: Build, Unit Tests, Integration Tests, Secret Scan, E2E Smoke Test, Docker Build and Push all pass. Note: `ci.yml`'s `integration-test` and `e2e` jobs already fetch all secrets from Infisical's `dev` environment (`Infisical/secrets-action@v1.0.16`, `env-slug: dev`) — confirmed by reading `.github/workflows/ci.yml`. This means `RESEND_API_KEY` will be automatically available in CI once it's added to Infisical's `dev` environment (not just `prod`) — add it to BOTH environments when performing this task's "Setup required before merge" step, not just `prod`. If `ResendEmailService`'s constructor throws `IllegalStateException` during CI (visible as an `integration-test` or `e2e` job failure with that exception in the log), the most likely cause is the key being present in Infisical `prod` but not `dev` — fix by adding it to `dev` too, don't add a fallback default key to `application.properties` to work around it (that would mask a real missing-secret condition in production).

Do not merge — leave the PR open for the user to review and merge, consistent with this repo's established workflow for this feature series.

---

## Self-Review Notes

- **Spec coverage**: token generation/storage/hashing (Task 2), email sending abstraction (Task 3), token lifecycle + anti-enumeration (Task 4), the two endpoints including the always-200 behavior (Task 5), the reset page (Task 6), the login-page entry point and success banner (Task 7). All "Out of scope" items from the spec (no change-notification email, no session revocation, no per-email rate limit beyond the shared bucket, no verified domain) correctly have no corresponding task.
- **No placeholders**: every step shows exact code, exact file paths, exact test assertions. The one explicitly-flagged uncertainty (Task 3's `CreateEmailOptions` getter names) is not a placeholder — it's a concrete instruction to verify against the actual resolved dependency and adjust, with the verified parts (builder chain, exception type, thread-safety) already confirmed against the real SDK source before this plan was written.
- **Type/name consistency**: `EmailService.sendPasswordResetEmail(String, String)` signature matches between Task 3's interface and Task 4's `PasswordResetService` call site. `PasswordResetService.requestReset(String, String)`/`resetPassword(String, String)` signatures match between Task 4's implementation and Task 5's controller call sites. Error messages (`"Invalid or expired reset link"`, `"This reset link has already been used"`) match between Task 4's implementation and Task 4's own test assertions (`contains(...)`, not exact-match, deliberately — allows minor message wording flexibility without breaking tests on a cosmetic change). `password_reset_tokens` table name and column names are consistent between Task 2's entity and the design spec's schema table.
- **Dependency-chain risk flagged explicitly**: Task 8's Step 3 calls out the untested CI risk around `RESEND_API_KEY` being empty in the CI environment — named as a real open question to verify during execution rather than silently assumed away, since no task in this plan actually runs the full test suite against a CI-like environment with the key unset.
