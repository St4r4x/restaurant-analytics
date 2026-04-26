# Deployment — restaurant-analytics

## Where we left off

The app is fully built (phases 1–4 complete). CI pipeline is in `.github/workflows/ci.yml`
and already builds + pushes the Docker image to GHCR on `main`:

```
ghcr.io/st4r4x/restaurant-analytics:latest
```

The next step is wiring up managed free-tier services and adding a deploy job to CI.

---

## Architecture decision

**Do not run docker-compose in production.** The 4-service stack (app + MongoDB + Redis +
PostgreSQL) exceeds free-tier memory on any single host. Instead, split across managed
services and deploy only the Spring Boot container.

| Service | Platform | Free tier |
|---|---|---|
| Spring Boot app | Railway (or Render) | Pull image from GHCR |
| MongoDB | MongoDB Atlas M0 | 512 MB, no expiry |
| PostgreSQL | Supabase (or Neon) | 500 MB |
| Redis | Upstash | 10k commands/day |

---

## What still needs to be done

### 1. Create the managed services

- [ ] **MongoDB Atlas** — create M0 cluster, database `newyork`, collection `restaurants`.
  Note the connection string: `mongodb+srv://...`
- [ ] **Supabase** (or Neon) — create project, note `SPRING_DATASOURCE_URL` (JDBC format),
  username, password
- [ ] **Upstash** — create Redis database, note host + port + password (TLS)
- [ ] **Railway** — create new project, connect to GHCR image
  `ghcr.io/st4r4x/restaurant-analytics:latest`

### 2. Set environment variables on Railway

Map each service's credentials to the env vars the app already reads:

```
MONGODB_URI=mongodb+srv://<user>:<pass>@<cluster>.mongodb.net
MONGODB_DATABASE=newyork
MONGODB_COLLECTION=restaurants

SPRING_DATASOURCE_URL=jdbc:postgresql://<supabase-host>:5432/postgres
SPRING_DATASOURCE_USERNAME=<supabase-user>
SPRING_DATASOURCE_PASSWORD=<supabase-password>

REDIS_HOST=<upstash-host>
REDIS_PORT=6379
# If Upstash requires password, add REDIS_PASSWORD and update RedisConfig

JWT_SECRET=<min-32-chars>
CONTROLLER_SIGNUP_CODE=<optional>
ADMIN_SIGNUP_CODE=<optional>
APP_UPLOADS_DIR=/app/uploads
```

> Upstash free tier uses TLS on port 6380. `RedisConfig.java` may need a `useSsl(true)` flag — check before deploying.

### 3. Add deploy job to CI

Append to `.github/workflows/ci.yml` after the `docker` job:

```yaml
deploy:
  name: Deploy to Railway
  needs: docker
  runs-on: ubuntu-latest
  if: github.ref == 'refs/heads/main'
  steps:
    - name: Trigger Railway redeploy
      run: curl -X POST "${{ secrets.RAILWAY_WEBHOOK_URL }}"
```

Add `RAILWAY_WEBHOOK_URL` as a GitHub Actions secret (Railway → project → settings →
deploy webhook).

### 4. Seed data

After the first deploy, trigger a manual sync to populate MongoDB:

```
POST /api/restaurants/refresh   (requires ROLE_ADMIN JWT)
```

Or set `NYC_API_MAX_RECORDS=5000` temporarily to do a quick partial import.

---

## Upstash TLS note

If Redis connections fail on Upstash, `RedisConfig.java` needs:

```java
config.useSsl(true);  // add to the LettuceClientConfiguration builder
```

The connection port should be `6380` in that case.

---

## Current CI pipeline (reference)

```
push to main
  └── build → unit-test → integration-test → e2e → docker (push to GHCR) → [deploy ← to add]
```

The `docker` job already tags `:latest` and `sha-<short>` on `main` pushes only.

---

## Ports / health check

The app exposes `8080`. Railway auto-detects this from the `EXPOSE 8080` in `Dockerfile`.
Health endpoint: `GET /api/restaurants/health` — returns `200 OK` when the app is up.
