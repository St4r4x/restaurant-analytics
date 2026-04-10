---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: — Full Product
status: executing
stopped_at: Completed 10-01-PLAN.md
last_updated: "2026-04-10T15:26:24.919Z"
last_activity: 2026-04-09 -- Phase 10 execution started
progress:
  total_phases: 10
  completed_phases: 9
  total_plans: 37
  completed_plans: 36
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-27)

**Core value:** A customer can search any NYC restaurant and immediately know whether it's clean — and a controller can document new hygiene findings against the same data.
**Current focus:** Phase 10 — admin-tools

## Current Position

Phase: 10 (admin-tools) — EXECUTING
Plan: 1 of 3
Status: Executing Phase 10
Last activity: 2026-04-09 -- Phase 10 execution started

Progress: [██████████] 100% (8/10 phases complete)

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: -
- Total execution time: -

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01-role-infrastructure P04 | 2 | 1 tasks | 2 files |
| Phase 01-role-infrastructure P01-01 | 15 | 1 tasks | 5 files |
| Phase 01-role-infrastructure P02 | 90 | 2 tasks | 4 files |
| Phase 02-controller-reports P01 | 18 | 2 tasks | 7 files |
| Phase 02-controller-reports P02 | 8 | 1 tasks | 2 files |
| Phase 02-controller-reports P03 | 20 | 2 tasks | 5 files |
| Phase 02-controller-reports P03 | 20 | 3 tasks | 5 files |
| Phase 03-customer-discovery P01 | 35 | 1 tasks | 3 files |
| Phase 03-customer-discovery P02 | 30 | 2 tasks | 5 files |
| Phase 03-customer-discovery P03 | 15 | 2 tasks | 2 files |
| Phase 03-customer-discovery P04 | 25 | 2 tasks | 2 files |
| Phase 04-integration-polish P01 | 22 | 2 tasks | 13 files |
| Phase 04-integration-polish P02 | 10 | 2 tasks | 4 files |
| Phase 04-integration-polish P03 | 125 | 2 tasks | 3 files |
| Phase 04-integration-polish P04 | 35 | 2 tasks | 1 files |
| Phase 05-controller-workspace P01 | 27 | 3 tasks | 4 files |
| Phase 05-controller-workspace P02 | 525612min | 1 tasks | 1 files |
| Phase 05-controller-workspace P02 | 12min | 1 tasks | 1 files |
| Phase 06-analytics-stats P01 | 31 | 2 tasks | 5 files |
| Phase 06-analytics-stats PP02 | 27 | 2 tasks | 5 files |
| Phase 06-analytics-stats PP03 | 15 | 1 tasks | 4 files |
| Phase 06-analytics-stats P03 | 20min | 2 tasks | 4 files |
| Phase 07-homepage-navigation PP01 | 36min | 2 tasks | 11 files |
| Phase 07-homepage-navigation P02 | 13min | 2 tasks | 4 files |
| Phase 07-homepage-navigation PP03 | 12min | 2 tasks | 5 files |
| Phase 07 P04 | 5 | 1 tasks | 1 files |
| Phase 10-admin-tools P02 | 2 | 1 tasks | 4 files |
| Phase 10 P01 | 5 | 3 tasks | 8 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: Controller reports stored in PostgreSQL (JPA), referencing MongoDB restaurants by `camis` ID only
- Roadmap: Role field added to `UserEntity` (no new table); `ROLE_CUSTOMER` / `ROLE_CONTROLLER` convention with `hasRole()`
- Roadmap: Controller signup gated by env-var registration code (Docker Compose injection)
- Roadmap: Customer UI extends existing `ViewController` + Thymeleaf templates
- Roadmap: Photos stored on Docker named volume (`uploads_data:/app/uploads`) — no S3
- [Phase 01-04]: Constructor injection chosen over @Autowired field injection for DataSeeder (testability, immutability)
- [Phase 01-04]: Idempotent seed pattern: findByUsername().isPresent() guard before every save() in ApplicationRunner
- [Phase 01-role-infrastructure]: ROLE_CUSTOMER / ROLE_CONTROLLER locked in as registration roles; ROLE_USER removed entirely
- [Phase 01-role-infrastructure]: Controller signup is fail-safe: CONTROLLER_SIGNUP_CODE env var absent means any signupCode returns HTTP 400
- [Phase 01-role-infrastructure]: Mockito upgraded to 5.x, Byte Buddy to 1.14.18 to support static mocking on Java 21+ runtime
- [Phase 01-02]: Abandoned @WebMvcTest for SecurityConfigTest on Java 25: Mockito agent attachment crashes JVM. Used JUnit 4 + AnnotationConfigWebApplicationContext + standaloneSetup instead.
- [Phase 01-02]: SecurityConfig registered before SecurityAutoConfiguration in test context to prevent duplicate FilterChain from @ConditionalOnDefaultWebSecurity.
- [Phase 01-02]: FilterRegistrationBean.setEnabled(false) prevents JwtAuthenticationFilter double-registration as servlet filter.
- [Phase 02-01]: assumeTrue(false) used instead of Assumptions.abort(String) — abort(String) added in JUnit 5.9.0, project uses 5.8.2 via Spring Boot 2.6.15 BOM
- [Phase 02-01]: Grade enum placed in com.aflokkat.entity (not com.aflokkat.domain) to avoid collision with existing com.aflokkat.domain.Grade MongoDB POJO
- [Phase 02-02]: spy() on JPA entity fails on Java 25 — use ArgumentCaptor on reportRepository.save() to verify partial-update behavior
- [Phase 02-02]: Manual 403 body map returned directly — do NOT use ResponseUtil.errorResponse() which cannot produce 403
- [Phase 02-controller-reports]: mockStatic(AppConfig.class) causes java.lang.VerifyError on Java 25; use reflection to patch AppConfig.properties static field in tests instead
- [Phase 02-controller-reports]: photoUpload test must stub userRepository.findByUsername() — controller calls getCurrentUser() before findById()
- [Phase 02-controller-reports]: mockStatic(AppConfig.class) causes VerifyError on Java 25 — use reflection to patch AppConfig.properties static field in tests instead
- [Phase 02-controller-reports]: photoUpload test must stub userRepository.findByUsername() — controller calls getCurrentUser() before findById()
- [Phase 02-controller-reports]: getPhoto() return type is ResponseEntity<Resource> not ResponseEntity<Map> — Spring MVC allows different return types per handler
- [Phase 03-customer-discovery]: Wave 0 stubs: @Disabled annotation on each test method; DAO interface stubs added in Plan 03-01 with UnsupportedOperationException impl to allow compilation before Plan 03-02 implementation
- [Phase 03-customer-discovery]: RestaurantDAO injected directly into RestaurantController for search/map-points — no service wrapper needed since there is no business logic
- [Phase 03-customer-discovery]: findMapPoints uses raw database.getCollection().aggregate() not typed aggregate() helper — return type is List<Document> not a POJO
- [Phase 03-customer-discovery]: Remove @Mock RestaurantService from search test — Mockito VerifyError on Java 25 for inline mocking; toView is static so no instance mock needed
- [Phase 03-customer-discovery]: Search card inserted between header and first .dashboard grid — append-only JS pattern, no rewrites
- [Phase 03-customer-discovery]: my-bookmarks.html is standalone HTML with no Thymeleaf th: attributes — client-side fetch-only rendering
- [Phase 03-customer-discovery]: restaurant.html and inspection-map.html are now public pages — auth guard removed from page load; authentication only triggers on bookmark button click
- [Phase 03-customer-discovery]: Leaflet.markerCluster CDN loaded after leaflet.min.js — load order is mandatory (CSS before JS, markerCluster after Leaflet)
- [Phase 03-customer-discovery]: Last cluster spiderfies on click when restaurants share same GPS coordinates — standard markerCluster behavior, not a bug
- [Phase 04-integration-polish]: Translation-only approach: no IDs, class names, Thymeleaf attributes, JS variable names, or API paths changed
- [Phase 04-integration-polish]: 3 out-of-scope Java files (Application.java, ValidationUtil.java, MongoClientFactory.java) deferred for follow-up cleanup
- [Phase 04-integration-polish]: /hygiene-radar REST endpoint retained in RestaurantController; only the Thymeleaf view route was removed from ViewController
- [Phase 04-integration-polish]: README full replacement: French placeholder had no reusable content; clean rewrite chosen over incremental edits
- [Phase 04-integration-polish]: Grade enum values corrected to actual code (A, B, C, F) vs plan spec (A, B, C, F, N, Z, P)
- [Phase 04-integration-polish]: @AfterEach SecurityContextHolder.clearContext() added to ReportControllerTest to prevent auth context leaks between tests that override the default @BeforeEach security context
- [Phase 05-controller-workspace]: Mockito mock(Authentication.class) fails on Java 25 — use UsernamePasswordAuthenticationToken concrete class instead (consistent with existing project test patterns)
- [Phase 05-controller-workspace]: antMatchers("/dashboard").hasRole("CONTROLLER") inserted immediately before anyRequest().permitAll() in SecurityConfig
- [Phase 05-controller-workspace]: uploadPhoto uses raw fetch() to preserve multipart boundary; fetchWithAuth would corrupt it with Content-Type: application/json
- [Phase 05-controller-workspace]: gradeBadgeHtml and borderColor declared at top-level scope (not IIFE) in dashboard.html so template literals can reference them
- [Phase 06-analytics-stats]: Wave 0 scaffold approach: all 5 test stubs @Disabled so mvn test exits 0 before any analytics implementation exists
- [Phase 06-analytics-stats]: Two stub methods added to RestaurantService (getWorstCuisinesByAverageScore, getBestCuisinesByAverageScore) returning empty lists to satisfy test compilation without business logic
- [Phase 06-analytics-stats]: AnalyticsController injects RestaurantDAO directly (not RestaurantService): Mockito cannot mock constructor-injected services on Java 25 — consistent with RestaurantController search/map-points pattern
- [Phase 06-analytics-stats]: BSON Document.getInteger(String, int) returns primitive int not Integer — cast to (long) not .longValue()
- [Phase 06-analytics-stats]: analytics.html uses 4 concurrent DOMContentLoaded fetches with inline CSS replicating dashboard.html patterns — no separate stylesheet
- [Phase 06-analytics-stats]: analytics.html uses inline CSS replicating dashboard.html patterns — no separate stylesheet
- [Phase 06-analytics-stats]: 4 API fetches fire concurrently on DOMContentLoaded — no sequential chaining
- [Phase 07-homepage-navigation]: restaurantDAO injected directly into RestaurantController for /sample — consistent with search/map-points pattern
- [Phase 07-homepage-navigation]: .antMatchers('/dashboard').hasRole('CONTROLLER') restored — guard was lost when SecurityConfig was edited during Phase 6
- [Phase 07-homepage-navigation]: Navbar auth state fully JS-driven: no Spring Security Thymeleaf — stateless JWT app has no server session to query
- [Phase 07-homepage-navigation]: landing.html has no auth guard: public page must not redirect anonymous visitors
- [Phase 07-homepage-navigation]: Chart.js and Leaflet CDN references removed from index.html: only needed on analytics.html and inspection-map.html
- [Phase 07-03]: inspection-map.html uses body flex-column layout so navbar integrates as first flex child above toolbar — no explicit padding-top needed
- [Phase 07]: Reused gradeBadgeHtml and renderRestaurantCards verbatim from index.html — no abstraction layer needed for two pages
- [Phase 10-02]: AdminController uses @Autowired ReportRepository directly (no service wrapper) — consistent with existing AnalyticsController pattern
- [Phase 10-02]: AuthService 5-arg constructor added (controllerSignupCode + adminSignupCode) — ADMIN check runs before CONTROLLER check to prevent code collision
- [Phase 10-02]: Pre-populate LinkedHashMap with all enum.values() at 0L before merging JPQL GROUP BY results — guarantees all enum keys always present
- [Phase 10-01]: Admin signup code checked before controller code in role-assignment — admin takes priority when both are set
- [Phase 10-01]: admin.signup.code= (empty) is default — admin accounts created via DataSeeder, not self-registration
- [Phase 10-01]: ADMIN_SIGNUP_CODE defaults to empty string in docker-compose.yml (admin signup disabled in Docker)

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 1: `anyRequest().permitAll()` is currently active in `SecurityConfig` — must be replaced with explicit `antMatchers` before any feature code is written on top
- Phase 1: `ROLE_` prefix convention must be locked in (store `ROLE_CONTROLLER` / `ROLE_CUSTOMER`, always use `hasRole()`) before Phase 2 starts
- Phase 2: Docker named volume for photo uploads must be added to `docker-compose.yml` before the first photo upload test
- Phase 3: Verify whether a MongoDB index on `name` / `address.street` already exists before implementing `$regex` search

## Session Continuity

Last session: 2026-04-10T15:26:24.913Z
Stopped at: Completed 10-01-PLAN.md
Resume file: None
