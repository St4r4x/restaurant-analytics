---
created: 2026-03-31T12:11:36.449Z
title: Refresh and update project documentation
area: docs
files:
  - README.md
  - ARCHITECTURE.md
  - CHANGELOG.md
  - ROADMAP.md
  - CLAUDE.md
---

## Problem

The project documentation predates the GSD phases and no longer reflects the current state of the codebase:
- `README.md` likely describes the original quickstart app, not the full restaurant hygiene platform built across 3 phases
- `ARCHITECTURE.md` may be missing the new layers: InspectionReportEntity (PostgreSQL), photo upload volume, new REST endpoints (search, map-points, reports), new templates (my-bookmarks.html)
- `CHANGELOG.md` may not reflect the changes shipped in phases 1–3
- `ROADMAP.md` at the project root (if different from `.planning/ROADMAP.md`) may be stale
- API endpoint table in docs is likely incomplete (missing /api/reports/**, /api/restaurants/search, /api/restaurants/map-points)

## Solution

1. Rewrite `README.md`: project overview, stack, quick-start instructions, API summary, user roles (CUSTOMER / CONTROLLER), seeded test accounts
2. Update `ARCHITECTURE.md`: add new entities (InspectionReportEntity, Grade, Status enums), new DAO methods, new controllers (ReportController), Docker volumes (uploads_data)
3. Update `CHANGELOG.md`: document phases 1, 2, and 3 changes
4. Verify `CLAUDE.md` still accurately reflects the project (likely mostly up to date)
5. Remove or update root `ROADMAP.md` if it conflicts with `.planning/ROADMAP.md`
