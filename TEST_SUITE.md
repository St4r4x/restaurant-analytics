# Test Suite Documentation

## Overview

This project includes a comprehensive test suite covering unit tests, integration tests, and POJO tests.

## Test Structure

### 1. Unit Tests

These tests focus on individual components without external dependencies.

#### ValidationUtilTest

- **Location**: `src/test/java/com/aflokkat/util/ValidationUtilTest.java`
- **Coverage**: Validation utility methods
- **Tests**:
  - ✅ `testRequireNonEmpty_ValidString` - Valid strings pass validation
  - ✅ `testRequireNonEmpty_NullString` - Null strings throw exception
  - ✅ `testRequireNonEmpty_EmptyString` - Empty strings throw exception
  - ✅ `testRequireNonEmpty_WhitespaceString` - Whitespace-only strings throw exception
  - ✅ `testRequirePositive_*` - Positive number validation (5 tests)
  - ✅ `testValidateFieldName_*` - Field name validation with regex (6 tests)

**Total: 14 test cases**

#### AppConfigTest

- **Location**: `src/test/java/com/aflokkat/config/AppConfigTest.java`
- **Coverage**: Configuration loading from properties and .env
- **Tests**:
  - ✅ Configuration values are loaded correctly
  - ✅ Default values are used when environment variables are absent
  - ✅ Configuration is not null

**Total: 6 test cases**

#### MongoClientFactoryTest

- **Location**: `src/test/java/com/aflokkat/config/MongoClientFactoryTest.java`
- **Coverage**: Singleton pattern implementation
- **Tests**:
  - ✅ `testGetInstance_ReturnsMongoClient` - Factory returns valid client
  - ✅ `testGetInstance_SingletonPattern` - Same instance on multiple calls
  - ✅ `testGetInstance_MultipleCallsReturnSame` - Consistency across 3+ calls
  - ✅ `testFactoryPattern_ThreadSafe` - Thread-safety validation (10 concurrent threads)

**Total: 4 test cases**

#### POJOTest

- **Location**: `src/test/java/com/aflokkat/POJOTest.java`
- **Coverage**: Data objects (Restaurant, AggregationCount, etc.)
- **Tests**:
  - ✅ Restaurant POJO creation and properties (2 tests)
  - ✅ AggregationCount POJO creation and properties (2 tests)
  - ✅ BoroughCuisineScore POJO creation and properties (2 tests)
  - ✅ CuisineScore POJO creation and properties (2 tests)

**Total: 8 test cases**

### 2. Integration Tests

These tests validate end-to-end functionality with a real MongoDB connection.

#### RestaurantDAOIntegrationTest

- **Location**: `src/test/java/com/aflokkat/RestaurantDAOIntegrationTest.java`
- **Requirements**: MongoDB running on localhost:27017 with "newyork" database
- **Coverage**: All 4 main use cases + validation + generic queries

**Use Case 1: Restaurant Count by Borough**

- ✅ `testUseCase1_GetRestaurantCountByBorough_ReturnsData` - Returns non-empty results
- ✅ `testUseCase1_CountByBorough_DataValidation` - All counts are positive
- ✅ `testUseCase1_CountByBorough_SortedDescending` - Results are sorted correctly

**Use Case 2: Average Score by Cuisine and Borough**

- ✅ `testUseCase2_GetAverageScoreByCuisineAndBorough_Italian` - Italian cuisine returns data
- ✅ `testUseCase2_AverageScore_ValidData` - Valid scores for known cuisine
- ✅ `testUseCase2_AverageScore_InvalidCuisine` - Graceful handling of unknown cuisine

**Use Case 3: Worst Cuisines**

- ✅ `testUseCase3_GetWorstCuisines_Manhattan` - Returns top N worst cuisines
- ✅ `testUseCase3_WorstCuisines_ValidData` - Valid data structure
- ✅ `testUseCase3_WorstCuisines_SortedByScore` - Results sorted ascending (worst first)

**Use Case 4: Cuisines with Minimum Count**

- ✅ `testUseCase4_GetCuisinesWithMinimumCount_500` - Returns cuisines with >500 restaurants
- ✅ `testUseCase4_CuisinesWithMinCount_Alphabetical` - Results alphabetically sorted
- ✅ `testUseCase4_CuisinesWithHighMinCount` - Respects minimum count threshold

**Input Validation Tests**

- ✅ `testFindAll_NegativeLimit` - Rejects negative limits
- ✅ `testFindAll_ZeroLimit` - Rejects zero limits
- ✅ `testFindByCuisine_EmptyCuisine` - Rejects empty strings
- ✅ `testFindByCuisine_NullCuisine` - Rejects null inputs
- ✅ `testCountByField_*` - Rejects invalid field names

**Generic Query Tests**

- ✅ `testCountAll_ReturnsPositiveNumber` - Total restaurant count is valid
- ✅ `testCountByCuisine_Italian` - Cuisine-specific counts work
- ✅ `testFindByCuisine_Italian` - Query by cuisine returns results

**Total: 22 test cases**

#### RestaurantDAOImplTest

- **Location**: `src/test/java/com/aflokkat/RestaurantDAOImplTest.java`
- **Purpose**: Unit test structure for DAO (requires refactoring for full coverage)
- **Note**: Full unit testing of RestaurantDAOImpl requires constructor injection of MongoClient

## Running Tests

### Run all tests

```bash
mvn test
```

### Run specific test class

```bash
mvn test -Dtest=ValidationUtilTest
mvn test -Dtest=AppConfigTest
mvn test -Dtest=MongoClientFactoryTest
```

### Run only unit tests (fast, no MongoDB required)

```bash
mvn test -Dtest="*Test" -DexcludedGroups=integration
```

### Run only integration tests (requires MongoDB)

```bash
mvn test -Dtest=RestaurantDAOIntegrationTest
```

### Run tests with coverage

```bash
mvn test jacoco:report
```

## Test Coverage Summary

| Component          | Test Class                   | Unit Tests | Integration Tests | Total  |
| ------------------ | ---------------------------- | ---------- | ----------------- | ------ |
| ValidationUtil     | ValidationUtilTest           | 14         | -                 | 14     |
| AppConfig          | AppConfigTest                | 6          | -                 | 6      |
| MongoClientFactory | MongoClientFactoryTest       | 4          | -                 | 4      |
| POJO Objects       | POJOTest                     | 8          | -                 | 8      |
| RestaurantDAO      | RestaurantDAOIntegrationTest | -          | 22                | 22     |
| **TOTAL**          |                              | **32**     | **22**            | **54** |

## Coverage Report

To generate a detailed coverage report:

```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

## Test Best Practices

1. ✅ **Isolation**: Unit tests are independent and don't require MongoDB
2. ✅ **Validation**: Comprehensive input validation testing
3. ✅ **Edge Cases**: Tests cover null, empty, negative, and boundary values
4. ✅ **Thread Safety**: Singleton pattern verified for concurrent access
5. ✅ **Integration**: Full end-to-end validation of all 4 use cases
6. ✅ **Documentation**: Each test class is well-documented

## Known Limitations

1. **RestaurantDAOImpl**: Full unit testing requires constructor injection of MongoClient
   - **Workaround**: Use integration tests or refactor to allow dependency injection
2. **Embedded MongoDB**: Integration tests require live MongoDB instance
   - **Enhancement**: Can be replaced with embedded MongoDB or Testcontainers

## Future Enhancements

- [ ] Add embedded MongoDB for integration tests (testcontainers)
- [ ] Add mock MongoDB tests with Mockito
- [ ] Add performance benchmark tests
- [ ] Add security tests for SQL/injection vulnerabilities
- [ ] Add stress tests for concurrent access
- [ ] Add data validation tests for POJO mapping

## Dependencies

```xml
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.2.0</version>
    <scope>test</scope>
</dependency>
```
