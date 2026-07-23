# Dossier d'analyse des risques du système d'information

**Certification** : RNCP niveau 7 — Expert en informatique et système d'information (3W Academy)
**Bloc** : 4A — Manager la cybersécurité des systèmes, applications et bases de données
**Livrable** : Dossier d'analyse des risques SI (évaluation individuelle)
**Candidat** : Arnaud Thery
**Support technique** : `restaurant-analytics`

---

## 1. Contexte et parties prenantes

`restaurant-analytics` est une API REST + dashboard web exposant des données publiques d'inspection sanitaire des restaurants de New York (NYC Open Data, CC0), enrichies d'une couche applicative (comptes utilisateurs, bookmarks, rapports d'inspection internes, panneau d'administration).

**Actifs du système d'information** :

| Actif | Nature | Localisation |
|---|---|---|
| Données NYC Open Data | Publiques, non sensibles | MongoDB (`newyork.restaurants`) |
| Comptes utilisateurs (username, email, hash de mot de passe) | Données personnelles (RGPD) | PostgreSQL |
| Rapports d'inspection internes + photos jointes | Données métier + potentiellement personnelles (visages, écritures) | PostgreSQL + disque local (`app.uploads.dir`) |
| Tokens JWT (access/refresh) | Secrets de session | Non persistés côté serveur (stateless) |
| Secret de signature JWT (`jwt.secret`) | Secret cryptographique | `application.properties`, versionné dans le dépôt Git |
| Code source et historique Git | Propriété intellectuelle | GitHub |
| Infrastructure (Railway : app, MongoDB, PostgreSQL, Redis, Elasticsearch) | Disponibilité du service | Railway (hébergement géré) |

**Parties prenantes** :
- **Candidat / développeur unique** — conception, développement, exploitation, sécurité (rôle DevSecOps de fait)
- **Utilisateurs finaux** (rôle `CUSTOMER`) — recherche, consultation, bookmarks
- **Contrôleurs sanitaires** (rôle `CONTROLLER`) — saisie de rapports d'inspection avec photos
- **Administrateur** (rôle `ADMIN`) — gestion des utilisateurs, supervision cron, purge cache
- **NYC Open Data (DOHMH)** — fournisseur de données externe, SLA informel
- **Railway** — hébergeur, sous-traitant au sens RGPD (traite des données personnelles pour compte du responsable de traitement)

**Méthode d'analyse** : approche par cartographie DICP simplifiée (Disponibilité / Intégrité / Confidentialité / Preuve), croisée avec une grille probabilité × impact, conforme à la logique de la méthode EBIOS RM adaptée à l'échelle d'un projet solo (pas de comité de risque formel, analyse portée uniquement par le développeur).

---

## 2. Cartographie des risques

Échelle : Probabilité (1 = rare → 4 = quasi certaine) × Impact (1 = négligeable → 4 = critique). Score = P × I. Seuil d'alerte ≥ 8.

| # | Risque | Actif touché | Critère DICP | P | I | Score | Statut au 2026-07-22 |
|---|---|---|---|---|---|---|---|
| R1 | Secret JWT versionné en clair dans `application.properties` | Sessions utilisateurs (tous rôles) | Confidentialité, Intégrité | 3 | 4 | **12** | **Traité** — PR feature/security-hardening (2026-07-22) |
| R2 | Absence de politique de rétention / suppression des données personnelles (pas de `DELETE /api/users/me`) | Comptes utilisateurs | Confidentialité (RGPD) | 3 | 3 | **9** | **Traité** — PR feature/security-hardening (2026-07-22) |
| R3 | Photos de rapports d'inspection stockées sur disque local, non chiffrées, sans contrôle d'accès dédié | Rapports d'inspection | Confidentialité | 2 | 3 | 6 | Partiellement traité (accès via API authentifiée, mais pas de séparation de stockage) |
| R4 | Scraping / déni de service applicatif sur les endpoints publics de recherche et de carte | Disponibilité du service | Disponibilité | 3 | 2 | 6 | **Traité** — `RateLimitFilter` couvre `/api/restaurants/search` et `/api/restaurants/map-points` |
| R5 | Comptes de test avec identifiants connus (`DataSeeder`) actifs en production | Comptes / accès admin | Confidentialité, Intégrité | 1 | 4 | 4 | **Traité** — `@Profile("dev")`, ne s'exécute pas en production |
| R6 | Exposition de stack traces / détails d'erreur interne dans les réponses API | Confidentialité (fuite d'architecture) | Confidentialité | 1 | 2 | 2 | **Traité** — `ResponseUtil.errorResponse()` masque systématiquement le détail des exceptions non métier |
| R7 | Panne du service NYC Open Data (fournisseur externe, SLA informel) | Disponibilité des données | Disponibilité | 2 | 2 | 4 | Non traité (pas de fallback documenté) |
| R8 | Absence de sauvegarde documentée pour MongoDB / PostgreSQL en cas de corruption | Intégrité, Disponibilité | Intégrité, Disponibilité | 2 | 3 | 6 | Non traité |
| R9 | Rate limiting en mémoire (`ConcurrentHashMap` non borné) — fuite mémoire possible sous charge soutenue | Disponibilité (app elle-même) | Disponibilité | 1 | 2 | 2 | Connu, documenté en commentaire dans le code (`RateLimitFilter`), non corrigé |
| R10 | Absence de rotation planifiée des secrets (JWT, éventuels tokens NYC API) | Confidentialité | Confidentialité | 2 | 3 | 6 | Non traité |

**Risques prioritaires (score ≥ 8)** : R1 (secret JWT exposé) et R2 (non-conformité RGPD) constituent les deux risques critiques à traiter avant toute exposition élargie du service (public plus large, commercialisation).

---

## 3. Process d'audit

L'audit couvre l'ensemble du cycle de vie du système, en deux volets complémentaires.

### 3.1 Audit organisationnel

| Point de contrôle | Constat |
|---|---|
| Gouvernance des accès | Un seul développeur détient tous les accès (Git, Railway, bases de données) — pas de séparation des rôles, risque de "single point of failure" humain |
| Gestion des secrets | Secrets stockés dans `application.properties` versionné (JWT) ou en variables d'environnement Railway (Mongo/Redis host) — pas de gestionnaire de secrets dédié |
| Traçabilité des actions admin | `AuditLogEntity` / `AuditService` en place depuis la v2.2.0 — les actions admin (changement de rôle, sync manuel) sont journalisées en PostgreSQL |
| Cycle de vie des dépendances | GitHub Dependabot activé — alertes automatiques sur CVE des dépendances Maven |
| Politique de mots de passe | BCrypt pour le hachage, mais pas de politique de complexité imposée côté serveur au-delà de la validation `ValidationUtil` |

### 3.2 Audit technique

| Point de contrôle | Constat |
|---|---|
| Authentification | JWT stateless (access 15 min / refresh 7 jours), `SecurityConfig` avec `SessionCreationPolicy.STATELESS` — conforme aux bonnes pratiques REST |
| Autorisation | Contrôle par rôle (`hasRole()`) sur les endpoints sensibles (`/api/reports/**`, `/actuator/**`) ; ordre des règles vérifié pour éviter qu'un wildcard plus large ne masque une règle plus spécifique (ex. `/api/reports/stats` déclaré avant `/api/reports/**`) |
| Chiffrement en transit | HTTPS terminé par le proxy Railway ; pas de redirection forcée `X-Forwarded-Proto` documentée pour un déploiement hors Railway |
| Chiffrement au repos | Aucun chiffrement applicatif des données PostgreSQL/MongoDB au-delà de ce que fournit l'hébergeur |
| Gestion des erreurs | `ResponseUtil` centralise la réponse d'erreur et journalise le détail uniquement côté serveur |
| Rate limiting | Bucket4j, deux paliers (`/api/auth/**` strict, recherche/carte relâché) — étendu au-delà du seul endpoint d'authentification |
| Tests de sécurité automatisés | Aucun test de sécurité dédié identifié (pas de scan SAST/DAST dans la CI actuelle) |
| Maintien en conditions opérationnelles | Cron jobs supervisés via `CronScheduler` + `GET /api/admin/cron/status`, mais pas d'alerting automatique en cas d'échec de sync |

---

## 4. Plan de sécurisation

| Priorité | Action | Ressources nécessaires | Risque(s) couvert(s) |
|---|---|---|---|
| **1 (urgent)** | Retirer `jwt.secret` de `application.properties`, le régénérer (64+ caractères), l'injecter uniquement via variable d'environnement Railway (`JWT_SECRET`) ; purger l'historique Git si possible | 1-2h, aucune dépendance externe | R1 |
| **1 (urgent)** | Ajouter `DELETE /api/users/me` (suppression de compte + anonymisation des rapports liés) et documenter une politique de rétention dans une page privacy | 1 jour de dev | R2 |
| 2 | Migrer le stockage des photos de `app.uploads.dir` (disque local) vers un object storage (Cloudflare R2 ou S3-compatible), avec URLs signées à durée limitée | Compte R2/S3, migration du code d'upload | R3 |
| 2 | Documenter une stratégie de sauvegarde régulière pour MongoDB et PostgreSQL (au minimum : export planifié + rétention 30 jours) | Configuration Railway ou script cron externe | R8 |
| 3 | Ajouter une politique de rotation des secrets (JWT au minimum annuelle, ou immédiate en cas de suspicion de fuite) | Procédure documentée, pas de dev | R1, R10 |
| 3 | Remplacer les `ConcurrentHashMap` non bornées de `RateLimitFilter` par un cache à expiration (Guava `CacheBuilder.expireAfterAccess`) — déjà noté en commentaire dans le code source | 2-3h de dev | R9 |
| 4 | Ajouter un fallback "dernière donnée connue" avec horodatage visible en cas d'échec de sync NYC Open Data | 1 jour de dev | R7 |
| 4 | Introduire un scan de dépendances (OWASP Dependency-Check ou équivalent) dans la CI | Configuration GitHub Actions | Réduction du risque de dérive sur les CVE non couvertes par Dependabot |

---

## 5. Protections déjà développées (preuves concrètes)

Ces protections sont déjà en place dans le code au moment de la rédaction de ce dossier — elles constituent la preuve que le plan de sécurisation ne part pas de zéro :

- **`DataSeeder`** gated par `@Profile("dev")` (`src/main/java/com/st4r4x/startup/DataSeeder.java:30`) — les comptes de test à identifiants connus ne sont jamais créés en profil de production.
- **`RateLimitFilter`** (Bucket4j, `src/main/java/com/st4r4x/security/RateLimitFilter.java`) — deux paliers de limitation, étendus aux endpoints de recherche et de carte en plus de l'authentification.
- **`ResponseUtil.errorResponse()`** — masquage systématique des stack traces côté client, journalisation complète côté serveur uniquement.
- **JWT stateless + rôles** (`SecurityConfig`, `JwtAuthenticationFilter`) — pas de session serveur, contrôle d'accès par rôle sur les endpoints sensibles.
- **`AuditService` / `AuditLogEntity`** — traçabilité des actions administrateur en base PostgreSQL, consultable via `GET /api/admin/audit`.
- **GitHub Dependabot** — veille automatisée sur les CVE des dépendances (alimente aussi le dossier de veille, Bloc 1.1).

---

## 6. Indicateurs de suivi

| Indicateur | Source | Fréquence | Seuil d'alerte |
|---|---|---|---|
| Nombre de requêtes bloquées par `RateLimitFilter` (HTTP 429) | Logs applicatifs | Hebdomadaire | Pic anormal (> 3× la moyenne glissante) = signal de scraping actif |
| Nombre de CVE ouvertes non résolues (Dependabot) | GitHub Security tab | À chaque alerte | Toute CVE critique/haute = traitement sous 7 jours |
| Résultat du dernier sync NYC Open Data (`GET /api/restaurants/sync-status`) | Endpoint applicatif | Quotidien (cron 02:30) | Échec de sync depuis > 48h |
| Nombre d'actions admin journalisées (`AuditService`) | PostgreSQL `audit_log` | Mensuel | Volume anormal sur un compte admin (indice de compromission) |
| Âge du secret JWT en production | Procédure manuelle (à formaliser) | Annuel | > 12 mois sans rotation |
| Job CI `secrets` (gitleaks) en échec | GitHub Actions | À chaque push/PR | Tout échec = secret détecté, bloquer le merge |

Ces indicateurs sont exploitables pour la prise de décision : ils permettent d'objectiver si le plan de sécurisation (section 4) produit un effet mesurable (ex. baisse du nombre de 429 après ajustement des seuils, absence de CVE critique ouverte plus de 7 jours).

---

## Renvoi croisé

Le plan de sécurité opérationnel détaillé (niveau code, configuration) est développé dans le dossier technique de l'application (Bloc 3.2) pour éviter la duplication — ce dossier (4A.1) porte l'analyse de risque et la décision stratégique, le dossier technique porte la mise en œuvre.
