# Roadmap — Restaurant Analytics

## État actuel (v2.0)

Spring Boot REST API + Thymeleaf dashboard analysant les données d'inspection sanitaire des restaurants de New York City. Données synchronisées depuis l'API NYC Open Data, stockées dans MongoDB, accélérées par Redis, avec gestion des utilisateurs sur PostgreSQL et authentification JWT.

---

## Phase 1 — Intégration API externe ✅

- [x] `NycOpenDataClient` : client HTTP paginé (RestTemplate, backoff exponentiel)
- [x] Mapping des champs API vers le modèle `Restaurant` / `Address` / `Grade`
- [x] `@Scheduled` — sync nightly à 02:00
- [x] `POST /api/restaurants/refresh` — sync manuel
- [x] `GET /api/restaurants/sync-status` — état du dernier sync + flag `running`
- [x] Retry logic avec backoff exponentiel
- [x] Déduplication : N lignes de violations → 1 document par restaurant, grades dédupliqués par date

---

## Phase 2 — Redis (cache + leaderboard) ✅

- [x] Dépendance Spring Data Redis (Lettuce)
- [x] Cache-aside sur les agrégations coûteuses (borough counts, cuisine scores) — TTL 1h
- [x] Sorted set `restaurants:top` mis à jour à chaque sync
- [x] `GET /api/restaurants/top` — classement par note d'inspection
- [x] Invalidation complète au sync via SCAN (non-bloquant)

---

## Phase 3 — Gestion des utilisateurs

### 3.1 Authentification ✅

- [x] Spring Security + JJWT 0.11.5
- [x] `POST /api/auth/register` — création de compte (BCrypt)
- [x] `POST /api/auth/login` — retourne access + refresh token
- [x] `POST /api/auth/refresh` — renouvellement du token
- [x] Stockage des utilisateurs dans PostgreSQL (`UserEntity` via Spring Data JPA)
- [x] `JwtAuthenticationFilter` — parse des claims en une seule opération par requête

### 3.2 Autorisation ✅

- [x] Définir les rôles : `ROLE_USER`, `ROLE_ADMIN`
- [x] Protéger les endpoints admin (`/api/restaurants/refresh`, `/api/restaurants/sync-status`) derrière `ROLE_ADMIN` (`@PreAuthorize` + `@EnableMethodSecurity`)
- [x] Endpoint `GET /api/users/me` — profil de l'utilisateur connecté
- [ ] Endpoint `PATCH /api/users/me` — mise à jour du profil

### 3.3 Bookmarks ✅

- [x] `POST /api/users/me/bookmarks/{restaurantId}` — mettre en favori (idempotent)
- [x] `DELETE /api/users/me/bookmarks/{restaurantId}` — retirer un favori
- [x] `GET /api/users/me/bookmarks` — liste des favoris enrichie (données MongoDB)
- [x] Bouton bookmark (★) dans le dashboard — carte Top Restaurants Sains + carte Mes Favoris
- [x] `BookmarkEntity` JPA + `BookmarkRepository` (contrainte unique `user_id / restaurant_id`)

### 3.4 UI ⏳ Partiel

- [x] Page login / formulaire (`/login`)
- [x] Bouton logout dans le dashboard (JWT en localStorage)
- [ ] Page profil utilisateur (bookmarks, historique)
- [ ] Panel admin : logs de sync, déclenchement manual sécurisé

---

## Phase 3.5 — Corrections identifiées (IMPROVEMENTS.md) ✅

| # | Fichier | Problème | État |
|---|---|---|---|
| 1 | `RestaurantController` | Toutes les erreurs retournent HTTP 500 au lieu de 400 pour les `IllegalArgumentException` | ✅ Résolu |
| 2 | `RestaurantService` | N+1 queries dans `getTrashAdvisorRestaurants` | ✅ Résolu (`$in`) |
| 3 | `aggregation/` | 3 classes mortes (`BoroughCount`, `AggregationId`, `RestaurantAggregation`) | ✅ Supprimées |
| 4 | `Address.java` | `toString()` produit `"null null, null"` si champs absents | ✅ Null-safe |
| 5 | `trash-advisor.html` | `#info-box` div + CSS `.info-box` jamais utilisés | ✅ Inexistants |
| 6 | `trash-advisor.html` | CSS `.control-group input` orphelin | ✅ Non-applicable |
| 7 | `MongoClientFactory` | `volatile` + `synchronized` — mélange de patterns | ⏳ Basse priorité |
| 8 | `RestaurantService` | DAO instancié directement — non-mockable | ✅ Résolu (constructor DI) |

---

## Phase 4 — Application métier : agents d'inspection & citoyens ✅

L'objectif est de transformer le dashboard analytique en une vraie application à double audience :
- **Agents d'inspection** : outils d'aide à la planification et au suivi terrain
- **Citoyens** : transparence et information sur l'hygiène des restaurants qu'ils fréquentent

### 4.1 Page détail restaurant ✅

- [x] Endpoint `GET /api/restaurants/{restaurantId}` — données complètes + historique des grades
- [x] Vue `/restaurant/{id}` : fiche complète (nom, adresse, carte Leaflet, historique des inspections avec dates/scores/violations, graphique Chart.js)
- [x] Liens "Voir" depuis les cartes du dashboard et Hygiene Radar

### 4.2 Badge de confiance (citoyens) ✅

- [x] Calcul côté serveur (getters calculés, zéro stockage MongoDB) : dernière note, score, tendance (improving / stable / worsening)
- [x] Badge coloré (vert A / jaune B / orange C / rouge Z-N-P) dans toutes les réponses API
- [x] Affichage du badge dans la fiche restaurant

### 4.3 Recherche géospatiale (citoyens) ✅

- [x] Index `2dsphere` sur `address.coord` (idempotent au démarrage)
- [x] Endpoint `GET /api/restaurants/nearby?lat=&lng=&radius=&limit=` — `$geoNear` pipeline
- [x] Widget carte "autour de moi" dans le dashboard (géolocalisation navigateur + markers Leaflet)

### 4.4 Carte de chaleur des violations (agents) ✅

- [x] Endpoint `GET /api/restaurants/heatmap?borough=&limit=` — points GPS + score (admin)
- [x] Vue `/inspection-map` avec Leaflet + Leaflet.heat (CDN)
- [x] Filtre par quartier (re-fetch API côté client)

### 4.5 Tableau de bord agents d'inspection ✅

- [x] Vue `/inspection` (protégée `ROLE_ADMIN`)
  - Établissements à risque (dernière note C ou Z) avec comptage notes consécutives
  - Classement des pires cuisines (Chart.js barre)
  - Inspections récentes
- [x] Endpoint `GET /api/inspection/at-risk?borough=&limit=`
- [x] Export CSV `GET /api/inspection/at-risk/export.csv`

### 4.6 Historique & tendances (agents + citoyens) ✅

- [x] Graphique d'évolution des scores dans la fiche restaurant (Chart.js ligne)
- [x] Tendance automatique calculée depuis les grades (±5 points seuil)

### 4.7 Fil d'actualité des inspections récentes (citoyens) ✅

- [x] Endpoint `GET /api/restaurants/recent-inspections?days=&limit=`
- [x] Section "Inspections Récentes" dans le dashboard

### Extras Phase 4

- [x] Renommage Trash Advisor → **Hygiene Radar** (image plus professionnelle)
- [x] `POST /api/restaurants/rebuild-cache` — reconstruire le leaderboard Redis depuis MongoDB sans sync externe
- [x] Suppression des 10 documents legacy (address en string, fausses données de test)
- [x] Refactoring : `ResponseUtil.errorResponse()` partagé, `getLatestGradeEntry()` helper, `Promise.all` sur la page inspection

---

## Phase 5 — Stretch goals (post-académique)

| Feature | Description | Prérequis |
|---|---|---|
| Recherche full-text (Elasticsearch) | Index + `GET /api/restaurants/search?q=` | Phase 3 complète |
| Requêtes géospatiales avancées | `$geoNear` MongoDB + isochrones | 4.3 |
| Pipeline Kafka | Topic d'ingestion alimenté par le client API | Infrastructure |
| Observabilité | Micrometer + Prometheus + Grafana | — |
| CI/CD | GitHub Actions : build, test, Docker push sur chaque PR | — |
| GraphQL | Complément ou remplacement de l'API REST | Phase 3 complète |
| Notifications push | Alerte quand un restaurant favori reçoit une mauvaise note | 3.3 + 4.1 |
