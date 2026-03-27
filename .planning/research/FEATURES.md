# Feature Landscape

**Domain:** Restaurant hygiene inspection — role-based (CONTROLLER vs CUSTOMER)
**Researched:** 2026-03-27
**Confidence:** HIGH (grounded in existing codebase + NYC Open Data domain model + known patterns for food safety/inspection apps)

---

## Existing Features (Already Built — Do Not Rebuild)

These are live in the codebase and must remain working after the milestone.

| Feature | Location | Notes |
|---------|----------|-------|
| JWT auth (register / login / refresh) | `AuthController`, `AuthService` | Single role today; needs CUSTOMER/CONTROLLER split |
| User profile + role field | `UserEntity`, `UserController /api/users/me` | `role` column already exists in `users` table |
| Restaurant bookmarks (add/remove/list) | `BookmarkEntity`, `UserController /api/users/me/bookmarks` | Customer-facing, keep as-is |
| At-risk restaurants list + CSV export | `InspectionController` (ADMIN-gated) | Repurpose for CONTROLLER role |
| Restaurant analytics (borough/cuisine/stats) | `RestaurantController` | Public read, no role change needed |
| Interactive map (`/inspection-map`) | `ViewController`, Thymeleaf template | Customer-visible; already has geo coords in `Address.coord` |
| Restaurant detail page (`/restaurant/{id}`) | `ViewController`, Thymeleaf template | Shows NYC grades; customer-facing |
| Hygiene radar (`/hygiene-radar`) | `ViewController`, Thymeleaf template | Analytics dashboard |

---

## CONTROLLER Role — Feature Landscape

### Table Stakes (CONTROLLER)

Features a controller must have or the role is unusable.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Secure controller registration via registration code | Prevents public access to filing reports; industry standard gating | Low | Add `registrationCode` field to `RegisterRequest`; validate against a configured code |
| Role-based route protection (`CONTROLLER` only) | Without this, any user can file reports | Low | Extend Spring Security `@PreAuthorize` — `role` field already in `UserEntity` |
| Create internal inspection report for a restaurant | Core job: document violations found on-site | Med | New `InspectionReportEntity` in PostgreSQL; linked to `restaurantId` (MongoDB ID) + `userId` |
| Report fields: violation list, overall score, grade, status | Minimum data a real inspection captures | Med | Map to NYC violation codes already present in `Grade.violationCode` / `Grade.violationDescription` |
| View own submitted reports (list + detail) | Controller must track their own workload | Low | `GET /api/reports/my` — filter by `userId` |
| Edit / update a submitted report | Corrections are expected; inspections get revised | Low | `PUT /api/reports/{id}` — only report owner can update |
| Report status lifecycle (`OPEN` → `PENDING` → `CLOSED`) | Follow-up tracking is core to inspection workflow | Low | Enum field on `InspectionReportEntity`; simple state machine |
| At-risk restaurant list access | Controllers need to identify which restaurants to prioritize | Low | Already exists in `InspectionController` — repoint from `ADMIN` to `CONTROLLER` role |

### Differentiators (CONTROLLER)

Features that make the controller experience noticeably better.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Photo attachment on a report | Real inspection apps always include evidence photos | High | Multipart upload; store on filesystem or blob storage; reference path in report entity — defer unless explicitly scoped |
| Pre-filled restaurant context when filing a report | Pulls NYC Open Data grade history inline so controller doesn't need to look it up separately | Low | Fetch restaurant + grades from MongoDB when creating report — join on `restaurantId` |
| Filter / search own reports by status or restaurant | When a controller has many reports, search saves time | Low | Query params on `GET /api/reports/my?status=OPEN&restaurantId=X` |
| At-risk export filtered to CONTROLLER's borough/zone | Targeted export rather than all 27k restaurants | Low | Extend existing CSV export with optional `borough` param — already supported |
| Report history per restaurant | See all internal reports filed for a given restaurant over time | Low | `GET /api/reports?restaurantId=X` (CONTROLLER only) |

### Anti-Features (CONTROLLER)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Publishing controller reports to NYC Open Data API | NYC API is read-only; no write endpoint exists | Keep reports internal (PostgreSQL only) |
| Customer-visible controller reports in v1 | Creates data quality / legal risk before validation workflow exists | Mark reports as `internal`; never surface via customer endpoints |
| Real-time push notifications when report status changes | Out of scope; significant infra overhead | Simple status field + polling if needed |
| Full CRUD on other controllers' reports | Controllers should only edit their own reports | Enforce `userId` ownership check in service layer |
| Free-text violation entry | Diverges from NYC violation code taxonomy, makes data inconsistent | Use the known `violationCode` / `violationDescription` fields from `Grade` as reference |

---

## CUSTOMER Role — Feature Landscape

### Table Stakes (CUSTOMER)

Features a customer must have or the product fails its core promise.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Open registration (no code) | Customer sign-up must be frictionless | Low | Default role = `CUSTOMER` on `POST /api/auth/register`; no code required |
| Search restaurants by name | Most common customer intent — "is [restaurant name] clean?" | Med | MongoDB text index or `$regex` on `name`; new `GET /api/restaurants/search?q=` endpoint |
| Search restaurants by address / neighborhood | Second most common intent — nearby search | Med | Filter on `address.street` or `address.zipcode`; existing `borough` filter already available |
| Restaurant detail page with hygiene grade + score | The core value proposition stated in `PROJECT.md` | Low | Page already exists (`/restaurant/{id}`, `restaurant.html`); needs grade prominently displayed |
| NYC inspection history for a restaurant | Grade alone is insufficient — customers want trend | Low | `grades` array already on `Restaurant` domain object; render as timeline |
| Violation summary (count critical vs non-critical) | Customers want to know what was wrong, not just the grade | Low | `Grade.criticalFlag` already exists; aggregate at read time |
| Interactive map view of restaurants with grade color-coding | Spatial context is expected for location-based search | Med | Map page already exists (`/inspection-map`); add grade-based marker color |
| Bookmark / saved restaurants | Customers return to check favorites | Low | Already fully built (`BookmarkEntity`, `UserController`) |

### Differentiators (CUSTOMER)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Grade trend indicator (improving / declining / stable) | Shows direction, not just current snapshot — uniquely useful | Low | Compare last 2-3 grades in `grades` array; compute trend at read time |
| "Similar restaurants nearby" hygiene comparison | Lets customer make relative choice ("this sushi place vs that one") | Med | Filter by cuisine + borough + radius; return sorted by score |
| Cuisine-type filter on map | "Show me all A-grade Italian restaurants in Brooklyn" — natural use case | Low | Extend existing `cuisine` and `borough` params; map filter UI |
| Borough-level hygiene summary | Customers curious about neighborhoods ("is Chinatown safer than Midtown?") | Low | Already exists in `GET /api/restaurants/by-borough`; surface in customer UI |
| Worst cuisines in a borough widget | Transparently informative; drives engagement | Low | Already exists in `GET /api/restaurants/worst-cuisines`; expose in customer dashboard |

### Anti-Features (CUSTOMER)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Internal controller report visibility | Reports are internal; surfacing unvalidated data misleads customers | Show only NYC Open Data grades + scores |
| Customer ability to file or edit inspection data | Crowdsourced inspection data creates liability and data integrity issues | Read-only access to all restaurant/inspection data |
| Real-time data via WebSocket | Overkill for inspection data that updates on inspection cycles (not second-by-second) | TTL cache (Redis, 3600s) is sufficient; already in place |
| Email/SMS alerts for grade changes | Notification infrastructure out of scope for v1 | Bookmarks serve the "follow a restaurant" intent without notifications |
| Rating or review system | Creates UGC moderation burden; diverges from official data focus | Official NYC grades and scores are the authoritative signal |

---

## Feature Dependencies

```
CONTROLLER registration (registration code) → Role-based route protection
Role-based route protection → Controller report CRUD
Controller report CRUD → Report status lifecycle
Controller report CRUD → Report history per restaurant

Customer open registration → Default CUSTOMER role assigned
Search endpoint → Restaurant detail page (customer flow)
Restaurant detail page → Grade trend indicator
Restaurant bookmarks → (already done, no new deps)
```

---

## MVP Recommendation

### CONTROLLER MVP (minimum to make the role functional)

1. Registration code gate on signup → role = `CONTROLLER`
2. `@PreAuthorize("hasRole('CONTROLLER')")` on all controller-only endpoints
3. `InspectionReportEntity` (PostgreSQL): `id`, `restaurantId`, `userId`, `violationCodes`, `score`, `grade`, `status`, `notes`, `createdAt`, `updatedAt`
4. `POST /api/reports` — create report
5. `GET /api/reports/my` — list own reports
6. `PUT /api/reports/{id}` — update own report (status, notes, score)
7. Repoint `InspectionController /api/inspection/at-risk` from `ADMIN` to `CONTROLLER`

### CUSTOMER MVP (minimum to deliver the core value promise)

1. Open registration defaults to `CUSTOMER` role
2. `GET /api/restaurants/search?q=` — name/address search (new endpoint)
3. Restaurant detail page (`/restaurant/{id}`) updated to prominently show latest grade + score + violation summary
4. Grade trend indicator (computed from existing `grades` array — no new data needed)
5. Map page updated with grade-based marker colors

### Defer

| Feature | Reason to Defer |
|---------|----------------|
| Photo attachments on reports | File storage infrastructure not in scope for v1; significant complexity vs value |
| "Similar restaurants nearby" | Geospatial query adds complexity; not table stakes |
| Email/SMS notifications | Notification service is a separate infrastructure concern |
| Publishing reports to external systems | No external write API exists |

---

## Data Model Gap Analysis

The existing domain model covers most customer-facing needs. The gap is entirely on the controller side.

| Need | Current State | Gap |
|------|--------------|-----|
| Restaurant grade + score | `Restaurant.grades` (List<Grade>) — fully populated | None |
| Violation detail | `Grade.violationCode`, `Grade.violationDescription`, `Grade.criticalFlag` | None |
| Geo coordinates for map | `Address.coord` (List<Double>) | None — already in MongoDB |
| Controller inspection report | Nothing | New `InspectionReportEntity` + repository + service + controller |
| Role on user | `UserEntity.role` (String) — column exists | Role values not yet enforced as Spring Security authorities |
| Registration code validation | Nothing | New validation in `AuthService.register()` |

---

## Sources

- Codebase analysis: `UserEntity.java`, `BookmarkEntity.java`, `InspectionController.java`, `UserController.java`, `ViewController.java`, `Restaurant.java`, `Grade.java`, `Address.java`
- Project requirements: `.planning/PROJECT.md`
- Domain knowledge: NYC Department of Health restaurant grading system (A/B/C grades, 0-13 = A, 14-27 = B, 28+ = C/Z/P)
- Confidence: HIGH — feature list grounded in existing data model and stated project requirements, not speculation
