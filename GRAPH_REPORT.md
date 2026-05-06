# Graph Report - .  (2026-05-06)

## Corpus Check
- 122 files · ~58,057 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1266 nodes · 2399 edges · 71 communities detected
- Extraction: 59% EXTRACTED · 41% INFERRED · 0% AMBIGUOUS · INFERRED: 986 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Address Domain Model|Address Domain Model]]
- [[_COMMUNITY_Application Bootstrap & Auth DTOs|Application Bootstrap & Auth DTOs]]
- [[_COMMUNITY_Controller & Aggregation Classes|Controller & Aggregation Classes]]
- [[_COMMUNITY_AggregationCount POJO|AggregationCount POJO]]
- [[_COMMUNITY_AppConfig & AnalyticsDAO Implementation|AppConfig & AnalyticsDAO Implementation]]
- [[_COMMUNITY_Restaurant REST Controller|Restaurant REST Controller]]
- [[_COMMUNITY_Bookmark Entity & JPA Layer|Bookmark Entity & JPA Layer]]
- [[_COMMUNITY_Admin Cron Endpoint & Tests|Admin Cron Endpoint & Tests]]
- [[_COMMUNITY_Service  Cache Semantic Layer|Service / Cache Semantic Layer]]
- [[_COMMUNITY_Elasticsearch Sync Service|Elasticsearch Sync Service]]
- [[_COMMUNITY_Restaurant Domain Model|Restaurant Domain Model]]
- [[_COMMUNITY_Security Config & JWT Filter|Security Config & JWT Filter]]
- [[_COMMUNITY_Admin OSM Enrichment Endpoint|Admin OSM Enrichment Endpoint]]
- [[_COMMUNITY_Restaurant DAO Implementation|Restaurant DAO Implementation]]
- [[_COMMUNITY_ViewController & Frontend Tests|ViewController & Frontend Tests]]
- [[_COMMUNITY_Admin Stats Endpoint & Tests|Admin Stats Endpoint & Tests]]
- [[_COMMUNITY_AnalyticsDAO Implementation|AnalyticsDAO Implementation]]
- [[_COMMUNITY_ReportRequest DTO|ReportRequest DTO]]
- [[_COMMUNITY_Map Points & Search API|Map Points & Search API]]
- [[_COMMUNITY_TopRestaurantEntry DTO|TopRestaurantEntry DTO]]
- [[_COMMUNITY_Auth Controller Concepts|Auth Controller Concepts]]
- [[_COMMUNITY_HeatmapPoint DTO|HeatmapPoint DTO]]
- [[_COMMUNITY_JWT Utilities|JWT Utilities]]
- [[_COMMUNITY_JWT Service|JWT Service]]
- [[_COMMUNITY_RestaurantDAO Unit Tests|RestaurantDAO Unit Tests]]
- [[_COMMUNITY_Admin Controller Test Classes|Admin Controller Test Classes]]
- [[_COMMUNITY_Grade Rename Refactor Plan|Grade Rename Refactor Plan]]
- [[_COMMUNITY_Auth Controller|Auth Controller]]
- [[_COMMUNITY_ViewController Test Classes|ViewController Test Classes]]
- [[_COMMUNITY_RefreshRequest DTO|RefreshRequest DTO]]
- [[_COMMUNITY_ResponseUtil Helper|ResponseUtil Helper]]
- [[_COMMUNITY_RestaurantWriteDAO|RestaurantWriteDAO]]
- [[_COMMUNITY_OpenAPI Config|OpenAPI Config]]
- [[_COMMUNITY_NYC Open Data Client|NYC Open Data Client]]
- [[_COMMUNITY_Frontend Redesign Plan|Frontend Redesign Plan]]
- [[_COMMUNITY_Module Group 35 (docs)|Module Group 35 (docs)]]
- [[_COMMUNITY_Module Group 36 (plan)|Module Group 36 (plan)]]
- [[_COMMUNITY_Module Group 37 (methodsecurityconfig)|Module Group 37 (methodsecurityconfig)]]
- [[_COMMUNITY_Module Group 38 (config)|Module Group 38 (config)]]
- [[_COMMUNITY_Module Group 39 (docs)|Module Group 39 (docs)]]
- [[_COMMUNITY_Module Group 40 (plan)|Module Group 40 (plan)]]
- [[_COMMUNITY_Module Group 41 (plan)|Module Group 41 (plan)]]
- [[_COMMUNITY_Module Group 42 (src)|Module Group 42 (src)]]
- [[_COMMUNITY_Module Group 43 (src)|Module Group 43 (src)]]
- [[_COMMUNITY_Module Group 44 (syncresult)|Module Group 44 (syncresult)]]
- [[_COMMUNITY_Module Group 45 (uncontrolledentry)|Module Group 45 (uncontrolledentry)]]
- [[_COMMUNITY_Module Group 46 (atriskentry)|Module Group 46 (atriskentry)]]
- [[_COMMUNITY_Module Group 47 (heatmappoint)|Module Group 47 (heatmappoint)]]
- [[_COMMUNITY_Module Group 48 (toprestaurantentry)|Module Group 48 (toprestaurantentry)]]
- [[_COMMUNITY_Module Group 49 (authrequest)|Module Group 49 (authrequest)]]
- [[_COMMUNITY_Module Group 50 (validationutil)|Module Group 50 (validationutil)]]
- [[_COMMUNITY_Module Group 51 (status)|Module Group 51 (status)]]
- [[_COMMUNITY_Module Group 52 (boroughcuisinescore)|Module Group 52 (boroughcuisinescore)]]
- [[_COMMUNITY_Module Group 53 (cuisinescore)|Module Group 53 (cuisinescore)]]
- [[_COMMUNITY_Module Group 54 (aggregationcount)|Module Group 54 (aggregationcount)]]
- [[_COMMUNITY_Module Group 55 (config)|Module Group 55 (config)]]
- [[_COMMUNITY_Module Group 56 (mongoclientfactory)|Module Group 56 (mongoclientfactory)]]
- [[_COMMUNITY_Module Group 57 (claudemd)|Module Group 57 (claudemd)]]
- [[_COMMUNITY_Module Group 58 (rationale)|Module Group 58 (rationale)]]
- [[_COMMUNITY_Module Group 59 (changelog)|Module Group 59 (changelog)]]
- [[_COMMUNITY_Module Group 60 (changelog)|Module Group 60 (changelog)]]
- [[_COMMUNITY_Module Group 61 (docs)|Module Group 61 (docs)]]
- [[_COMMUNITY_Module Group 62 (docs)|Module Group 62 (docs)]]
- [[_COMMUNITY_Module Group 63 (docs)|Module Group 63 (docs)]]
- [[_COMMUNITY_Module Group 64 (docs)|Module Group 64 (docs)]]
- [[_COMMUNITY_Module Group 65 (docs)|Module Group 65 (docs)]]
- [[_COMMUNITY_Module Group 66 (docs)|Module Group 66 (docs)]]
- [[_COMMUNITY_Module Group 67 (docs)|Module Group 67 (docs)]]
- [[_COMMUNITY_Module Group 68 (plan)|Module Group 68 (plan)]]
- [[_COMMUNITY_Module Group 69 (plan)|Module Group 69 (plan)]]
- [[_COMMUNITY_Module Group 70 (plan)|Module Group 70 (plan)]]

## God Nodes (most connected - your core abstractions)
1. `NycApiRestaurantDto` - 38 edges
2. `AppConfig` - 30 edges
3. `RestaurantService` - 29 edges
4. `Restaurant` - 27 edges
5. `RestaurantServiceTest` - 25 edges
6. `AuthServiceTest` - 25 edges
7. `RestaurantDAOImpl` - 25 edges
8. `InspectionReportEntity` - 22 edges
9. `RestaurantDAOIT` - 21 edges
10. `RestaurantController` - 21 edges
11. `RestaurantDAO` - 20 edges
12. `ReportControllerTest` - 19 edges
13. `InspectionRecord` - 19 edges
14. `Restaurant Domain Model` - 19 edges
15. `RestaurantService` - 19 edges

## Surprising Connections (you probably didn't know these)
- `Rationale: Cache Warm-Up Cron (cold cache after nightly sync)` --rationale_for--> `CronScheduler Class`  [INFERRED]
  docs/superpowers/specs/2026-05-06-ci-cron-design.md → src/main/java/com/st4r4x/sync/CronScheduler.java
- `API: GET /api/restaurants/autocomplete` --references--> `RestaurantController REST Controller`  [INFERRED]
  docs/api.md → src/main/java/com/st4r4x/controller/RestaurantController.java
- `Changelog Phase 21 — Java 25 + Spring Boot 4 Upgrade` --conceptually_related_to--> `AppConfig`  [INFERRED]
  CHANGELOG.md → src/main/java/com/st4r4x/config/AppConfig.java
- `API: GET /api/restaurants/autocomplete` --references--> `ElasticsearchSyncService`  [INFERRED]
  docs/api.md → src/main/java/com/st4r4x/sync/ElasticsearchSyncService.java
- `Rationale: ES as Read-Only Replica (MongoDB = source of truth)` --rationale_for--> `ElasticsearchSyncService`  [INFERRED]
  docs/superpowers/specs/2026-05-05-search-navbar-osm-design.md → src/main/java/com/st4r4x/sync/ElasticsearchSyncService.java

## Hyperedges (group relationships)
- **AppConfig Reflection Patch Pattern (Java 25 test workaround)** — jwtutiltest_class, reportcontrollertest_class, restaurantdaoit_class, userrepositoryit_class, appconfig_class [INFERRED 0.85]
- **Controller Test Suite (standaloneSetup pattern)** — adminControllerTest_test, analyticsControllerTest_test, restaurantControllerSearchTest_test, reportControllerTest_test, standaloneSetup_pattern [EXTRACTED 1.00]
- **ViewController Method Tests** — viewcontrolleruncontrolledtest_class, viewcontrollerdashboardtest_class, viewcontrollerprofiletest_class, viewcontrolleranalyticstest_class, viewcontroller_class [EXTRACTED 1.00]
- **Authentication Flow** — authservice_authservice, userrepository_userrepository, jwtservice_jwtservice, userentity_userentity, registerrequest_registerrequest, jwtresponse_jwtresponse, refreshrequest_refreshrequest [EXTRACTED 1.00]
- **JWT Security Chain** — jwtservice_JwtService, jwtutil_JwtUtil, jwtauthenticationfilter_JwtAuthenticationFilter, appconfig_AppConfig [EXTRACTED 1.00]
- **PostgreSQL JPA Entity Layer** — userentity_UserEntity, bookmarkentity_BookmarkEntity, inspectionreportentity_InspectionReportEntity, grade_Grade, status_Status [EXTRACTED 1.00]
- **PostgreSQL Spring JPA Repositories** — userrepository_UserRepository, bookmarkrepository_BookmarkRepository, reportrepository_ReportRepository [EXTRACTED 1.00]
- **User Bookmark Management Flow** — usercontroller_UserController, bookmarkrepository_BookmarkRepository, bookmarkentity_BookmarkEntity, userentity_UserEntity, restaurantdao_RestaurantDAO [EXTRACTED 1.00]
- **Spring Security Stack** — config_SecurityConfig, config_MethodSecurityConfig, service_AuthService, authcontroller_AuthController [INFERRED 0.85]
- **Frontend Redesign System (Clean Civic Design System + frontend_redesign_spec + frontend_redesign_plan)** — clean_civic_design_system, frontend_redesign_spec, frontend_redesign_plan [EXTRACTED 1.00]
- **Grade Rename Refactor (InspectionRecord + LetterGrade + RestaurantService + RestaurantCacheService)** — inspectionrecord_InspectionRecord, lettergrade_LetterGrade, restaurantservice_RestaurantService, restaurantcacheservice_RestaurantCacheService [EXTRACTED 1.00]
- **AnalyticsDAO Split Refactor (AnalyticsDAO + RestaurantDAO + AnalyticsController + RestaurantController + RestaurantService)** — analyticsdao_AnalyticsDAO, restaurantdao_RestaurantDAO, analyticscontroller_AnalyticsController, restaurantcontroller_RestaurantController, restaurantservice_RestaurantService [EXTRACTED 1.00]
- **NYC Data Sync Pipeline: NycOpenDataClient to SyncService to RestaurantWriteDAO plus ElasticsearchSyncService plus OsmEnrichmentService** — sync_NycOpenDataClient, sync_SyncService, dao_RestaurantWriteDAO, sync_ElasticsearchSyncService, sync_OsmEnrichmentService [EXTRACTED 0.95]
- **CronScheduler tracks JobStatus for cache-warmup, osm-reenrichment, es-reindex jobs** — sync_CronScheduler, sync_JobStatus, cache_RestaurantCacheService, sync_OsmEnrichmentService, sync_ElasticsearchSyncService [EXTRACTED 0.95]
- **ReportController enforces per-user isolation using UserRepository, ReportRepository, and Spring Security context** — controller_ReportController, repository_ReportRepository, repository_UserRepository, entity_UserEntity, entity_InspectionReportEntity [EXTRACTED 0.90]
- **Nightly Data Sync Pipeline: NYC API to MongoDB to Cache to ES to OSM** — syncservice_class, nycapiclient_concept, restaurantwritedao_interface, restaurantcacheservice_class, elasticsearchsyncservice_class, osmenrichmentservice_class [EXTRACTED 0.95]
- **Scheduled Background Jobs: Cache Warmup, OSM Re-Enrichment, ES Reindex** — cronscheduler_class, restaurantcacheservice_class, osmenrichmentservice_class, elasticsearchsyncservice_class, jobstatus_class [EXTRACTED 0.95]
- **Admin-Only Operations: Sync, OSM Enrich, Cron Status, Reports Stats** — admincontroller_class, restaurantcontroller_class, osmenrichmentservice_class, cronscheduler_class [EXTRACTED 0.90]
- **Elasticsearch Sync Pipeline (SyncService → ElasticsearchSyncService → ES Index)** — syncservice_class, elasticsearchsynservice_class, elasticsearchclient_bean, restaurantdao_interface [EXTRACTED 1.00]
- **CronScheduler Job Registry (CronScheduler + JobStatus + RestaurantCacheService + OsmEnrichmentService + ElasticsearchSyncService)** — cronscheduler_class, jobstatus_class, restaurantcacheservice_class, osmenrichmentservice_class, elasticsearchsynservice_class [EXTRACTED 1.00]
- **OSM Enrichment Pipeline (SyncService → OsmEnrichmentService → Overpass API → Restaurant MongoDB)** — syncservice_class, osmenrichmentservice_class, overpass_api, restaurant_domain [EXTRACTED 1.00]

## Communities

### Community 0 - "Address Domain Model"
Cohesion: 0.03
Nodes (7): Address, InspectionRecord, NycApiRestaurantDto, RestaurantService, RestaurantServiceTest, SyncService, SyncServiceTest

### Community 1 - "Application Bootstrap & Auth DTOs"
Cohesion: 0.04
Nodes (16): Application, AuthRequest, AuthService, AuthServiceTest, DataSeeder, DataSeederTest, JwtAuthenticationFilter, JwtResponse (+8 more)

### Community 2 - "Controller & Aggregation Classes"
Cohesion: 0.03
Nodes (104): Address Domain POJO, AdminController REST Controller, AggregationCount, AnalyticsController REST Controller, AnalyticsDAO Interface, AppConfig, AppConfig Dotenv Integration, AppConfig 4-Tier Property Resolution (+96 more)

### Community 3 - "AggregationCount POJO"
Cohesion: 0.03
Nodes (12): AggregationCount, AggregationPojoTest, AnalyticsController, AnalyticsControllerTest, AnalyticsDAO, AtRiskEntry, BoroughCuisineScore, CuisineScore (+4 more)

### Community 4 - "AppConfig & AnalyticsDAO Implementation"
Cohesion: 0.04
Nodes (11): AppConfig, AppConfigTest, ElasticsearchConfig, ElasticsearchConfigTest, MongoClientFactory, MongoClientFactoryTest, NycOpenDataClient, RateLimitFilter (+3 more)

### Community 5 - "Restaurant REST Controller"
Cohesion: 0.03
Nodes (6): RestaurantController, RestaurantControllerSampleTest, RestaurantDAO, RestaurantDAOIT, ValidationUtil, ValidationUtilTest

### Community 6 - "Bookmark Entity & JPA Layer"
Cohesion: 0.06
Nodes (6): BookmarkEntity, BookmarkRepository, InspectionReportEntity, ReportController, ReportControllerTest, UserController

### Community 7 - "Admin Cron Endpoint & Tests"
Cohesion: 0.04
Nodes (10): AdminController, AdminCronStatusTest, CronScheduler, CronSchedulerTest, JobStatus, JobStatusTest, RateLimitFilterTest, RestaurantCacheService (+2 more)

### Community 8 - "Service / Cache Semantic Layer"
Cohesion: 0.07
Nodes (58): AggregationCount (aggregation result POJO), BoroughCuisineScore (aggregation result POJO), CuisineScore (aggregation result POJO), RestaurantCacheService (Redis cache), Jackson 2 Explicit Bean for Boot 4, MongoDB Singleton Client, AppConfig (application config reader), MongoClientFactory (+50 more)

### Community 9 - "Elasticsearch Sync Service"
Cohesion: 0.09
Nodes (6): ElasticsearchSyncService, EsRestaurantDoc, ElasticsearchSyncServiceTest, NycOpenDataClientTest, RestaurantControllerAutocompleteTest, ViewControllerDashboardTest

### Community 10 - "Restaurant Domain Model"
Cohesion: 0.1
Nodes (2): Restaurant, RestaurantOsmFieldsTest

### Community 11 - "Security Config & JWT Filter"
Cohesion: 0.07
Nodes (3): SecurityConfig, SecurityConfigTest, StubReportsController

### Community 12 - "Admin OSM Enrichment Endpoint"
Cohesion: 0.11
Nodes (4): AdminOsmEnrichTest, OsmEnrichmentService, OsmResult, OsmEnrichmentServiceTest

### Community 13 - "Restaurant DAO Implementation"
Cohesion: 0.14
Nodes (1): RestaurantDAOImpl

### Community 14 - "ViewController & Frontend Tests"
Cohesion: 0.1
Nodes (4): ViewController, ViewControllerAnalyticsTest, ViewControllerProfileTest, ViewControllerUncontrolledTest

### Community 15 - "Admin Stats Endpoint & Tests"
Cohesion: 0.23
Nodes (2): AdminControllerTest, ReportRepository

### Community 16 - "AnalyticsDAO Implementation"
Cohesion: 0.27
Nodes (1): AnalyticsDAOImpl

### Community 17 - "ReportRequest DTO"
Cohesion: 0.17
Nodes (1): ReportRequest

### Community 18 - "Map Points & Search API"
Cohesion: 0.22
Nodes (1): RestaurantControllerSearchTest

### Community 19 - "TopRestaurantEntry DTO"
Cohesion: 0.18
Nodes (1): TopRestaurantEntry

### Community 20 - "Auth Controller Concepts"
Cohesion: 0.2
Nodes (10): AuthController, Admin Signup Code Role Assignment, JWT Filter Double Registration Fix, JWT Role-Based Access Control, MethodSecurity Split Config Rationale, Stateless Session (JWT), MethodSecurityConfig, SecurityConfig (+2 more)

### Community 21 - "HeatmapPoint DTO"
Cohesion: 0.22
Nodes (1): HeatmapPoint

### Community 22 - "JWT Utilities"
Cohesion: 0.25
Nodes (9): BookmarkRepository (JPA), JWT Authentication Filter, JwtService Interface, JwtUtil (JwtService implementation), ReportRepository, UserController, UserEntity (JPA), UserRepository (JPA) (+1 more)

### Community 23 - "JWT Service"
Cohesion: 0.25
Nodes (1): JwtService

### Community 24 - "RestaurantDAO Unit Tests"
Cohesion: 0.29
Nodes (1): RestaurantDAOImplTest

### Community 25 - "Admin Controller Test Classes"
Cohesion: 0.43
Nodes (7): AdminControllerTest, AdminController, LetterGrade Enum, ReportRepository JPA Repository, ReportRequest DTO, Standalone MockMvc Test Pattern, Status Enum

### Community 26 - "Grade Rename Refactor Plan"
Cohesion: 0.52
Nodes (7): AnalyticsDAO Single Responsibility Problem, Grade Name Collision Problem, Grade Rename + AnalyticsDAO Split Implementation Plan, Grade Rename + AnalyticsDAO Split Design Spec, InspectionRecord (domain POJO), LetterGrade (entity enum), Rationale: InspectionRecord rename

### Community 27 - "Auth Controller"
Cohesion: 0.53
Nodes (1): AuthController

### Community 28 - "ViewController Test Classes"
Cohesion: 0.4
Nodes (5): ViewController, ViewControllerAnalyticsTest, ViewControllerDashboardTest, ViewControllerProfileTest, ViewControllerUncontrolledTest

### Community 29 - "RefreshRequest DTO"
Cohesion: 0.5
Nodes (1): RefreshRequest

### Community 30 - "ResponseUtil Helper"
Cohesion: 0.5
Nodes (1): ResponseUtil

### Community 31 - "RestaurantWriteDAO"
Cohesion: 0.67
Nodes (1): RestaurantWriteDAO

### Community 32 - "OpenAPI Config"
Cohesion: 0.67
Nodes (1): OpenApiConfig

### Community 33 - "NYC Open Data Client"
Cohesion: 0.67
Nodes (3): NycApiRestaurantDto, NycOpenDataClient, NycOpenDataClientTest

### Community 34 - "Frontend Redesign Plan"
Cohesion: 1.0
Nodes (3): Clean Civic Design System, Frontend Redesign Implementation Plan, Frontend Redesign Design Spec

### Community 35 - "Module Group 35 (docs)"
Cohesion: 0.67
Nodes (3): Commercialisation: Technical Hardening Must-Fix, Deployment: Production Notes (DataSeeder, cron schedule), Rationale: Gate DataSeeder behind @Profile(dev)

### Community 36 - "Module Group 36 (plan)"
Cohesion: 0.67
Nodes (3): Plan: Parallelize CI YAML (unit-test || integration-test), Rationale: CI Parallelization (reduce wall-clock time ~4 min), Spec: CI Sequential Jobs Problem (Rationale)

### Community 37 - "Module Group 37 (methodsecurityconfig)"
Cohesion: 1.0
Nodes (1): MethodSecurityConfig

### Community 38 - "Module Group 38 (config)"
Cohesion: 1.0
Nodes (2): ElasticsearchConfig (ES client factory), ElasticsearchConfig Unit Test

### Community 39 - "Module Group 39 (docs)"
Cohesion: 1.0
Nodes (2): UI: Clean Civic Design System, UI: Thymeleaf Templates List

### Community 40 - "Module Group 40 (plan)"
Cohesion: 1.0
Nodes (2): Plan: CI Optimization + Cron System Implementation, Spec: CI Optimization + Cron System Design

### Community 41 - "Module Group 41 (plan)"
Cohesion: 1.0
Nodes (2): Plan: OSM Name Matching via Levenshtein Distance, Spec: OSM Name Matching Strategy

### Community 42 - "Module Group 42 (src)"
Cohesion: 1.0
Nodes (0): 

### Community 43 - "Module Group 43 (src)"
Cohesion: 1.0
Nodes (0): 

### Community 44 - "Module Group 44 (syncresult)"
Cohesion: 1.0
Nodes (1): SyncResult

### Community 45 - "Module Group 45 (uncontrolledentry)"
Cohesion: 1.0
Nodes (1): UncontrolledEntry DTO

### Community 46 - "Module Group 46 (atriskentry)"
Cohesion: 1.0
Nodes (1): AtRiskEntry DTO

### Community 47 - "Module Group 47 (heatmappoint)"
Cohesion: 1.0
Nodes (1): HeatmapPoint DTO

### Community 48 - "Module Group 48 (toprestaurantentry)"
Cohesion: 1.0
Nodes (1): TopRestaurantEntry DTO

### Community 49 - "Module Group 49 (authrequest)"
Cohesion: 1.0
Nodes (1): AuthRequest DTO

### Community 50 - "Module Group 50 (validationutil)"
Cohesion: 1.0
Nodes (1): ValidationUtil

### Community 51 - "Module Group 51 (status)"
Cohesion: 1.0
Nodes (1): Status (entity enum)

### Community 52 - "Module Group 52 (boroughcuisinescore)"
Cohesion: 1.0
Nodes (1): BoroughCuisineScore

### Community 53 - "Module Group 53 (cuisinescore)"
Cohesion: 1.0
Nodes (1): CuisineScore

### Community 54 - "Module Group 54 (aggregationcount)"
Cohesion: 1.0
Nodes (1): AggregationCount

### Community 55 - "Module Group 55 (config)"
Cohesion: 1.0
Nodes (1): OpenApiConfig

### Community 56 - "Module Group 56 (mongoclientfactory)"
Cohesion: 1.0
Nodes (1): MongoClientFactory

### Community 57 - "Module Group 57 (claudemd)"
Cohesion: 1.0
Nodes (1): Restaurant Analytics CLAUDE.md

### Community 58 - "Module Group 58 (rationale)"
Cohesion: 1.0
Nodes (1): Rationale: AnalyticsDAO split from RestaurantDAOImpl

### Community 59 - "Module Group 59 (changelog)"
Cohesion: 1.0
Nodes (1): CHANGELOG

### Community 60 - "Module Group 60 (changelog)"
Cohesion: 1.0
Nodes (1): Release v2.0.0 — Frontend Redesign + CI/CD

### Community 61 - "Module Group 61 (docs)"
Cohesion: 1.0
Nodes (1): API: Authentication Endpoints

### Community 62 - "Module Group 62 (docs)"
Cohesion: 1.0
Nodes (1): API: Analytics Endpoints

### Community 63 - "Module Group 63 (docs)"
Cohesion: 1.0
Nodes (1): Commercialisation: Freemium SaaS Tiers

### Community 64 - "Module Group 64 (docs)"
Cohesion: 1.0
Nodes (1): Commercialisation: Infrastructure Scaling Path

### Community 65 - "Module Group 65 (docs)"
Cohesion: 1.0
Nodes (1): UI: Pages Table (URL → Auth → Description)

### Community 66 - "Module Group 66 (docs)"
Cohesion: 1.0
Nodes (1): Development: Seeded Test Accounts

### Community 67 - "Module Group 67 (docs)"
Cohesion: 1.0
Nodes (1): Development: Test Layout (Unit + Integration)

### Community 68 - "Module Group 68 (plan)"
Cohesion: 1.0
Nodes (1): Plan: Autocomplete Dropdown Frontend (debounced JS)

### Community 69 - "Module Group 69 (plan)"
Cohesion: 1.0
Nodes (1): Plan: JobStatus POJO Implementation

### Community 70 - "Module Group 70 (plan)"
Cohesion: 1.0
Nodes (1): Plan: CronScheduler Implementation

## Ambiguous Edges - Review These
- `UserRepositoryIT (Integration Test)` → `RestaurantCacheService Redis Cache`  [AMBIGUOUS]
  src/test/java/com/st4r4x/repository/UserRepositoryIT.java · relation: conceptually_related_to

## Knowledge Gaps
- **108 isolated node(s):** `MethodSecurityConfig`, `RestaurantDAOImplTest`, `RestaurantTest (POJO Unit Test)`, `ValidationUtilTest`, `Testcontainers PostgreSQLContainer` (+103 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Module Group 37 (methodsecurityconfig)`** (2 nodes): `MethodSecurityConfig`, `MethodSecurityConfig.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 38 (config)`** (2 nodes): `ElasticsearchConfig (ES client factory)`, `ElasticsearchConfig Unit Test`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 39 (docs)`** (2 nodes): `UI: Clean Civic Design System`, `UI: Thymeleaf Templates List`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 40 (plan)`** (2 nodes): `Plan: CI Optimization + Cron System Implementation`, `Spec: CI Optimization + Cron System Design`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 41 (plan)`** (2 nodes): `Plan: OSM Name Matching via Levenshtein Distance`, `Spec: OSM Name Matching Strategy`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 42 (src)`** (1 nodes): `LetterGrade.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 43 (src)`** (1 nodes): `Status.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 44 (syncresult)`** (1 nodes): `SyncResult`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 45 (uncontrolledentry)`** (1 nodes): `UncontrolledEntry DTO`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 46 (atriskentry)`** (1 nodes): `AtRiskEntry DTO`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 47 (heatmappoint)`** (1 nodes): `HeatmapPoint DTO`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 48 (toprestaurantentry)`** (1 nodes): `TopRestaurantEntry DTO`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 49 (authrequest)`** (1 nodes): `AuthRequest DTO`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 50 (validationutil)`** (1 nodes): `ValidationUtil`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 51 (status)`** (1 nodes): `Status (entity enum)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 52 (boroughcuisinescore)`** (1 nodes): `BoroughCuisineScore`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 53 (cuisinescore)`** (1 nodes): `CuisineScore`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 54 (aggregationcount)`** (1 nodes): `AggregationCount`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 55 (config)`** (1 nodes): `OpenApiConfig`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 56 (mongoclientfactory)`** (1 nodes): `MongoClientFactory`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 57 (claudemd)`** (1 nodes): `Restaurant Analytics CLAUDE.md`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 58 (rationale)`** (1 nodes): `Rationale: AnalyticsDAO split from RestaurantDAOImpl`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 59 (changelog)`** (1 nodes): `CHANGELOG`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 60 (changelog)`** (1 nodes): `Release v2.0.0 — Frontend Redesign + CI/CD`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 61 (docs)`** (1 nodes): `API: Authentication Endpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 62 (docs)`** (1 nodes): `API: Analytics Endpoints`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 63 (docs)`** (1 nodes): `Commercialisation: Freemium SaaS Tiers`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 64 (docs)`** (1 nodes): `Commercialisation: Infrastructure Scaling Path`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 65 (docs)`** (1 nodes): `UI: Pages Table (URL → Auth → Description)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 66 (docs)`** (1 nodes): `Development: Seeded Test Accounts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 67 (docs)`** (1 nodes): `Development: Test Layout (Unit + Integration)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 68 (plan)`** (1 nodes): `Plan: Autocomplete Dropdown Frontend (debounced JS)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 69 (plan)`** (1 nodes): `Plan: JobStatus POJO Implementation`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Module Group 70 (plan)`** (1 nodes): `Plan: CronScheduler Implementation`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `UserRepositoryIT (Integration Test)` and `RestaurantCacheService Redis Cache`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `RestaurantService` connect `Address Domain Model` to `AggregationCount POJO`, `Restaurant REST Controller`, `Bookmark Entity & JPA Layer`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Why does `RestaurantDAOImpl` connect `Restaurant DAO Implementation` to `Address Domain Model`, `AppConfig & AnalyticsDAO Implementation`, `Restaurant REST Controller`?**
  _High betweenness centrality (0.042) - this node is a cross-community bridge._
- **Why does `Restaurant` connect `Restaurant Domain Model` to `Address Domain Model`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **What connects `MethodSecurityConfig`, `RestaurantDAOImplTest`, `RestaurantTest (POJO Unit Test)` to the rest of the system?**
  _108 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Address Domain Model` be split into smaller, more focused modules?**
  _Cohesion score 0.03 - nodes in this community are weakly interconnected._
- **Should `Application Bootstrap & Auth DTOs` be split into smaller, more focused modules?**
  _Cohesion score 0.04 - nodes in this community are weakly interconnected._