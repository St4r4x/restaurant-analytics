# Restaurant Analytics — NYC Inspection Data

Spring Boot REST API and web dashboard for exploring NYC restaurant hygiene data.
Data is synced from the NYC Open Data API into MongoDB. The application supports two
user roles: **CUSTOMER** (read-only discovery) and **CONTROLLER** (inspection report filing).

Academic project — Aflokkat / big data module.

## Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 11 |
| Framework | Spring Boot 2.6.15 |
| Primary DB | MongoDB (raw driver, no Spring Data) |
| RDBMS | PostgreSQL 15 (users, bookmarks, reports) |
| Cache | Redis 7 (TTL 3600 s) |
| Security | JWT (access 15 min / refresh 7 days) |
| Build | Maven |
| Deployment | Docker Compose |

## Quick Start

```bash
# Build
mvn clean package -DskipTests

# Start all services (MongoDB, Redis, PostgreSQL, app)
docker compose up -d

# Follow logs
docker compose logs -f app
```

App runs on http://localhost:8080. Swagger UI: http://localhost:8080/swagger-ui.html

## Seeded Test Accounts

Two accounts are seeded on startup via `DataSeeder`:

| Username | Password | Role |
|----------|----------|------|
| customer_test | password | ROLE_CUSTOMER |
| controller_test | password | ROLE_CONTROLLER |

## User Roles

**ROLE_CUSTOMER**
- Search restaurants by name or address
- View restaurant detail page (grade badge, inspection history)
- Browse restaurants on the interactive map
- Bookmark restaurants

**ROLE_CONTROLLER**
- All customer capabilities
- File internal inspection reports (`POST /api/reports`)
- List and filter own reports (`GET /api/reports`)
- Edit own reports (`PATCH /api/reports/{id}`)
- Attach photos to reports (`POST /api/reports/{id}/photo`)

Controller registration requires the `CONTROLLER_SIGNUP_CODE` environment variable
to be set. If absent, all controller signups return HTTP 400.

## API Endpoints

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/auth/register | Register (CUSTOMER or CONTROLLER with signup code) |
| POST | /api/auth/login | Login → access + refresh JWT |
| POST | /api/auth/refresh | Refresh access token |

### Restaurants (public read)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/restaurants/by-borough | Count per borough |
| GET | /api/restaurants/stats | Global stats |
| GET | /api/restaurants/health | Health check |
| GET | /api/restaurants/search?q=&limit= | Search by name or address |
| GET | /api/restaurants/map-points | Lightweight points for map |
| GET | /api/restaurants/{restaurantId} | Restaurant detail |
| GET | /api/restaurants/top | Top healthiest restaurants |
| GET | /api/restaurants/cuisines | All distinct cuisine types |
| GET | /api/restaurants/by-cuisine | Top cuisines by count |
| GET | /api/restaurants/hygiene-radar | Healthiest restaurants by borough/cuisine |
| GET | /api/restaurants/recent-inspections | Restaurants inspected in last N days |
| GET | /api/restaurants/nearby | Geospatial search (2dsphere) |

### Reports (CONTROLLER only)

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/reports | Create inspection report |
| GET | /api/reports | List own reports (optional ?status=) |
| PATCH | /api/reports/{id} | Edit own report |
| POST | /api/reports/{id}/photo | Upload photo |
| GET | /api/reports/{id}/photo | Stream photo |

### Users (authenticated)

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/users/me | Current user profile |
| GET | /api/users/bookmarks | List bookmarks |
| POST | /api/users/bookmarks | Add bookmark |
| DELETE | /api/users/bookmarks/{restaurantId} | Remove bookmark |

## Pages

| URL | Description |
|-----|-------------|
| / | Dashboard (analytics charts) |
| /login | Login / Register |
| /restaurant/{camis} | Restaurant detail |
| /inspection-map | Interactive grade-colored map |
| /my-bookmarks | Saved restaurants |

## Configuration

Key `application.properties` settings:

```properties
mongodb.uri=mongodb://mongodb:27017
mongodb.database=newyork
mongodb.collection=restaurants
spring.datasource.url=jdbc:postgresql://postgres:5432/restaurantdb
redis.host=redis
redis.port=6379
nyc.api.max_records=0   # 0 = unlimited; set to 5000 for local testing
jwt.secret=<min 32 chars>
```

## Tests

```bash
mvn test                                         # full suite
mvn test -Dtest=ReportControllerTest,SecurityConfigTest -q   # targeted
```
