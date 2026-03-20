# Restaurant Analytics - CLAUDE.md

## Project Overview

Spring Boot REST API + web dashboard for analyzing New York restaurant data stored in MongoDB.
Academic project (Aflokkat / big data module).

## Stack

- **Language**: Java 11
- **Framework**: Spring Boot 2.6.15
- **Database**: MongoDB (driver: `mongodb-driver-sync`)
- **Build**: Maven (`mvn`)
- **Config**: dotenv-java (`.env` file at project root)
- **Testing**: JUnit 4 + Mockito
- **Deployment**: Docker / Docker Compose

## Architecture

```
com.aflokkat/
├── Application.java          # Spring Boot entry point
├── controller/               # REST endpoints + Thymeleaf view routes
├── service/                  # Business logic
├── dao/                      # MongoDB data access (interface + impl)
├── domain/                   # POJOs (Restaurant)
├── aggregation/              # Aggregation result DTOs
├── config/                   # MongoClientFactory, AppConfig
└── util/                     # ValidationUtil
```

## Configuration

Environment variables (via `.env` or Docker):
```
MONGODB_URI=mongodb://localhost:27017
MONGODB_DATABASE=newyork
MONGODB_COLLECTION=restaurants
```

Docker Compose uses `MONGO_URI` and `MONGO_DB` (different keys — see `docker-compose.yml`).

## Common Commands

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
java -jar target/quickstart-app-1.0-SNAPSHOT.jar

# Tests
mvn test
mvn test -Dtest=RestaurantDAOIntegrationTest

# Docker
docker-compose up -d
docker-compose logs -f app
docker-compose down
```

## API Endpoints

| Endpoint | Description |
|---|---|
| `GET /api/restaurants/by-borough` | Count per borough |
| `GET /api/restaurants/cuisine-scores?cuisine=X` | Avg score by borough for a cuisine |
| `GET /api/restaurants/worst-cuisines?borough=X&limit=N` | Worst cuisines in a borough |
| `GET /api/restaurants/popular-cuisines?minCount=N` | Cuisines with >= N restaurants |
| `GET /api/restaurants/stats` | Global stats |
| `GET /api/restaurants/health` | Health check |

App runs on `http://localhost:8080`.

## Key Notes

- Integration tests require a live MongoDB instance on `localhost:27017` with the `newyork` DB loaded
- Data import: use `init-restaurants.js` or import `restaurants.json` manually
- No Spring Data MongoDB — uses raw `mongodb-driver-sync` with manual aggregation pipelines
- The `GeocodingService` caches coordinates internally
