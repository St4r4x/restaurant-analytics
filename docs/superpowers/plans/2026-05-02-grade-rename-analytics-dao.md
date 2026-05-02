# Grade Rename + AnalyticsDAO Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the `Grade` name collision between `domain` and `entity` packages, and extract the 3 raw-Document aggregation methods out of `RestaurantDAOImpl` into a new `AnalyticsDAO`/`AnalyticsDAOImpl`.

**Architecture:** Two independent refactors applied sequentially. Task 1–2 rename the two `Grade` types and update all references. Tasks 3–5 create `AnalyticsDAO`/`AnalyticsDAOImpl`, move the 3 methods, wire `AnalyticsController` and `RestaurantController` to the new DAO, and remove the dead service wrappers. No behavior changes anywhere.

**Tech Stack:** Java 25, Spring Boot 4, MongoDB driver (raw `Document` aggregation), JPA/Hibernate, JUnit 5 + Mockito, Maven (`mvn`)

---

## File Map

**Created:**
- `src/main/java/com/st4r4x/dao/AnalyticsDAO.java`
- `src/main/java/com/st4r4x/dao/AnalyticsDAOImpl.java`

**Renamed:**
- `src/main/java/com/st4r4x/domain/Grade.java` → `InspectionRecord.java`
- `src/main/java/com/st4r4x/entity/Grade.java` → `LetterGrade.java`

**Modified (InspectionRecord rename):**
- `src/main/java/com/st4r4x/domain/Restaurant.java`
- `src/main/java/com/st4r4x/sync/SyncService.java`
- `src/main/java/com/st4r4x/service/RestaurantService.java`
- `src/main/java/com/st4r4x/cache/RestaurantCacheService.java`
- `src/test/java/com/st4r4x/cache/RestaurantCacheServiceTest.java`
- `src/test/java/com/st4r4x/service/RestaurantServiceTest.java`

**Modified (LetterGrade rename):**
- `src/main/java/com/st4r4x/entity/InspectionReportEntity.java`
- `src/main/java/com/st4r4x/dto/ReportRequest.java`
- `src/main/java/com/st4r4x/controller/AdminController.java`
- `src/test/java/com/st4r4x/controller/AdminControllerTest.java`
- `src/test/java/com/st4r4x/controller/ReportControllerTest.java`

**Modified (AnalyticsDAO split):**
- `src/main/java/com/st4r4x/dao/RestaurantDAO.java`
- `src/main/java/com/st4r4x/dao/RestaurantDAOImpl.java`
- `src/main/java/com/st4r4x/service/RestaurantService.java`
- `src/main/java/com/st4r4x/controller/AnalyticsController.java`
- `src/main/java/com/st4r4x/controller/RestaurantController.java`
- `src/test/java/com/st4r4x/controller/AnalyticsControllerTest.java`
- `src/test/java/com/st4r4x/controller/RestaurantControllerSearchTest.java`

---

## Task 1: Rename `domain.Grade` → `InspectionRecord`

**Files:**
- Rename: `src/main/java/com/st4r4x/domain/Grade.java` → `src/main/java/com/st4r4x/domain/InspectionRecord.java`
- Modify: `src/main/java/com/st4r4x/domain/Restaurant.java`
- Modify: `src/main/java/com/st4r4x/sync/SyncService.java`
- Modify: `src/main/java/com/st4r4x/service/RestaurantService.java`
- Modify: `src/main/java/com/st4r4x/cache/RestaurantCacheService.java`
- Modify: `src/test/java/com/st4r4x/cache/RestaurantCacheServiceTest.java`
- Modify: `src/test/java/com/st4r4x/service/RestaurantServiceTest.java`

- [ ] **Step 1: Rename the file and update the class declaration**

  In `src/main/java/com/st4r4x/domain/InspectionRecord.java` (was `Grade.java`), change the class name only — leave all fields and methods exactly as-is:

  ```java
  package com.st4r4x.domain;

  import org.bson.codecs.pojo.annotations.BsonProperty;

  public class InspectionRecord {

      @BsonProperty("date")
      private String date;

      @BsonProperty("grade")
      private String grade;

      @BsonProperty("score")
      private Integer score;

      @BsonProperty("inspection_type")
      private String inspectionType;

      @BsonProperty("action")
      private String action;

      @BsonProperty("violation_code")
      private String violationCode;

      @BsonProperty("violation_description")
      private String violationDescription;

      @BsonProperty("critical_flag")
      private String criticalFlag;

      public InspectionRecord() {}

      public String getDate() { return date; }
      public void setDate(String date) { this.date = date; }

      public String getGrade() { return grade; }
      public void setGrade(String grade) { this.grade = grade; }

      public Integer getScore() { return score; }
      public void setScore(Integer score) { this.score = score; }

      public String getInspectionType() { return inspectionType; }
      public void setInspectionType(String inspectionType) { this.inspectionType = inspectionType; }

      public String getAction() { return action; }
      public void setAction(String action) { this.action = action; }

      public String getViolationCode() { return violationCode; }
      public void setViolationCode(String violationCode) { this.violationCode = violationCode; }

      public String getViolationDescription() { return violationDescription; }
      public void setViolationDescription(String violationDescription) { this.violationDescription = violationDescription; }

      public String getCriticalFlag() { return criticalFlag; }
      public void setCriticalFlag(String criticalFlag) { this.criticalFlag = criticalFlag; }

      @Override
      public String toString() {
          return "InspectionRecord{date='" + date + "', grade='" + grade + "', score=" + score
                  + ", inspectionType='" + inspectionType + "'}";
      }
  }
  ```

  Delete `src/main/java/com/st4r4x/domain/Grade.java`.

- [ ] **Step 2: Update `Restaurant.java`**

  In `src/main/java/com/st4r4x/domain/Restaurant.java`, replace the import and the two field references:

  ```java
  // Replace this import:
  import com.st4r4x.domain.Grade;
  // With:
  import com.st4r4x.domain.InspectionRecord;
  ```

  Replace field declaration:
  ```java
  // Before:
  private List<Grade> grades;
  // After:
  private List<InspectionRecord> grades;
  ```

  Replace getter/setter signatures:
  ```java
  // Before:
  public List<Grade> getGrades() { return grades; }
  public void setGrades(List<Grade> grades) { this.grades = grades; }
  // After:
  public List<InspectionRecord> getGrades() { return grades; }
  public void setGrades(List<InspectionRecord> grades) { this.grades = grades; }
  ```

- [ ] **Step 3: Update `SyncService.java`**

  In `src/main/java/com/st4r4x/sync/SyncService.java`:

  ```java
  // Replace import:
  import com.st4r4x.domain.Grade;
  // With:
  import com.st4r4x.domain.InspectionRecord;
  ```

  Inside `buildRestaurant()`, replace all `Grade` references:
  ```java
  // Before:
  List<Grade> grades = new ArrayList<>();
  // ...
  Grade grade = new Grade();
  // After:
  List<InspectionRecord> grades = new ArrayList<>();
  // ...
  InspectionRecord grade = new InspectionRecord();
  ```

- [ ] **Step 4: Update `RestaurantService.java`**

  In `src/main/java/com/st4r4x/service/RestaurantService.java`:

  ```java
  // Replace import:
  import com.st4r4x.domain.Grade;
  // With:
  import com.st4r4x.domain.InspectionRecord;
  ```

  Replace all occurrences of `Grade` used as the domain type (lines 233, 242, 247, 252, 254):
  ```java
  // Before:
  List<Grade> grades = r.getGrades();
  Grade g = getLatestGradeEntry(r);
  List<Grade> sorted = grades.stream()
  // After:
  List<InspectionRecord> grades = r.getGrades();
  InspectionRecord g = getLatestGradeEntry(r);
  List<InspectionRecord> sorted = grades.stream()
  ```

  Also update the private helper method signature (find `getLatestGradeEntry`):
  ```java
  // Before:
  private Grade getLatestGradeEntry(Restaurant r) {
  // After:
  private InspectionRecord getLatestGradeEntry(Restaurant r) {
  ```

- [ ] **Step 5: Update `RestaurantCacheService.java`**

  In `src/main/java/com/st4r4x/cache/RestaurantCacheService.java`:

  ```java
  // Replace import:
  import com.st4r4x.domain.Grade;
  // With:
  import com.st4r4x.domain.InspectionRecord;
  ```

  Replace the loop variable (line 127):
  ```java
  // Before:
  for (Grade g : r.getGrades()) {
  // After:
  for (InspectionRecord g : r.getGrades()) {
  ```

- [ ] **Step 6: Update `RestaurantCacheServiceTest.java`**

  In `src/test/java/com/st4r4x/cache/RestaurantCacheServiceTest.java`:

  ```java
  // Replace import:
  import com.st4r4x.domain.Grade;
  // With:
  import com.st4r4x.domain.InspectionRecord;
  ```

  Replace all `Grade` references in test methods:
  ```java
  // Before (line 101):
  r.setGrades(Collections.singletonList(new Grade()));
  // After:
  r.setGrades(Collections.singletonList(new InspectionRecord()));

  // Before (line 111):
  Grade grade = new Grade();
  // After:
  InspectionRecord grade = new InspectionRecord();
  ```

- [ ] **Step 7: Update `RestaurantServiceTest.java`**

  In `src/test/java/com/st4r4x/service/RestaurantServiceTest.java`:

  ```java
  // Replace import:
  import com.st4r4x.domain.Grade;
  // With:
  import com.st4r4x.domain.InspectionRecord;
  ```

  Replace the helper factory method (lines 206-207):
  ```java
  // Before:
  private static Grade grade(String letter, int score, String date) {
      Grade g = new Grade();
  // After:
  private static InspectionRecord grade(String letter, int score, String date) {
      InspectionRecord g = new InspectionRecord();
  ```

- [ ] **Step 8: Verify the build compiles**

  ```bash
  mvn compile -q
  ```

  Expected: `BUILD SUCCESS` with no errors. If you see `cannot find symbol` for `Grade`, search for any missed references:
  ```bash
  grep -rn "domain\.Grade\|import com\.st4r4x\.domain\.Grade" src/ --include="*.java"
  ```

- [ ] **Step 9: Run the tests**

  ```bash
  mvn test -q
  ```

  Expected: `BUILD SUCCESS`. All tests pass.

- [ ] **Step 10: Commit**

  ```bash
  git add src/main/java/com/st4r4x/domain/InspectionRecord.java \
          src/main/java/com/st4r4x/domain/Restaurant.java \
          src/main/java/com/st4r4x/sync/SyncService.java \
          src/main/java/com/st4r4x/service/RestaurantService.java \
          src/main/java/com/st4r4x/cache/RestaurantCacheService.java \
          src/test/java/com/st4r4x/cache/RestaurantCacheServiceTest.java \
          src/test/java/com/st4r4x/service/RestaurantServiceTest.java
  git rm src/main/java/com/st4r4x/domain/Grade.java
  git commit -m "refactor: rename domain.Grade to InspectionRecord"
  ```

---

## Task 2: Rename `entity.Grade` → `LetterGrade`

**Files:**
- Rename: `src/main/java/com/st4r4x/entity/Grade.java` → `src/main/java/com/st4r4x/entity/LetterGrade.java`
- Modify: `src/main/java/com/st4r4x/entity/InspectionReportEntity.java`
- Modify: `src/main/java/com/st4r4x/dto/ReportRequest.java`
- Modify: `src/main/java/com/st4r4x/controller/AdminController.java`
- Modify: `src/test/java/com/st4r4x/controller/AdminControllerTest.java`
- Modify: `src/test/java/com/st4r4x/controller/ReportControllerTest.java`

- [ ] **Step 1: Rename the file and update the enum declaration**

  Create `src/main/java/com/st4r4x/entity/LetterGrade.java`:

  ```java
  package com.st4r4x.entity;

  public enum LetterGrade { A, B, C, F }
  ```

  Delete `src/main/java/com/st4r4x/entity/Grade.java`.

- [ ] **Step 2: Update `InspectionReportEntity.java`**

  In `src/main/java/com/st4r4x/entity/InspectionReportEntity.java`, update the import and field:

  ```java
  // Replace import (if present — may be same package, no import needed):
  // entity.Grade is in the same package, so there may be no explicit import.
  // Check with:
  //   grep -n "import.*Grade" src/main/java/com/st4r4x/entity/InspectionReportEntity.java
  ```

  Replace field declaration and accessor:
  ```java
  // Before:
  private Grade grade;
  // ...
  public void setGrade(Grade grade) { this.grade = grade; }
  // After:
  private LetterGrade grade;
  // ...
  public void setGrade(LetterGrade grade) { this.grade = grade; }
  ```

  Also update the getter return type:
  ```java
  // Before:
  public Grade getGrade() { return grade; }
  // After:
  public LetterGrade getGrade() { return grade; }
  ```

- [ ] **Step 3: Update `ReportRequest.java`**

  In `src/main/java/com/st4r4x/dto/ReportRequest.java`:

  ```java
  // Replace import:
  import com.st4r4x.entity.Grade;
  // With:
  import com.st4r4x.entity.LetterGrade;
  ```

  Replace field and accessors:
  ```java
  // Before:
  private Grade grade;
  // ...
  public Grade getGrade() { return grade; }
  public void setGrade(Grade grade) { this.grade = grade; }
  // After:
  private LetterGrade grade;
  // ...
  public LetterGrade getGrade() { return grade; }
  public void setGrade(LetterGrade grade) { this.grade = grade; }
  ```

- [ ] **Step 4: Update `AdminController.java`**

  In `src/main/java/com/st4r4x/controller/AdminController.java`:

  ```java
  // Replace import:
  import com.st4r4x.entity.Grade;
  // With:
  import com.st4r4x.entity.LetterGrade;
  ```

  Replace both usages (lines ~50 and ~54):
  ```java
  // Before:
  for (Grade g : Grade.values()) {
  // ...
  Grade grade = (Grade) row[0];
  // After:
  for (LetterGrade g : LetterGrade.values()) {
  // ...
  LetterGrade grade = (LetterGrade) row[0];
  ```

- [ ] **Step 5: Update `AdminControllerTest.java`**

  In `src/test/java/com/st4r4x/controller/AdminControllerTest.java`:

  ```java
  // Replace import:
  import com.st4r4x.entity.Grade;
  // With:
  import com.st4r4x.entity.LetterGrade;
  ```

  Replace all `Grade.` usages with `LetterGrade.` — e.g. `Grade.A` → `LetterGrade.A`.

- [ ] **Step 6: Update `ReportControllerTest.java`**

  In `src/test/java/com/st4r4x/controller/ReportControllerTest.java`:

  ```java
  // Replace import:
  import com.st4r4x.entity.Grade;
  // With:
  import com.st4r4x.entity.LetterGrade;
  ```

  Replace the helper method signature and usages:
  ```java
  // Before:
  private InspectionReportEntity makeEntity(Long id, String restaurantId, Grade grade, Status status) {
  // After:
  private InspectionReportEntity makeEntity(Long id, String restaurantId, LetterGrade grade, Status status) {
  ```

  Replace all call sites e.g. `Grade.A` → `LetterGrade.A`, `Grade.B` → `LetterGrade.B`, etc.

- [ ] **Step 7: Verify the build compiles**

  ```bash
  mvn compile -q
  ```

  Expected: `BUILD SUCCESS`. If you see `cannot find symbol` for `Grade` in the entity package:
  ```bash
  grep -rn "entity\.Grade\|import com\.st4r4x\.entity\.Grade" src/ --include="*.java"
  ```

- [ ] **Step 8: Run the tests**

  ```bash
  mvn test -q
  ```

  Expected: `BUILD SUCCESS`. All tests pass.

- [ ] **Step 9: Commit**

  ```bash
  git add src/main/java/com/st4r4x/entity/LetterGrade.java \
          src/main/java/com/st4r4x/entity/InspectionReportEntity.java \
          src/main/java/com/st4r4x/dto/ReportRequest.java \
          src/main/java/com/st4r4x/controller/AdminController.java \
          src/test/java/com/st4r4x/controller/AdminControllerTest.java \
          src/test/java/com/st4r4x/controller/ReportControllerTest.java
  git rm src/main/java/com/st4r4x/entity/Grade.java
  git commit -m "refactor: rename entity.Grade to LetterGrade"
  ```

---

## Task 3: Create `AnalyticsDAO` interface and `AnalyticsDAOImpl`

**Files:**
- Create: `src/main/java/com/st4r4x/dao/AnalyticsDAO.java`
- Create: `src/main/java/com/st4r4x/dao/AnalyticsDAOImpl.java`

- [ ] **Step 1: Create `AnalyticsDAO.java`**

  ```java
  package com.st4r4x.dao;

  import java.util.List;
  import org.bson.Document;

  public interface AnalyticsDAO {
      List<Document> findMapPoints();
      List<Document> findBoroughGradeDistribution();
      long countAtRiskRestaurants();
  }
  ```

- [ ] **Step 2: Create `AnalyticsDAOImpl.java`**

  Copy the 3 method bodies verbatim from `RestaurantDAOImpl`. The impl holds its own `database` and `collection` references — same constructor pattern as `RestaurantDAOImpl`.

  ```java
  package com.st4r4x.dao;

  import java.util.ArrayList;
  import java.util.Arrays;
  import java.util.List;

  import org.bson.Document;
  import org.springframework.stereotype.Repository;

  import com.st4r4x.config.AppConfig;
  import com.st4r4x.config.MongoClientFactory;
  import com.mongodb.client.MongoClient;
  import com.mongodb.client.MongoDatabase;

  @Repository
  public class AnalyticsDAOImpl implements AnalyticsDAO {

      private final MongoDatabase database;
      private final String collectionName;

      public AnalyticsDAOImpl() {
          MongoClient mongoClient = MongoClientFactory.getInstance();
          this.database = mongoClient.getDatabase(AppConfig.getMongoDatabase());
          this.collectionName = AppConfig.getMongoCollection();
      }

      @Override
      public List<Document> findMapPoints() {
          List<Document> pipeline = Arrays.asList(
              new Document("$match", new Document("address.coord", new Document("$exists", true))),
              new Document("$project", new Document("_id", 0)
                  .append("restaurantId", "$restaurant_id")
                  .append("name", 1)
                  .append("grade", new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))
                  .append("borough", 1)
                  .append("cuisine", 1)
                  .append("lat",  new Document("$arrayElemAt", Arrays.asList("$address.coord", 1)))
                  .append("lng",  new Document("$arrayElemAt", Arrays.asList("$address.coord", 0)))
              )
          );
          List<Document> results = new ArrayList<>();
          database.getCollection(collectionName)
                  .aggregate(pipeline)
                  .forEach(results::add);
          return results;
      }

      @Override
      public List<Document> findBoroughGradeDistribution() {
          List<Document> pipeline = Arrays.asList(
              new Document("$addFields", new Document("lastGrade",
                  new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))),
              new Document("$match", new Document("lastGrade",
                  new Document("$in", Arrays.asList("A", "B", "C")))),
              new Document("$group", new Document()
                  .append("_id", new Document()
                      .append("borough", "$borough")
                      .append("grade", "$lastGrade"))
                  .append("count", new Document("$sum", 1))),
              new Document("$group", new Document()
                  .append("_id", "$_id.borough")
                  .append("grades", new Document("$push", new Document()
                      .append("grade", "$_id.grade")
                      .append("count", "$count")))),
              new Document("$sort", new Document("_id", 1))
          );
          List<Document> results = new ArrayList<>();
          database.getCollection(collectionName)
              .aggregate(pipeline)
              .forEach(results::add);
          return results;
      }

      @Override
      public long countAtRiskRestaurants() {
          List<Document> pipeline = Arrays.asList(
              new Document("$addFields", new Document("lastGrade",
                  new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))),
              new Document("$match", new Document("lastGrade",
                  new Document("$in", Arrays.asList("C", "Z")))),
              new Document("$count", "total")
          );
          List<Document> results = new ArrayList<>();
          database.getCollection(collectionName)
              .aggregate(pipeline)
              .forEach(results::add);
          return results.isEmpty() ? 0L : (long) results.get(0).getInteger("total", 0);
      }
  }
  ```

- [ ] **Step 3: Verify it compiles**

  ```bash
  mvn compile -q
  ```

  Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/com/st4r4x/dao/AnalyticsDAO.java \
          src/main/java/com/st4r4x/dao/AnalyticsDAOImpl.java
  git commit -m "feat: add AnalyticsDAO interface and AnalyticsDAOImpl"
  ```

---

## Task 4: Remove the 3 methods from `RestaurantDAO` and `RestaurantDAOImpl`

**Files:**
- Modify: `src/main/java/com/st4r4x/dao/RestaurantDAO.java`
- Modify: `src/main/java/com/st4r4x/dao/RestaurantDAOImpl.java`

- [ ] **Step 1: Remove from `RestaurantDAO.java`**

  Delete the three method declarations. The methods to remove are (check current line numbers with `grep -n "findMapPoints\|findBoroughGradeDistribution\|countAtRiskRestaurants" src/main/java/com/st4r4x/dao/RestaurantDAO.java`):

  ```java
  // Remove this declaration:
  List<org.bson.Document> findBoroughGradeDistribution();

  // Remove this declaration:
  long countAtRiskRestaurants();

  // Remove this declaration:
  List<org.bson.Document> findMapPoints();
  ```

  Also remove any Javadoc comment blocks directly above these declarations.

- [ ] **Step 2: Remove from `RestaurantDAOImpl.java`**

  Delete the three method implementations:
  - `findMapPoints()` (lines ~365–384)
  - `findBoroughGradeDistribution()` (lines ~386–409)
  - `countAtRiskRestaurants()` (lines ~426–439)

  Locate them with:
  ```bash
  grep -n "findMapPoints\|findBoroughGradeDistribution\|countAtRiskRestaurants" src/main/java/com/st4r4x/dao/RestaurantDAOImpl.java
  ```

- [ ] **Step 3: Verify it compiles**

  ```bash
  mvn compile -q
  ```

  Expected: `BUILD SUCCESS`. If you see errors about `findMapPoints` etc. still being called, those callers are addressed in Task 5 — but compilation should succeed at this point because the callers (`AnalyticsController`, `RestaurantController`, `RestaurantService`) still reference `restaurantDAO` which no longer has these methods, causing compile errors. That is expected — proceed to Task 5 immediately.

  > **Note:** If Maven reports compile errors referencing these 3 method names in controllers or service, that is expected and will be fixed in Task 5. Do NOT commit yet — continue to Task 5 first, then verify + commit both tasks together.

---

## Task 5: Wire callers to `AnalyticsDAO`, remove dead service wrappers

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/AnalyticsController.java`
- Modify: `src/main/java/com/st4r4x/controller/RestaurantController.java`
- Modify: `src/main/java/com/st4r4x/service/RestaurantService.java`
- Modify: `src/test/java/com/st4r4x/controller/AnalyticsControllerTest.java`
- Modify: `src/test/java/com/st4r4x/controller/RestaurantControllerSearchTest.java`

- [ ] **Step 1: Update `AnalyticsController.java`**

  Add the `AnalyticsDAO` import and field. Keep `RestaurantDAO` — it is still used for `countAll`, `findWorstCuisinesByAverageScore`, `findBestCuisinesByAverageScore`, `findAtRiskRestaurants`.

  ```java
  // Add import (alongside existing RestaurantDAO import):
  import com.st4r4x.dao.AnalyticsDAO;
  ```

  Add the field below the existing `restaurantDAO` field:
  ```java
  @Autowired
  private AnalyticsDAO analyticsDAO;
  ```

  Replace all 3 call sites:
  ```java
  // Before:
  long atRisk = restaurantDAO.countAtRiskRestaurants();
  // After:
  long atRisk = analyticsDAO.countAtRiskRestaurants();

  // Before (appears twice — both occurrences):
  List<Document> boroughData = restaurantDAO.findBoroughGradeDistribution();
  List<Document> raw = restaurantDAO.findBoroughGradeDistribution();
  // After:
  List<Document> boroughData = analyticsDAO.findBoroughGradeDistribution();
  List<Document> raw = analyticsDAO.findBoroughGradeDistribution();
  ```

- [ ] **Step 2: Update `RestaurantController.java`**

  Add the `AnalyticsDAO` import and field. Keep `RestaurantDAO` — it is used for all other methods.

  ```java
  // Add import:
  import com.st4r4x.dao.AnalyticsDAO;
  ```

  Add field:
  ```java
  @Autowired
  private AnalyticsDAO analyticsDAO;
  ```

  Replace the one call site (line ~393):
  ```java
  // Before:
  List<Document> data = restaurantDAO.findMapPoints();
  // After:
  List<Document> data = analyticsDAO.findMapPoints();
  ```

- [ ] **Step 3: Remove dead wrappers from `RestaurantService.java`**

  `RestaurantService` has two methods that now delegate to a DAO method that no longer exists on `RestaurantDAO`. Nothing calls these service methods (verified: no callers outside the service itself). Delete them entirely:

  ```java
  // Remove this method (~lines 144-148):
  public List<Document> findBoroughGradeDistribution() {
      return restaurantDAO.findBoroughGradeDistribution();
  }

  // Remove this method (~lines 170-172):
  public long countAtRiskRestaurants() {
      return restaurantDAO.countAtRiskRestaurants();
  }
  ```

  Also remove the `Document` import from `RestaurantService` if it is now unused:
  ```bash
  grep -n "Document" src/main/java/com/st4r4x/service/RestaurantService.java
  ```
  If `Document` no longer appears in any method body or return type, remove `import org.bson.Document;`.

- [ ] **Step 4: Update `AnalyticsControllerTest.java`**

  Add `AnalyticsDAO` mock and inject it into the controller under test. Remove the 3 method stubs from the `RestaurantDAO` mock.

  ```java
  // Add import:
  import com.st4r4x.dao.AnalyticsDAO;
  ```

  Add mock field (alongside existing `@Mock RestaurantDAO restaurantDAO`):
  ```java
  @Mock
  private AnalyticsDAO analyticsDAO;
  ```

  In the `setUp()` / `@BeforeEach` method (or wherever the controller is instantiated with `standaloneSetup`), inject `analyticsDAO` into the controller. If the controller is constructed with `new AnalyticsController()` + field injection via `@InjectMocks`, Mockito will auto-inject both mocks.

  If the controller is instantiated manually, add:
  ```java
  controller.analyticsDAO = analyticsDAO;  // only if field is package-visible
  // Or use reflection if the field is private — check existing test pattern for RestaurantDAO injection
  ```

  Move the 3 stubs from `restaurantDAO` to `analyticsDAO`:
  ```java
  // Before:
  when(restaurantDAO.countAtRiskRestaurants()).thenReturn(412L);
  when(restaurantDAO.findBoroughGradeDistribution()).thenReturn(Collections.emptyList());
  // After:
  when(analyticsDAO.countAtRiskRestaurants()).thenReturn(412L);
  when(analyticsDAO.findBoroughGradeDistribution()).thenReturn(Collections.emptyList());
  ```

  Check all test methods in this file for any other `restaurantDAO.findBoroughGradeDistribution()` or `restaurantDAO.countAtRiskRestaurants()` stubs and move them to `analyticsDAO` as well.

- [ ] **Step 5: Update `RestaurantControllerSearchTest.java`**

  Add `AnalyticsDAO` mock. Move the `findMapPoints` stub.

  ```java
  // Add import:
  import com.st4r4x.dao.AnalyticsDAO;
  ```

  Add mock field:
  ```java
  @Mock
  private AnalyticsDAO analyticsDAO;
  ```

  Inject into the controller under test — same pattern as Step 4 above (check how `restaurantDAO` is currently injected in this test).

  Move the stub:
  ```java
  // Before (line ~99):
  when(restaurantDAO.findMapPoints())...
  // After:
  when(analyticsDAO.findMapPoints())...
  ```

- [ ] **Step 6: Verify the build compiles**

  ```bash
  mvn compile -q
  ```

  Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Run the full test suite**

  ```bash
  mvn test -q
  ```

  Expected: `BUILD SUCCESS`. All tests pass. If a test fails with `WantedButNotInvoked` or `UnnecessaryStubbingException`, it means a stub was moved to the wrong mock — compare the failing test's method name against Steps 4-5 and move the stub to the correct mock.

- [ ] **Step 8: Commit Tasks 4 and 5 together**

  ```bash
  git add src/main/java/com/st4r4x/dao/RestaurantDAO.java \
          src/main/java/com/st4r4x/dao/RestaurantDAOImpl.java \
          src/main/java/com/st4r4x/service/RestaurantService.java \
          src/main/java/com/st4r4x/controller/AnalyticsController.java \
          src/main/java/com/st4r4x/controller/RestaurantController.java \
          src/test/java/com/st4r4x/controller/AnalyticsControllerTest.java \
          src/test/java/com/st4r4x/controller/RestaurantControllerSearchTest.java
  git commit -m "refactor: extract AnalyticsDAO from RestaurantDAO"
  ```
