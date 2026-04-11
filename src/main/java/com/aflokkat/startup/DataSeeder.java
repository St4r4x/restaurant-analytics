package com.aflokkat.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.aflokkat.entity.UserEntity;
import com.aflokkat.repository.UserRepository;

/**
 * Seeds one CUSTOMER and one CONTROLLER test account on every application startup.
 * Skips creation if the account already exists (idempotent — safe to run on restarts).
 *
 * Seed credentials (for development and testing only):
 *   customer_test  / Test1234!  -> ROLE_CUSTOMER
 *   controller_test / Test1234! -> ROLE_CONTROLLER
 *
 * Note: These credentials are hardcoded for academic scope. In production,
 * use environment variables (e.g., SEED_CUSTOMER_PASSWORD).
 *
 * Docker note: existing rows with role=ROLE_USER (from before Phase 1) will remain
 * but will be unable to access protected endpoints. Run `docker compose down -v`
 * followed by `docker compose up -d` to reset the PostgreSQL volume during development.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // SECURITY: in production, disable DataSeeder or inject via SEED_PASSWORD env var
    private static final String SEED_PASSWORD =
            System.getenv("SEED_PASSWORD") != null ? System.getenv("SEED_PASSWORD") : "Test1234!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedUser("customer_test", "customer@test.com", "ROLE_CUSTOMER");
        seedUser("controller_test", "controller@test.com", "ROLE_CONTROLLER");
        seedUser("admin_test", "admin@test.com", "ROLE_ADMIN");
    }

    private void seedUser(String username, String email, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            log.debug("Seed account already exists, skipping: {}", username);
            return;
        }
        String hash = passwordEncoder.encode(SEED_PASSWORD);
        userRepository.save(new UserEntity(username, email, hash, role));
        log.info("Seeded test account: {} ({})", username, role);
    }
}
