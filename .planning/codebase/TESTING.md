# Testing

**Analysis Date:** 2026-03-27

## Framework

**Primary frameworks:**
- JUnit 4 — used in integration tests and older unit tests (`org.junit.Test`, `@Before`/`@After`)
- JUnit 5 (Jupiter) — used in newer unit tests (`org.junit.jupiter.api.Test`, `@ExtendWith`)
- Mockito — mocking framework for unit tests (`@Mock`, `@InjectMocks`, `MockitoExtension`)

**Mixed JUnit 4/5:** The codebase uses both JUnit versions. Integration tests use JUnit 4; service/util tests trend towards JUnit 5.

**Build integration:**
```bash
mvn test                                          # All unit tests
mvn test -Dtest=RestaurantDAOIntegrationTest      # Integration tests only
mvn verify                                        # All tests including integration
```

## Test Structure

**Location:** `src/test/java/com/aflokkat/`

**Test files (116 total @Test methods):**
```
test/
├── aggregation/
│   └── AggregationPojoTest.java         (6 tests)  — POJO field mapping
├── cache/
│   └── RestaurantCacheServiceTest.java  (8 tests)  — Redis cache service
├── config/
│   ├── AppConfigTest.java               (7 tests)  — Spring config beans
│   └── MongoClientFactoryTest.java      (4 tests)  — MongoDB client factory
├── dao/
│   ├── RestaurantDAOImplTest.java       (5 tests)  — DAO with mocked Mongo
│   └── RestaurantDAOIntegrationTest.java (15 tests) — Integration vs live MongoDB
├── domain/
│   └── RestaurantTest.java             (2 tests)  — Domain POJO behavior
├── security/
│   └── JwtUtilTest.java                (12 tests) — JWT generation/validation
├── service/
│   ├── AuthServiceTest.java            (10 tests) — Auth business logic
│   └── RestaurantServiceTest.java      (21 tests) — Restaurant service logic
├── sync/
│   ├── NycOpenDataClientTest.java      (4 tests)  — API client
│   └── SyncServiceTest.java            (9 tests)  — Sync orchestration
└── util/
    └── ValidationUtilTest.java         (13 tests) — Input validation
```

**Total: ~116 unit tests + 15 integration tests = 131 tests**

## Unit Test Patterns

### Standard JUnit 5 + Mockito pattern (service tests)
```java
@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantDAO restaurantDAO;

    @InjectMocks
    private RestaurantService restaurantService;

    @Test
    void testGetRestaurantsByBorough_returnsResults() {
        // Arrange
        when(restaurantDAO.findByBorough(anyString())).thenReturn(mockList);

        // Act
        List<Restaurant> result = restaurantService.getRestaurantsByBorough("MANHATTAN");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(restaurantDAO).findByBorough("MANHATTAN");
    }
}
```

### JUnit 4 pattern (older tests)
```java
public class ValidationUtilTest {
    @Test
    public void testValidBorough() {
        assertTrue(ValidationUtil.isValidBorough("MANHATTAN"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBorough_throwsException() {
        ValidationUtil.requireValidBorough("INVALID");
    }
}
```

### Integration test pattern
```java
public class RestaurantDAOIntegrationTest {
    private RestaurantDAO restaurantDAO;

    @Before
    public void setUp() {
        // Connects to localhost:27017/newyork
        restaurantDAO = new RestaurantDAOImpl(...);
    }

    @After
    public void tearDown() { /* cleanup */ }

    @Test
    public void testFindByBorough_returnsResults() {
        List<Restaurant> results = restaurantDAO.findByBorough("MANHATTAN");
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }
}
```

## Mocking Strategy

**Service layer:** Mocked DAO via `@Mock` + `@InjectMocks` — no real DB needed
**Cache service:** Mocked Redis `Jedis` client
**Sync service:** Mocked `NycOpenDataClient` and `RestaurantDAO`
**JWT:** Real key generation, no mocks (pure logic)
**Integration tests:** Real MongoDB at `localhost:27017`, DB `newyork` must be populated

## Integration Test Requirements

Integration tests (`RestaurantDAOIntegrationTest`) require:
- MongoDB running on `localhost:27017`
- Database `newyork` with `restaurants` collection populated
- Not run by default — must be explicitly invoked

```bash
# Run integration tests
mvn test -Dtest=RestaurantDAOIntegrationTest

# Skip integration tests (default)
mvn test  # integration tests are not annotated @Ignore but require live DB
```

## Coverage

**Unit coverage:** ~64 unit tests across 12 test classes (before recent expansion to ~116)
**Integration coverage:** 15 tests covering the 4 main DAO use cases
**Gaps:**
- No Spring Boot `@SpringBootTest` / `@WebMvcTest` (no controller tests)
- No Redis failure scenarios / connection error handling
- No SyncService concurrency tests
- No JWT token tampering / replay attack tests
- No end-to-end API tests
- PostgreSQL/JPA layer has no dedicated tests

## Assertions Style

**JUnit 4:** `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull` (static imports from `org.junit.Assert`)
**JUnit 5:** `assertEquals`, `assertNotNull`, `assertThrows` (from `org.junit.jupiter.api.Assertions`)
**Pattern:** Arrange-Act-Assert (AAA) is consistently followed

## Naming Conventions

- Test class: `{Subject}Test.java`
- Test method: descriptive camelCase — e.g., `testGetRestaurantsByBorough_returnsResults`, `testValidBorough`
- Integration test class: `{Subject}IntegrationTest.java`
