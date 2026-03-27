# Technology Stack

**Analysis Date:** 2026-03-27

## Languages

**Primary:**
- Java 11 - All backend logic, REST API, configuration, data access layers

**Build Language:**
- YAML - Docker Compose configuration (`docker-compose.yml`)

## Runtime

**Environment:**
- Java 21 JRE (production container `eclipse-temurin:21-jre-jammy`)
- Java 21 with Maven 3.8 (build container `maven:3.8-eclipse-temurin-21`)

**Package Manager:**
- Maven 3.8
- Lockfile: `pom.xml` (XML manifest with pinned dependency versions)

## Frameworks

**Core:**
- Spring Boot 2.6.15 - REST API framework, dependency injection, auto-configuration
- Spring Web Starter - HTTP server, servlet container, request handling
- Spring Data JPA - PostgreSQL ORM for users and bookmarks entities
- Spring Security - Authentication, authorization, password encoding
- Spring Data Redis - Redis connection management and template operations

**Template Engine:**
- Thymeleaf - Server-side rendering for web dashboard views

**Testing:**
- JUnit 4 (version 4.13.2) - Test runner and assertions
- Mockito (Spring Boot 2.6 managed version ~4.x) - Object mocking for unit tests
- Mockito JUnit Jupiter - Integration with JUnit 5 for Spring Boot Test suite

**Build/Dev:**
- Spring Boot Maven Plugin - Packaging, running, and managing Spring Boot apps
- Maven Compiler Plugin 3.13.0 - Java 11 source/target compilation

**API Documentation:**
- springdoc-openapi-ui 1.8.0 - Generates OpenAPI 3.0 spec and Swagger UI at `/swagger-ui.html`

## Key Dependencies

**Critical:**
- `mongodb-driver-sync` (Spring Boot 2.6 managed) - Raw MongoDB driver for synchronous queries, aggregation pipelines (no Spring Data MongoDB ORM)
- `postgresql` (Spring Boot 2.6 managed) - PostgreSQL JDBC driver for relational data
- `spring-boot-starter-data-redis` - Redis client via Lettuce connection factory

**Security:**
- `jjwt-api` 0.11.5 - JWT token generation and validation (API only)
- `jjwt-impl` 0.11.5 - JJWT implementation (runtime scope)
- `jjwt-jackson` 0.11.5 - JJWT Jackson serialization (runtime scope)

**Configuration:**
- `dotenv-java` 3.0.0 - Loads `.env` files for environment variable configuration (fallback after system env vars)

**Logging:**
- `spring-boot-starter-logging` - SLF4J + Logback logging framework (included by Spring Boot)

## Configuration

**Environment:**
- Configured via priority chain:
  1. System environment variables (set by Docker Compose or CI/CD)
  2. `.env` file (local development, optional)
  3. `src/main/resources/application.properties` (default/fallback values)
- Config class: `com.aflokkat.config.AppConfig` - centralized property accessors

**Key Configuration Files:**
- `src/main/resources/application.properties` - Default settings for MongoDB, PostgreSQL, Redis, JWT, NYC API, logging levels
- `docker-compose.yml` - Service orchestration with environment variable overrides
- `Dockerfile` - Multi-stage build: Maven builder stage → Java 21 JRE production stage

**Environment Variables (Docker/System):**
- `MONGODB_URI` - MongoDB connection string (default: `mongodb://mongodb:27017`)
- `MONGODB_DATABASE` - Database name (default: `newyork`)
- `MONGODB_COLLECTION` - Collection name (default: `restaurants`)
- `REDIS_HOST` - Redis hostname (default: `localhost`)
- `REDIS_PORT` - Redis port (default: `6379`)
- Key application properties can also be overridden as `MONGODB_URI`, `REDIS_HOST`, etc. (dot notation converted to underscore, uppercased)

## Build Configuration

**Maven Build:**
- Java source/target: 11
- Encoding: UTF-8
- Clean build removes artifacts: `mvn clean package -DskipTests`
- JAR output: `target/quickstart-app-1.0-SNAPSHOT.jar`

**Docker Multi-Stage Build:**
- Stage 1 (builder): Maven 3.8 + Java 21 - compiles, runs unit tests (if not skipped), generates JAR
- Stage 2 (production): Java 21 JRE only - runs JAR, exposes port 8080

## Platform Requirements

**Development:**
- Java 11+ (source/target compatibility)
- Maven 3.8+
- Docker & Docker Compose (plugin version `docker compose`, not `docker-compose`)
- Local MongoDB on `localhost:27017` (for integration tests)

**Production:**
- Docker runtime
- 4 services in Docker Compose network:
  - `restaurant-app` (Spring Boot JAR) - port 8080
  - `mongodb` (latest) - port 27017
  - `redis` (7-alpine) - port 6379
  - `postgres` (15) - port 5432
- Health checks enabled on all services
- Persistent volumes: `mongodb_data`, `postgres_data`

**Minimum Resources:**
- 2GB heap for JVM (Spring Boot defaults)
- 512MB for MongoDB
- 256MB for Redis
- 512MB for PostgreSQL

---

*Stack analysis: 2026-03-27*
