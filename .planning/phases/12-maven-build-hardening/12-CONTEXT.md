# Phase 12: Maven Build Hardening - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire JaCoCo coverage report + Failsafe plugin into `pom.xml` so that:
- `mvn test` generates a JaCoCo HTML report at `target/site/jacoco/index.html`
- `mvn test` fails when instruction coverage drops below a threshold (measured at implementation time)
- All existing Mockito-instrumented tests continue to pass via the `@{argLine}` late-binding fix
- Failsafe infrastructure is in place (correct argLine) ready for Phase 14 Testcontainers migration

This phase does NOT migrate any tests to Failsafe — that is Phase 14.
This phase does NOT touch application code, only `pom.xml`.

**Note:** Spring Boot was upgraded to 3.4.4 + Java 25 immediately before this phase. The "27 existing test files" baseline from the roadmap may differ — verify actual test count after upgrade before setting threshold.

</domain>

<decisions>
## Implementation Decisions

### Coverage Threshold
- **D-01:** Measure the actual baseline first by running `mvn test jacoco:report` with no check goal, read `target/site/jacoco/index.html` instruction coverage %, then set threshold at `max(baseline − 5%, baseline)` if baseline ≥ 60%, or `baseline − 5%` if below 60%.
- **D-02:** Threshold must be documented in `pom.xml` with a comment: "Measured baseline: X% — threshold set at Y% (baseline minus 5%)". Never set an aspirational target not yet met.
- **D-03:** Threshold enforced on `INSTRUCTION` counter (not line or branch) — most stable metric across refactors.

### JaCoCo Exclusions
- **D-04:** Exclude the following packages from measurement (pure data carriers, no meaningful logic):
  - `com/st4r4x/dto/**`
  - `com/st4r4x/entity/**`
  - `com/st4r4x/aggregation/**`
  - `com/st4r4x/domain/**` (MongoDB POJOs — getters/setters only)
- **D-05:** Everything else is measured: `service/**`, `dao/**`, `controller/**`, `security/**`, `cache/**`, `sync/**`, `util/**`, `config/**`.

### Failsafe Plugin
- **D-06:** Add `maven-failsafe-plugin` with `@{argLine} -XX:+EnableDynamicAgentLoading` as argLine — same late-binding pattern as Surefire.
- **D-07:** Do NOT rename or move any tests in this phase. `RestaurantDAOIntegrationTest` stays under Surefire until Phase 14 migrates it to Testcontainers.
- **D-08:** Failsafe bound to `integration-test` and `verify` lifecycle phases — no tests will run under it yet since there are no `*IT.java` files.

### argLine Late-Binding Fix (Critical)
- **D-09:** Surefire argLine MUST use `@{argLine}` (with `@`, not `$`) to pick up the JaCoCo agent string set by `prepare-agent` goal. Current pom.xml has literal `-XX:+EnableDynamicAgentLoading` — this MUST become `@{argLine} -XX:+EnableDynamicAgentLoading`.
- **D-10:** JaCoCo `prepare-agent` goal must run in `initialize` phase (before `test-compile`) so `${argLine}` property is available when Surefire reads it.

### Claude's Discretion
- JaCoCo plugin version: use latest stable compatible with Spring Boot 3.4.4 BOM (or explicit recent version if not managed).
- Whether to add a separate `jacoco` Maven profile or wire goals directly into the default lifecycle — Claude decides based on simplicity.
- HTML report destination: default `target/site/jacoco/index.html` as specified in roadmap success criteria.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Testing — TEST-07 (JaCoCo report), TEST-08 (threshold enforcement)

### Phase Goal
- `.planning/ROADMAP.md` §Phase 12 — success criteria (4 items)

### Current pom.xml State
- `pom.xml` — Spring Boot 3.4.4, Java 25, maven-compiler-plugin 3.14.0, current Surefire argLine config (must be read before editing)

### Prior Phase Context
- `.planning/STATE.md` §Accumulated Context — argLine late-binding decision, JaCoCo baseline measurement note

No external specs — requirements fully captured in decisions above.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `pom.xml` — current Surefire plugin has `<argLine>-XX:+EnableDynamicAgentLoading</argLine>` (must be updated to `@{argLine} -XX:+EnableDynamicAgentLoading`)
- `pom.xml` — maven-compiler-plugin 3.14.0, Spring Boot 3.4.4 parent (BOM may manage jacoco-maven-plugin version)

### Established Patterns
- All plugin config lives in `<build><plugins>` — no profiles in use currently
- Surefire plugin already explicitly configured — extend it, don't duplicate

### Integration Points
- Surefire argLine: the only change to an existing plugin config
- All other additions are new plugin entries in `<build><plugins>`
- No application source changes required

</code_context>

<specifics>
## Specific Ideas

- Measure baseline first before writing the check goal — this is a hard requirement, not optional
- The threshold comment in pom.xml is required by ROADMAP success criterion #4
- Spring Boot 3.4.4 upgrade (just completed) may affect test pass rate — verify all tests green before setting threshold

</specifics>

<deferred>
## Deferred Ideas

- Moving RestaurantDAOIntegrationTest to Failsafe → Phase 14 (Testcontainers)
- Publishing JaCoCo report as PR comment → Phase 15 (CI-08)
- Branch coverage threshold → deferred until coverage improves in Phase 19

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 12-maven-build-hardening*
*Context gathered: 2026-04-12*
