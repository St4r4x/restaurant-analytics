# Restaurant Analytics — NYC Inspection Data

![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)

**Live demo:** https://restaurant-app-production-3b11.up.railway.app/

Spring Boot REST API and web dashboard for exploring NYC restaurant hygiene data.
Data is synced from the NYC Open Data API into MongoDB. Two user roles: **CUSTOMER** (discovery) and **CONTROLLER** (inspection report filing).

Academic project — Aflokkat / big data module.

---

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.5 |
| Primary DB | MongoDB (raw driver, no Spring Data) |
| RDBMS | PostgreSQL 15 (users, bookmarks, reports) |
| Cache | Redis 7 (TTL 3600 s) |
| Security | JWT (access 15 min / refresh 7 days) |
| Build | Maven |
| Deployment | Docker Compose |

---

## Quick Start

```bash
docker compose up -d
# App: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

See **[docs/development.md](docs/development.md)** for local dev setup, seeded test accounts, and how to run tests.

---

## Documentation

| Topic | File |
|-------|------|
| Architecture & package structure | [docs/architecture.md](docs/architecture.md) |
| API endpoint reference | [docs/api.md](docs/api.md) |
| Configuration keys & env vars | [docs/configuration.md](docs/configuration.md) |
| Docker Compose deployment | [docs/deployment.md](docs/deployment.md) |
| UI pages & design system | [docs/ui.md](docs/ui.md) |
| Local development & testing | [docs/development.md](docs/development.md) |
