# Phase 14: Testcontainers Integration Tests - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-12
**Phase:** 14-testcontainers-integration-tests
**Areas discussed:** MongoDB test data seeding, PostgreSQL coverage scope, Failsafe test naming

---

## MongoDB Test Data Seeding

| Option | Description | Selected |
|--------|-------------|----------|
| Programmatic insert in @BeforeClass | Insert ~50 hand-crafted documents via MongoClient in @BeforeClass. Fast, self-contained, no external files. | ✓ |
| Load a fixture JSON file | Ship a restaurants-fixture.json under src/test/resources and bulk-insert. More realistic volume but adds a file to maintain. | |
| Small NYC API sync | Run SyncService with max_records=200 against real NYC Open Data API. Requires internet in CI. | |

**User's choice:** Programmatic insert in @BeforeClass

---

## MongoDB Assertion Threshold

| Option | Description | Selected |
|--------|-------------|----------|
| Adapt assertions to seeded data | Seed ~50 documents and change minCount threshold to match (e.g., findCuisinesWithMinimumCount(10)). | ✓ |
| Seed 500+ documents | Insert 500+ Italian restaurant docs to preserve exact assertion threshold. More setup work. | |

**User's choice:** Adapt assertions to seeded data

---

## PostgreSQL Coverage Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Write actual JPA integration tests | Add UserRepositoryIT.java covering UserRepository + BookmarkRepository save/retrieve. | ✓ |
| Container available, no new PG tests | Spin up PG container for Spring context but no dedicated test methods. | |

**User's choice:** Write actual JPA integration tests

---

## PostgreSQL Test Entities

| Option | Description | Selected |
|--------|-------------|----------|
| Both User + Bookmark repositories | Cover UserRepository and BookmarkRepository — both JPA repos that exist. | ✓ |
| User only | Only UserRepository.save() + findByUsername(). Deferred Bookmark to Phase 19. | |

**User's choice:** Both User + Bookmark repositories

---

## Failsafe Test Naming

| Option | Description | Selected |
|--------|-------------|----------|
| Rename to *IT.java — run under Failsafe | RestaurantDAOIntegrationTest → RestaurantDAOIT.java, UserRepositoryIT.java. Runs via mvn failsafe:integration-test, not during mvn test. | ✓ |
| Keep *Test.java — run under Surefire | Stays in mvn test lifecycle. Mixes fast unit tests with slow container tests. | |

**User's choice:** Rename to *IT.java — run under Failsafe

---

## Claude's Discretion

- Exact number of seeded documents (Claude decides based on DAO query requirements)
- Helper method vs. inner TestDataFactory class for seeding
- Testcontainers MongoDB/PostgreSQL image versions (mongo:7.0, postgres:15-alpine to match production)

## Deferred Ideas

- Full service-layer IT tests → Phase 19
- Redis IT test → Phase 19 or standalone
- Seeding from a BSON dump → out of scope
