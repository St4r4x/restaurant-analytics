# Configuration Reference

## Property Resolution Order

`AppConfig` resolves each property through four tiers (first match wins):

1. JVM system property (`-Dmongodb.uri=…`)
2. Environment variable (`MONGODB_URI`)
3. `.env` file in the project root
4. `src/main/resources/application.properties`

Docker Compose injects tier-2 environment variables on the `app` container — the defaults in `application.properties` are only used for local `mvn spring-boot:run`.

---

## `application.properties` Keys

### MongoDB

| Key | Default | Description |
|-----|---------|-------------|
| `mongodb.uri` | `mongodb://mongodb:27017` | Connection URI |
| `mongodb.database` | `newyork` | Database name |
| `mongodb.collection` | `restaurants` | Primary collection |

### PostgreSQL

| Key | Default | Description |
|-----|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://postgres:5432/restaurantdb` | JDBC URL |
| `spring.datasource.username` | `restaurant` | DB user |
| `spring.datasource.password` | `restaurant` | DB password |
| `spring.jpa.hibernate.ddl-auto` | `update` | Hibernate DDL strategy |

### Redis

| Key | Default | Description |
|-----|---------|-------------|
| `redis.host` | `redis` | Lettuce host |
| `redis.port` | `6379` | Lettuce port |
| `redis.cache.ttl-seconds` | `3600` | Default TTL for all cache keys |

### NYC Open Data Sync

| Key | Default | Description |
|-----|---------|-------------|
| `nyc.api.url` | `https://data.cityofnewyork.us/resource/43nn-pn8j.json` | Source endpoint |
| `nyc.api.app_token` | *(empty)* | Optional Socrata app token — avoids rate limiting |
| `nyc.api.page-size` | `1000` | Records per page |
| `nyc.api.max_records` | `0` | 0 = unlimited; set to e.g. `5000` for local testing |

### JWT

| Key | Default | Description |
|-----|---------|-------------|
| `jwt.secret` | *(must set)* | HMAC signing secret — minimum 32 characters |
| `jwt.access.expiration.ms` | `900000` | Access token TTL (15 min) |
| `jwt.refresh.expiration.ms` | `604800000` | Refresh token TTL (7 days) |

### Signup Codes

| Key | Default | Description |
|-----|---------|-------------|
| `CONTROLLER_SIGNUP_CODE` | *(env var)* | Required to register as CONTROLLER; unset = 400 |
| `ADMIN_SIGNUP_CODE` | *(env var)* | Required to register as ADMIN; unset = signup disabled |

---

## Environment Variables (Docker Compose)

The `app` service in `docker-compose.yml` maps these env vars:

| Variable | Maps to property |
|----------|-----------------|
| `MONGODB_URI` | `mongodb.uri` |
| `MONGODB_DATABASE` | `mongodb.database` |
| `MONGODB_COLLECTION` | `mongodb.collection` |
| `REDIS_HOST` | `redis.host` |
| `REDIS_PORT` | `redis.port` |
| `JWT_SECRET` | `jwt.secret` |
| `CONTROLLER_SIGNUP_CODE` | controller registration gate |
| `ADMIN_SIGNUP_CODE` | admin registration gate |
