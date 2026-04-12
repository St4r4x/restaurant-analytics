# Phase 12: Maven Build Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-12
**Phase:** 12-maven-build-hardening
**Areas discussed:** Coverage Threshold, JaCoCo Exclusions, Failsafe Scope

---

## Coverage Threshold

| Option | Description | Selected |
|--------|-------------|----------|
| Measure baseline first | Run mvn jacoco:report, set threshold at baseline − 5% | ✓ |
| Fixed 60% | Set 60% unconditionally | |
| Report only, no threshold | Generate HTML report but don't enforce minimum | |

**User's choice:** Measure baseline first (Recommended)
**Notes:** Threshold set after measuring actual baseline — never aspirational.

---

## JaCoCo Exclusions

| Option | Description | Selected |
|--------|-------------|----------|
| DTOs + entities only | Exclude dto/**, entity/**, aggregation/**, domain/** | ✓ |
| Measure everything | No exclusions | |
| Exclude generated + config too | Also exclude config/**, Application.java | |

**User's choice:** DTOs + entities only (Recommended)
**Notes:** Clean signal — exclude pure data carriers, measure all logic layers.

---

## Failsafe Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Wire plugin only | Add failsafe with correct argLine, no test migration | ✓ |
| Move integration tests now | Rename *IT.java and run under Failsafe now | |

**User's choice:** Wire plugin only (Recommended)
**Notes:** Test migration deferred to Phase 14 (Testcontainers).

---

## Claude's Discretion

- JaCoCo plugin version selection
- Whether to use a Maven profile or default lifecycle wiring
- HTML report destination (default target/site/jacoco/)

## Deferred Ideas

- RestaurantDAOIntegrationTest → Failsafe: deferred to Phase 14
- PR coverage comment: deferred to Phase 15 (CI-08)
- Branch coverage threshold: deferred to Phase 19
