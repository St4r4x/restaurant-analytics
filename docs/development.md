# Development Guide

## Prerequisites

- Java 25 (JDK)
- Maven 3.x
- Docker + Docker Compose (for integration tests and full stack)

---

## Quick Start (local)

```bash
# 1. Start backing services only
docker compose up -d mongodb postgres redis

# 2. Run the application
mvn spring-boot:run

# App: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

For a lightweight sync during local development, set `nyc.api.max_records=5000` in `application.properties` before the first run — the full dataset (200 k+ rows) takes several minutes.

---

## Seeded Test Accounts

`DataSeeder` creates three accounts on every startup:

| Username | Password | Role |
|----------|----------|------|
| `customer_test` | `Test1234!` | `ROLE_CUSTOMER` |
| `controller_test` | `Test1234!` | `ROLE_CONTROLLER` |
| `admin_test` | `Test1234!` | `ROLE_ADMIN` |

> **Note:** `DataSeeder` runs unconditionally in all profiles. Gate it behind `@Profile("dev")` before using this in a shared environment.

---

## Running Tests

```bash
# Full unit test suite
mvn test

# Specific test class(es)
mvn test -Dtest=ReportControllerTest,SecurityConfigTest -q

# DAO integration test (Testcontainers — no live MongoDB required)
mvn failsafe:integration-test -Dit.test=RestaurantDAOIT
```

### Test Layout

| Package | Type | Description |
|---------|------|-------------|
| `com.st4r4x.controller` | Unit (Mockito) | Controller slice tests — mocked DAO/service |
| `com.st4r4x.sync` | Unit (Mockito) | `SyncService` mapping and orchestration |
| `com.st4r4x.dao` | Integration (Testcontainers) | `RestaurantDAO` against a real `mongo:7.0` container |

The Testcontainers IT starts a `mongo:7.0` container, seeds 60 documents, runs all DAO tests, then shuts down the container. No local MongoDB installation is needed.

---

## Build

```bash
# Compile + package (skip tests)
mvn clean package -DskipTests

# Run the fat JAR
java -jar target/quickstart-app-1.0-SNAPSHOT.jar
```

---

## Manual Data Sync

```bash
# Trigger a sync via the API (requires ADMIN JWT)
curl -X POST http://localhost:8080/api/restaurants/refresh \
     -H "Authorization: Bearer <accessToken>"
```

Alternatively, use the **Sync Now** button in the `/admin` page.

---

## Package Structure

See [architecture.md](architecture.md) for the full package tree and DAO layer design.
