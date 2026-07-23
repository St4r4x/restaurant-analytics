# Dossier technique — Application développée

**Certification** : RNCP niveau 7 — Expert en informatique et système d'information (3W Academy)
**Bloc** : 3 — Concevoir et développer une application informatique
**Livrable** : Application développée + dossier technique (évaluation individuelle)
**Candidat** : Arnaud Thery
**Application livrée** : `restaurant-analytics` — code source sur GitHub, démo déployée sur Railway

---

## 1. Revue de code

### 1.1 Méthode

La revue de code n'est pas ponctuelle : elle est intégrée au workflow de développement via le processus Superpowers (`docs/superpowers/`) — chaque fonctionnalité passe par un cycle brainstorm → spec → plan → implémentation, documenté dans `docs/superpowers/specs/` et `docs/superpowers/plans/`. Cette section formalise une revue rétrospective sur l'état actuel du code, avec des recommandations concrètes.

### 1.2 Constats — points forts

- **Séparation des responsabilités respectée** : `dao/` (accès MongoDB brut), `service/` (logique métier), `controller/` (HTTP), `repository/` (JPA/PostgreSQL) — pas de logique métier fuyant dans les contrôleurs.
- **Pas de duplication de la connexion MongoDB** : `MongoClientFactory` centralise l'instanciation, chaque DAO récupère l'instance via `getInstance().getDatabase(...)` plutôt que de recréer une connexion.
- **Gestion des erreurs cohérente** : `ResponseUtil.errorResponse()` centralise le format de réponse d'erreur JSON et empêche la fuite de stack traces vers le client (`src/main/java/com/st4r4x/util/ResponseUtil.java:23-26`).
- **Sécurité par défaut** : `SecurityConfig` déclare une politique `STATELESS` (pas de session serveur) et un ordre de règles explicite pour éviter qu'un wildcard plus large (`/api/reports/**`) ne masque une règle plus spécifique (`/api/reports/stats`).
- **Limitations connues documentées dans le code lui-même** : le commentaire de `RateLimitFilter` signale explicitement que les `ConcurrentHashMap` de buckets sont non bornées et propose la solution (`Guava CacheBuilder.expireAfterAccess()`) — pratique qui facilite la maintenance par un tiers.

### 1.3 Recommandations pour l'équipe de développement

| # | Recommandation | Fichier concerné | Justification |
|---|---|---|---|
| 1 | Sortir `jwt.secret` de `application.properties` (actuellement versionné en clair) vers une variable d'environnement obligatoire | `src/main/resources/application.properties:27` | Un secret cryptographique ne doit jamais être committé, même dans un contexte académique — risque de fuite via l'historique Git |
| 2 | Remplacer les `ConcurrentHashMap` non bornées de `RateLimitFilter` par un cache à expiration | `src/main/java/com/st4r4x/security/RateLimitFilter.java:38-39` | Le commentaire du fichier signale déjà la limite ; sous charge soutenue avec IPs variées, croissance mémoire non bornée |
| 3 | Ajouter des tests de sécurité automatisés (scan de dépendances SAST) dans la CI | `.github/workflows/ci.yml` | Actuellement, seul GitHub Dependabot couvre les CVE connues ; pas de scan statique du code applicatif |
| 4 | Documenter explicitement la convention `@Profile("dev")` pour tout futur composant de seed/démo | `src/main/java/com/st4r4x/startup/DataSeeder.java` | Le pattern existe et fonctionne (`DataSeeder` est déjà gated), mais rien n'empêche un futur composant similaire d'oublier cette annotation |
| 5 | Uniformiser l'injection de dépendances : le projet mélange injection par constructeur (DAOs) et `@Autowired` sur champ (certains services) | `service/`, `controller/` | Cohérence de style — pas bloquant fonctionnellement, mais facilite la lecture pour un nouveau contributeur |

**Conventions de code respectées** : indentation 4 espaces, pas de `var`, imports explicites sans wildcard, logging via SLF4J (`LoggerFactory.getLogger`) et non `System.out.println`, une classe par fichier — conventions documentées dans les règles du projet et vérifiées sur l'ensemble du code source.

---

## 2. Stratégie de tests

### 2.1 Niveaux de tests

| Niveau | Outil | Périmètre | Exemple |
|---|---|---|---|
| **Unitaire** | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) | Services, DAOs (mockés), utilitaires, sécurité | `JwtUtilTest`, `ValidationUtilTest`, `AuthServiceTest`, `RestaurantServiceTest` |
| **Slice (contrôleur)** | `MockMvc` (`@WebMvcTest` ou setup standalone) | Comportement HTTP (codes de statut, JSON, sécurité) sans démarrer tout le contexte Spring | `RestaurantControllerSearchTest`, `AdminControllerTest`, `ReportControllerTest` |
| **Intégration** | JUnit 5 avec vraie instance MongoDB/PostgreSQL | Requêtes réelles, agrégations, contraintes de schéma | `RestaurantDAOIT`, `UserRepositoryIT` (suffixe `IT` = nécessite une base vivante sur `localhost`) |
| **Configuration** | JUnit 5 | Chargement de propriétés, beans Spring | `AppConfigTest`, `SecurityConfigTest`, `ElasticsearchConfigTest` |

Au total : **38 classes de test, 210 tests unitaires/slice + 14 tests d'intégration Testcontainers** couvrant DAO, service, contrôleur, sécurité, synchronisation (`sync/`), cache Redis et configuration. Trois classes ont été ajoutées au cours de la revue de certification pour combler des trous de couverture identifiés sur des composants à logique métier non triviale : `AnalyticsDAOIT` (les 9 requêtes d'agrégation MongoDB de `AnalyticsDAOImpl` n'avaient qu'une couverture indirecte via les mocks des tests de contrôleur), `JwtAuthenticationFilterTest` (extraction/validation JWT sur chaque requête, non testé malgré son rôle central en sécurité) et `AuthControllerTest` (codes de statut HTTP register/login/refresh, la logique métier étant déjà couverte côté `AuthServiceTest` mais pas la couche contrôleur).

### 2.2 Objectifs

- Garantir qu'aucune régression fonctionnelle ne passe en production sans échec de test détecté en CI.
- Isoler la logique métier de l'infrastructure (MongoDB, Redis, Elasticsearch) via des mocks pour les tests unitaires, afin de pouvoir les exécuter sans dépendance externe.
- Réserver les tests d'intégration (`*IT.java`) aux cas où le comportement réel de la base de données (agrégations MongoDB, contraintes JPA) ne peut pas être fiablement simulé par un mock.

### 2.3 Responsabilités

Projet solo : le candidat assume l'écriture, l'exécution et la maintenance de l'ensemble de la suite de tests. Le pipeline CI (`.github/workflows/ci.yml`) joue le rôle de garde-fou automatique avant tout merge sur `main`.

### 2.4 Critères d'entrée et de sortie

| Critère | Règle appliquée |
|---|---|
| **Entrée** (avant merge) | Le build (`mvn clean package`) doit compiler sans erreur ; toute nouvelle fonctionnalité doit inclure au moins un test unitaire couvrant le cas nominal |
| **Sortie** (avant déploiement) | `mvn test` doit passer intégralement en CI ; le seuil de couverture JaCoCo (voir 2.6) doit être respecté ; les tests d'intégration (`*IT`) sont exécutés dans un job CI séparé avec MongoDB/PostgreSQL réels |

### 2.5 Risques couverts par les tests

- **Régression silencieuse sur les endpoints publics** — couverte par les tests `MockMvc` de chaque contrôleur, qui vérifient les codes de statut et le format JSON.
- **Rupture de la logique de rate limiting** — `RateLimitFilterTest` vérifie explicitement le comportement de blocage (HTTP 429) après dépassement du quota.
- **Fuite de comptes de test en production** — `DataSeederTest` vérifie le comportement du composant sous profil `dev`.
- **Rupture d'agrégation MongoDB** (heatmap, at-risk, cuisine rankings) — couverte par `RestaurantDAOImplTest` (mocké) et `RestaurantDAOIT` (réel) en complément.

### 2.6 Environnement de tests et taux de couverture

- **Tests unitaires et slice** : exécutés en CI sans dépendance externe (mocks).
- **Tests d'intégration** (`*IT`) : nécessitent une instance MongoDB locale sur `localhost:27017` avec la base `newyork` peuplée, et PostgreSQL pour `UserRepositoryIT` — exécutés dans un job CI dédié (`integration-test`) avec services Docker éphémères.
- **Couverture de code** : mesurée par JaCoCo (`jacoco-maven-plugin` 0.8.14, choisi pour son support explicite de Java 25). Le seuil minimum imposé en CI est de **38 % d'instructions couvertes** (`INSTRUCTION` / `COVEREDRATIO`), fixé à partir d'une baseline mesurée de 43 % moins une marge de tolérance de 5 points pour absorber la dérive naturelle du code. Les packages `dto/`, `entity/`, `aggregation/` et `domain/` (POJOs sans logique) sont exclus du calcul car leur couverture n'est pas représentative de la qualité fonctionnelle.
- Un rapport de couverture HTML est publié en artefact CI et un commentaire automatique de couverture est ajouté sur chaque pull request (`PavanMudigonda/jacoco-reporter`).

**Objectif fixé vs atteint** : seuil CI fixé à 38 %, marge conservée sous la baseline réelle mesurée de 43 % — objectif tenu, avec une marge de sécurité volontaire plutôt qu'un seuil optimiste non tenable.

---

## 3. Procédures d'installation et déploiement continu

### 3.1 Installation locale (développement)

```bash
# 1. Cloner le dépôt et se placer dans le répertoire du projet
git clone <repo> && cd restaurant-analytics

# 2. Démarrer les services d'infrastructure (MongoDB, PostgreSQL, Redis)
docker compose up -d

# 3. Build (skip tests pour un build rapide en local)
mvn clean package -DskipTests

# 4. Lancer l'application
java -jar target/quickstart-app-1.0-SNAPSHOT.jar
# ou, en développement itératif :
mvn spring-boot:run
```

Healthchecks Docker Compose garantissent que MongoDB, PostgreSQL et Redis sont prêts avant le démarrage du conteneur applicatif (`docs/deployment.md`).

### 3.2 Plan de test associé à l'installation

| Étape | Vérification |
|---|---|
| Après `docker compose up -d` | `docker compose logs -f app` ne montre aucune exception de connexion aux services d'infrastructure |
| Après démarrage | `GET /api/restaurants/health` répond 200 |
| Après premier sync | `GET /api/restaurants/sync-status` indique un `completedAt` renseigné et un `recordCount` > 0 |
| Vérification sécurité | `GET /api/admin/cron/status` sans token → 401 ; avec token non-ADMIN → 403 |

### 3.3 Déploiement continu (production — Railway)

Le déploiement suit une logique de build automatique par détection de plateforme plutôt qu'un Dockerfile piloté manuellement :

1. **Détection du build** : `railway.toml` fixe explicitement la `buildCommand` Maven pour éviter que Railpack n'ajoute un profil Maven inexistant (`-Pproduction`).
2. **Version du runtime** : `.tool-versions` fixe Java 25 (Railpack utilise Java 21 par défaut, incompatible avec certains flags JVM du projet).
3. **Fichier de build alternatif** : `Dockerfile.ci` (renommé depuis `Dockerfile`) est ignoré par Railpack, qui privilégie l'auto-détection Maven — évite un conflit entre deux stratégies de build.
4. **Variables d'environnement** injectées par Railway au démarrage du conteneur : `MONGODB_URI`, `MONGODB_DATABASE`, `MONGODB_COLLECTION`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET` (à définir manuellement, minimum 32 caractères).
5. **Gestion mémoire conteneur** : `UseContainerSupport` + `MaxRAMPercentage=75.0` au démarrage de la JVM, pour éviter une croissance de heap non bornée sur les plans Railway à mémoire limitée.

Ce pipeline constitue une logique de déploiement continu : chaque push sur `main` déclenche un nouveau build et déploiement automatique côté Railway, précédé par la validation CI (build + tests + couverture) sur GitHub Actions.

### 3.4 Scripts d'installation

| Script / fichier | Rôle |
|---|---|
| `docker-compose.yml` | Orchestration des 4 services (app, mongodb, postgres, redis) avec healthchecks |
| `Dockerfile.ci` | Build multi-stage de l'image applicative pour la CI |
| `railway.toml` | Commande de build explicite pour l'hébergement Railway |
| `.tool-versions` | Version Java forcée (25) pour l'auto-détection Railpack |
| `.github/workflows/ci.yml` | Pipeline CI : build → tests unitaires/slice → tests d'intégration (services Docker) → couverture JaCoCo |

---

## 4. Plan de sécurité

Ce plan de sécurité porte la mise en œuvre technique. L'analyse de risque stratégique complète (cartographie, priorisation, indicateurs de suivi) est développée dans le dossier d'analyse des risques du système d'information (Bloc 4A.1) — ce document s'y réfère plutôt que de la dupliquer.

### 4.1 Mesures déjà en place

- **Authentification** : JWT stateless, access token 15 min / refresh token 7 jours (`JwtUtil`, `SecurityConfig`).
- **Autorisation par rôle** : `hasRole()` sur les endpoints sensibles, ordre des règles vérifié pour éviter les collisions de wildcard.
- **Rate limiting** : Bucket4j, deux paliers (`/api/auth/**` strict à 10 req/min, recherche/carte à 100 req/min).
- **Masquage des erreurs internes** : `ResponseUtil.errorResponse()` ne renvoie jamais de stack trace au client.
- **Isolation des comptes de démonstration** : `DataSeeder` gated par `@Profile("dev")`.
- **Traçabilité** : `AuditService` journalise les actions administrateur (changement de rôle, déclenchement de sync, etc.) en PostgreSQL, consultable via `GET /api/admin/audit`.
- **Veille sur les dépendances** : GitHub Dependabot actif sur le dépôt.

### 4.2 Mesures à mettre en œuvre (renvoi au plan de sécurisation du Bloc 4A.1)

Les actions prioritaires (secret JWT versionné, absence d'endpoint de suppression de compte RGPD, migration du stockage photo) sont détaillées avec ressources et priorités dans le dossier d'analyse des risques (Bloc 4A.1, section 4).

---

## 5. Plan de maintenance

### 5.1 Supervision automatisée

`CronScheduler` centralise l'ensemble des tâches planifiées (`@Scheduled`) :

| Tâche | Fréquence | Rôle |
|---|---|---|
| Cache warm-up (Redis) | 02:30 | Reconstruction du sorted-set de leaderboard après le sync nocturne |
| Réindexation Elasticsearch | 04:00 | Reconstruction quotidienne de l'index de recherche full-text |
| Sync NYC Open Data | 02:00 | Rafraîchissement des données restaurants depuis l'API NYC Open Data |

Statut consultable via `GET /api/admin/cron/status` (rôle ADMIN).

### 5.2 Procédure en cas d'incident

| Symptôme | Action |
|---|---|
| Sync NYC Open Data en échec | Consulter `GET /api/restaurants/sync-status` pour le détail de l'erreur ; déclencher un sync manuel via `POST /api/restaurants/refresh` (ADMIN) une fois la cause corrigée |
| Cache Redis désynchronisé | `POST /api/admin/rebuild-cache` (ADMIN) force la reconstruction complète |
| Panne applicative détectée | `GET /api/restaurants/health` en premier diagnostic ; logs consultables via `docker compose logs -f app` en local ou dashboard Railway en production |

### 5.3 Support utilisateur

- Un lien "Signaler un problème" présent dans le pied de page de toutes les pages du dashboard (bouton flottant + lien footer) ouvre directement un template d'issue GitHub — canal de support unique et centralisé, cohérent avec un projet porté par un développeur unique.
- La documentation utilisateur (utilisation des rôles CUSTOMER/CONTROLLER/ADMIN, fonctionnement des bookmarks et rapports) reste à formaliser en dehors du code — actuellement seule la documentation technique (`docs/`) existe.

### 5.4 Montée de version

Toute évolution suit le cycle documenté dans `docs/superpowers/` (spec → plan → exécution) et se conclut par une mise à jour de `CHANGELOG.md` et, si nécessaire, `docs/architecture.md` / `docs/api.md` — garantissant que la documentation ne dérive pas du code réel au fil des versions.
