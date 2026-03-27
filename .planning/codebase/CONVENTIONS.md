# Coding Conventions

**Analysis Date:** 2026-03-27

## Naming Patterns

**Files:**
- PascalCase for all Java class files: `Restaurant.java`, `RestaurantService.java`, `ValidationUtil.java`
- Interface naming: Interface + Impl pattern for implementations: `RestaurantDAO.java` (interface) → `RestaurantDAOImpl.java` (implementation)
- Test files use Test or IntegrationTest suffix: `RestaurantServiceTest.java`, `RestaurantDAOIntegrationTest.java`

**Functions/Methods:**
- camelCase for all method names
- Getters follow Java bean convention: `getLatestGrade()`, `getBadgeColor()`, `getRestaurantId()`
- Setters follow Java bean convention: `setName()`, `setGrades()`, `setCuisine()`
- Query methods in DAO use `find*` prefix for retrieval operations: `findCountByBorough()`, `findByCuisine()`, `findRandom()`, `findRecentlyInspected()`
- Service methods use `get*` prefix: `getRestaurantCountByBorough()`, `getWorstCuisinesByAverageScoreInBorough()`, `getAtRiskRestaurants()`
- Validation methods use `require*` prefix: `requireNonEmpty()`, `requirePositive()`, `validateFieldName()`
- Helper methods use descriptive verb-noun pattern: `getLatestGradeEntry()`, `restaurantWithGrades()`, `errorResponse()`

**Variables:**
- camelCase for local variables and instance variables: `restaurantDAO`, `cuisineFilter`, `worstCuisines`, `maxScore`
- Constants in UPPER_SNAKE_CASE: `KEY_BY_BOROUGH`, `KEY_CUISINE_SCORES_PREFIX`, `MAX_RETRIES`, `TYPE_AGG_COUNT`
- Private fields use `private` visibility with camelCase: `private RestaurantDAO restaurantDAO`, `private StringRedisTemplate redis`

**Types:**
- PascalCase for class names: `Restaurant`, `Grade`, `Address`, `UserEntity`
- Interface names use descriptive nouns: `RestaurantDAO`
- DTO (Data Transfer Object) suffix pattern: `AuthRequest`, `JwtResponse`, `RegisterRequest`, `HeatmapPoint`
- Entity suffix for JPA entities: `UserEntity`, `BookmarkEntity`
- Service suffix for business logic services: `RestaurantService`, `AuthService`, `RestaurantCacheService`
- Util/Utility suffix for utility/helper classes: `ValidationUtil`, `ResponseUtil`

## Code Style

**Formatting:**
- Java 11 target (source and compiler target set in `pom.xml`)
- UTF-8 encoding enforced via Maven compiler plugin configuration
- No explicit formatter tool configured (follows Spring Boot/Maven defaults)
- Indentation: implied 4 spaces (standard Java convention)

**Linting:**
- No explicit linting tool configured
- Code follows Spring Boot conventions by default

## Import Organization

**Order:**
1. Standard Java imports (`java.util.*`, `java.io.*`, etc.)
2. javax imports (`javax.servlet.*`)
3. Spring imports (`org.springframework.*`)
4. Third-party library imports (`org.mongodb.*`, `io.jsonwebtoken.*`, `com.fasterxml.jackson.*`, `io.swagger.*`)
5. Project-specific imports (`com.aflokkat.*`)

**Path Aliases:**
- No aliases configured; uses fully qualified package names
- Package structure reflects application layers: `com.aflokkat.{domain|service|dao|controller|config|cache|security|util|sync|dto|entity|repository}`

## Error Handling

**Patterns:**
- Input validation throws `IllegalArgumentException` with descriptive messages:
  - Example from `ValidationUtil.requireNonEmpty()`: "fieldName ne peut pas être null ou vide"
  - Example from `ValidationUtil.requirePositive()`: "limit doit être positif, reçu: -1"
- Service layer calls `ValidationUtil` methods before executing business logic (see `RestaurantService.getWorstCuisinesByAverageScoreInBorough()` lines 62-63)
- Controllers catch all exceptions and delegate to `ResponseUtil.errorResponse()` which:
  - Returns HTTP 400 for `IllegalArgumentException` (client error)
  - Returns HTTP 500 for other exceptions (server error)
  - Response format: `{"status": "error", "message": "..."}`
- DAO layer validates field names using `validateFieldName()` to prevent injection attacks (MongoDB aggregation pipelines)
- Cache failures are swallowed with warning logs for graceful degradation (see `RestaurantCacheService` lines 97, 106, 141, 160, 188)

## Logging

**Framework:** SLF4J with Logback (provided by Spring Boot starter-logging)

**Logger declaration pattern:**
```java
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);
```

**Patterns:**
- `logger.info()` for significant operational events: data sync started/completed, cache operations, upsert counts
  - Example: `logger.info("Fetched {} records (total so far: {})", page.size(), all.size());`
- `logger.debug()` for detailed diagnostic information: method entry/exit, query execution
  - Example: `logger.debug("Agrégation: comptage par champ '{}'", fieldName);`
- `logger.warn()` for potentially harmful situations: failed cache operations, API retry attempts
  - Example: `logger.warn("Cache read failed for key {}: {}", key, e.getMessage());`
- `logger.error()` for error conditions: sync failures with stack trace
  - Example: `logger.error("Sync failed: {}", e.getMessage(), e);`
- Application logging level configured in `application.properties`: root=INFO, com.aflokkat=DEBUG

## Comments

**When to Comment:**
- French comments used for conceptual explanation: "Classe utilitaire" (utility class), "Valide qu'une string n'est pas null ou vide" (validates string is not null or empty)
- English comments used for technical/business logic clarification and implementation notes
- Use case headers with comment blocks: `// =============== USE CASE 1 ===============`
- Complex algorithms documented: "Lower score = better (fewer violations)" comment in trend calculation

**JSDoc/JavaDoc:**
- Method-level JavaDoc used for public methods in interfaces and services, particularly:
  - DAO interface methods have full JavaDoc describing parameters and return values
  - Service methods have JavaDoc explaining business logic and use cases
  - Controllers use Swagger/OpenAPI annotations (`@Operation`, `@Parameter`, `@Tag`) instead of raw JavaDoc
- Constructor and field comments minimal; rely on naming clarity
- Example (from `RestaurantDAO.java` lines 14-16): `/** Récupère tous les restaurants avec limite */`

## Function Design

**Size:**
- Service methods typically 3-10 lines for delegation to DAO
- Business logic methods 10-30 lines for computationally intensive operations (e.g., `getHygieneRadarRestaurants()` is 38 lines)
- Helper/utility methods 5-15 lines
- No explicit size limits enforced; follows pragmatic sizing based on cohesion

**Parameters:**
- Validation parameters at method entry using `ValidationUtil` checks
- Service methods pass simple types (String, int, double): `getWorstCuisinesByAverageScoreInBorough(String borough, int limit)`
- DAO methods use `Map<String, Object>` for complex filtering: `findWithFilters(Map<String, Object> filters, int limit)`
- Controllers use `@RequestParam` annotations with default values
- Helper methods use varargs for collections: `restaurantWithGrades(Grade... grades)`

**Return Values:**
- Explicit null returns for "not found" cases: `getLatestGrade()` returns null if no grades
- Empty collections preferred over null for collections: `findAverageScoreByCuisineAndBorough()` returns empty list vs null (see integration test line 105)
- Wrapped responses in controllers: `{"status": "success", "data": [...], "count": N}`
- Static helper methods return computed values: `getBadgeColor()` returns String, `getLatitude()` returns Double

## Module Design

**Exports:**
- All public classes are beans eligible for Spring dependency injection (marked with `@Service`, `@Controller`, `@Configuration`, `@Repository`)
- DAO interface pattern: interface defines contract in `RestaurantDAO.java`, implementation in `RestaurantDAOImpl.java`
- Services delegate to DAOs for persistence operations
- Controllers depend on Services (never directly on DAOs)

**Barrel Files:**
- No barrel/index files used
- Each class is in its own file following Java convention
- Package-level organization provides logical grouping

## Code Structure Patterns

**Layering:**
- Controller → Service → DAO pattern strictly enforced:
  - Controllers (`RestaurantController.java`) call Services
  - Services call DAOs
  - DAOs directly access MongoDB or call cache layer
- Cross-cutting concerns separated:
  - Validation: `ValidationUtil` utility class
  - Response formatting: `ResponseUtil` utility class
  - Caching: `RestaurantCacheService` (service-level cache wrapper)
  - Security: `JwtUtil`, `JwtAuthenticationFilter`, `SecurityConfig`

**DTO vs Domain:**
- Domain POJOs (`Restaurant.java`, `Grade.java`, `Address.java`) map directly from MongoDB using BSON annotations
- DTOs (`AuthRequest.java`, `JwtResponse.java`, `HeatmapPoint.java`) used for API contracts
- Computed fields extracted from domain into service layer (not persisted in POJO): `getLatestGrade()`, `getTrend()`, `getBadgeColor()`
- View mapping method `RestaurantService.toView()` builds view Map for controllers (lines 263-280)

---

*Convention analysis: 2026-03-27*
