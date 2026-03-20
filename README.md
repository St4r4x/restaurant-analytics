# Restaurant Analytics

Spring Boot REST API + dashboard web pour analyser les données d'inspection sanitaire des restaurants de New York City.

**Stack** : Java 11 · Spring Boot 2.6 · MongoDB · Redis · PostgreSQL · Docker Compose · JWT

---

## Démarrage rapide

```bash
# Copier les variables d'environnement
cp .env.example .env   # ou utiliser le .env fourni

# Démarrer tous les services (MongoDB + Redis + PostgreSQL + app)
docker compose up -d --build

# Attendre ~15s que les healthchecks passent
# Ouvrir le navigateur
open http://localhost:8080
```

### Comptes par défaut

| Rôle | Username | Password |
|---|---|---|
| Admin | `admin` | `adminpass` |
| User | `testuser` | `pass123` |

> **Première utilisation** : connectez-vous en tant qu'admin, puis cliquez sur **🔄 Reconstruire le cache** dans la carte "Top Restaurants Sains" pour peupler le leaderboard Redis.

---

## Fonctionnalités

### Citoyens (tous les utilisateurs)

- **Dashboard** — statistiques par quartier, scores de cuisine, top restaurants sains, favoris, inspections récentes, carte "autour de moi"
- **Fiche restaurant** (`/restaurant/{id}`) — historique complet des inspections, graphique d'évolution des scores, carte Leaflet, badge de confiance (A/B/C/Z + tendance)
- **Hygiene Radar** (`/hygiene-radar`) — recherche des restaurants les plus sains par quartier/cuisine
- **Favoris** — bookmark/unbookmark, liste persistée par compte

### Agents d'inspection (ROLE_ADMIN)

- **Carte de chaleur** (`/inspection-map`) — heatmap Leaflet des scores d'inspection, filtre par quartier
- **Tableau de bord agents** (`/inspection`) — établissements à risque (dernière note C/Z), pires cuisines (Chart.js), export CSV, inspections récentes
- **Sync NYC Open Data** — `POST /api/restaurants/refresh` déclenche une ingestion depuis l'API officielle; sync automatique à 02:00 chaque nuit

---

## API REST

Tous les endpoints nécessitent un token JWT (`Authorization: Bearer <token>`).

```bash
# Obtenir un token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"pass123"}' | jq -r .accessToken)
```

| Endpoint | Auth | Description |
|---|---|---|
| `POST /api/auth/login` | — | Connexion, retourne access + refresh token |
| `POST /api/auth/register` | — | Création de compte |
| `POST /api/auth/refresh` | — | Renouvellement du token |
| `GET /api/restaurants/by-borough` | User | Comptage par quartier (Redis TTL 1h) |
| `GET /api/restaurants/cuisine-scores` | User | Score moyen par quartier pour une cuisine |
| `GET /api/restaurants/worst-cuisines` | User | Pires cuisines d'un quartier |
| `GET /api/restaurants/popular-cuisines` | User | Cuisines avec ≥ N restaurants |
| `GET /api/restaurants/stats` | User | Statistiques globales |
| `GET /api/restaurants/top` | User | Top restaurants sains (Redis leaderboard) |
| `GET /api/restaurants/{id}` | User | Fiche complète d'un restaurant |
| `GET /api/restaurants/recent-inspections` | User | Inspections des N derniers jours |
| `GET /api/restaurants/nearby` | User | Restaurants à moins de R mètres (2dsphere) |
| `GET /api/restaurants/hygiene-radar` | User | Restaurants sains par quartier/cuisine |
| `GET /api/restaurants/heatmap` | **Admin** | Points GPS + score pour heatmap |
| `POST /api/restaurants/refresh` | **Admin** | Sync NYC Open Data |
| `POST /api/restaurants/rebuild-cache` | **Admin** | Reconstruire le leaderboard Redis |
| `GET /api/inspection/at-risk` | **Admin** | Établissements à risque (grade C/Z) |
| `GET /api/inspection/at-risk/export.csv` | **Admin** | Export CSV |
| `GET /api/users/me` | User | Profil utilisateur |
| `GET /api/users/me/bookmarks` | User | Favoris |
| `POST /api/users/me/bookmarks/{id}` | User | Ajouter un favori |
| `DELETE /api/users/me/bookmarks/{id}` | User | Retirer un favori |

Documentation Swagger : `http://localhost:8080/swagger-ui.html`

---

## Structure du projet

```
src/main/java/com/aflokkat/
├── controller/        # REST endpoints + routes Thymeleaf
├── service/           # Logique métier
├── dao/               # Accès MongoDB (interface + impl, pipelines manuels)
├── domain/            # POJOs (Restaurant, Grade, Address)
├── dto/               # DTOs de réponse (HeatmapPoint, AtRiskEntry, …)
├── aggregation/       # DTOs d'agrégation MongoDB
├── cache/             # RestaurantCacheService (Redis)
├── sync/              # Client NYC Open Data + SyncService
├── security/          # JwtUtil + JwtAuthenticationFilter
├── entity/            # Entités JPA (UserEntity, BookmarkEntity)
├── repository/        # Spring Data JPA repositories
├── config/            # SecurityConfig, MongoClientFactory, RedisConfig
└── util/              # ValidationUtil, ResponseUtil

src/main/resources/templates/
├── login.html          # Page de connexion
├── index.html          # Dashboard principal
├── restaurant.html     # Fiche détail restaurant
├── hygiene-radar.html  # Recherche restaurants sains
├── inspection-map.html # Carte de chaleur (admin)
└── inspection.html     # Tableau de bord agents (admin)
```

---

## Configuration

Variables d'environnement (`.env`) :

```env
MONGODB_URI=mongodb://localhost:27017
MONGODB_DATABASE=newyork
MONGODB_COLLECTION=restaurants
REDIS_HOST=localhost
REDIS_PORT=6379
API_SECRET=<jwt_secret>
ADMIN_USERNAME=admin
ADMIN_PASSWORD=adminpass
USER_USERNAME=testuser
USER_PASSWORD=pass123
```

---

## Commandes utiles

```bash
# Build
mvn clean package -DskipTests

# Tests unitaires (21 tests, pas de MongoDB requis)
mvn test

# Logs Docker
docker compose logs -f app

# Reconstruire le cache Redis depuis MongoDB (admin)
curl -X POST http://localhost:8080/api/restaurants/rebuild-cache \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```
