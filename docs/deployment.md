# Deployment

## Docker Compose (recommended)

```bash
# Start all services in the background
docker compose up -d

# Follow application logs
docker compose logs -f app

# Stop and remove containers (data volumes preserved)
docker compose down

# Rebuild the app image after code changes
docker compose up -d --build app
```

### Services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `app` | Built from `Dockerfile` | 8080 | Spring Boot application |
| `mongodb` | `mongo:7.0` | 27017 | Primary restaurant data store |
| `postgres` | `postgres:15-alpine` | 5432 | Users, bookmarks, inspection reports |
| `redis` | `redis:7-alpine` | 6379 | Cache + sorted-set leaderboard |

### Startup Order

Docker Compose healthchecks ensure the app container waits for MongoDB, PostgreSQL, and Redis to be ready before starting.

### Volumes

| Volume | Service | Purpose |
|--------|---------|---------|
| `mongodb_data` | mongodb | Persists restaurant documents |
| `postgres_data` | postgres | Persists user and report tables |

### Environment Variables

See [configuration.md](configuration.md) for the full list. At minimum, set `JWT_SECRET` (≥ 32 chars) and optionally `CONTROLLER_SIGNUP_CODE` before starting.

---

## Manual Build

```bash
# Compile and package (skip tests for a fast build)
mvn clean package -DskipTests

# Run with defaults from application.properties
java -jar target/quickstart-app-1.0-SNAPSHOT.jar

# Override MongoDB URI at runtime
java -Dmongodb.uri=mongodb://localhost:27017 -jar target/quickstart-app-1.0-SNAPSHOT.jar
```

---

## Production Notes

- `nyc.api.max_records=0` means unlimited — the nightly sync can take several minutes on first run.
- The nightly sync is scheduled at **02:00 server local time** via `@Scheduled(cron = "0 0 2 * * *")` in `SyncService`.
- Manual sync trigger: `POST /api/restaurants/refresh` (ADMIN role required).
- `DataSeeder` creates three seeded accounts on every startup (see [development.md](development.md)). Gate it behind `@Profile("dev")` before going to a shared production environment.
- The `ARCHITECTURE.md` at the project root is superseded by [docs/architecture.md](architecture.md).
