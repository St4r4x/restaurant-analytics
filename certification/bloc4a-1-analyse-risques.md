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
| Secret de signature JWT (`jwt.secret`) | Secret cryptographique | Variable d'environnement `JWT_SECRET` (versionné en clair dans `application.properties` jusqu'au 2026-07-22, cf. R1) |
| Code source et historique Git | Propriété intellectuelle | GitHub |
| Infrastructure | Disponibilité du service | Railway (application ; Elasticsearch arrêté depuis juillet 2026), MongoDB Atlas (données restaurants), Supabase (PostgreSQL, région `eu-west-1`), Redis managé hors Railway |

**Parties prenantes** :
- **Candidat / développeur unique** — conception, développement, exploitation, sécurité (rôle DevSecOps de fait)
- **Utilisateurs finaux** (rôle `CUSTOMER`) — recherche, consultation, bookmarks
- **Contrôleurs sanitaires** (rôle `CONTROLLER`) — saisie de rapports d'inspection avec photos
- **Administrateur** (rôle `ADMIN`) — gestion des utilisateurs, supervision cron, purge cache
- **NYC Open Data (DOHMH)** — fournisseur de données externe, SLA informel
- **Railway, Supabase, MongoDB Atlas, Resend** — hébergeurs et prestataires techniques, sous-traitants au sens RGPD (traitent des données personnelles pour le compte du responsable de traitement : comptes en PostgreSQL chez Supabase, emails de réinitialisation via Resend)

**Méthode d'analyse** : approche par cartographie DICP simplifiée (Disponibilité / Intégrité / Confidentialité / Preuve), croisée avec une grille probabilité × impact, conforme à la logique de la méthode EBIOS RM adaptée à l'échelle d'un projet solo (pas de comité de risque formel, analyse portée uniquement par le développeur).

---

## 2. Cartographie des risques

Échelle : Probabilité (1 = rare → 4 = quasi certaine) × Impact (1 = négligeable → 4 = critique). Score = P × I. Seuil d'alerte ≥ 8.

| # | Risque | Actif touché | Critère DICP | P | I | Score | Statut (MAJ 2026-07-29, après pentest 4A.2) |
|---|---|---|---|---|---|---|---|
| R1 | Secret JWT versionné en clair dans `application.properties` | Sessions utilisateurs (tous rôles) | Confidentialité, Intégrité | 3 | 4 | **12** | **Traité** — PR feature/security-hardening (2026-07-22) |
| R2 | Absence de politique de rétention / suppression des données personnelles (pas de `DELETE /api/users/me`) | Comptes utilisateurs | Confidentialité (RGPD) | 3 | 3 | **9** | **Traité** — endpoint ajouté le 2026-07-22 ; le pentest a révélé qu'il échouait pour tout utilisateur ayant demandé un reset de mot de passe (FK `password_reset_tokens`) → corrigé le 2026-07-29 (PR #29), cf. §2.1 |
| R3 | Photos de rapports d'inspection stockées sur disque local, non chiffrées, sans contrôle d'accès dédié | Rapports d'inspection | Confidentialité | 2 | 3 | 6 | Partiellement traité (accès via API authentifiée, mais pas de séparation de stockage) |
| R4 | Scraping / déni de service applicatif sur les endpoints publics de recherche et de carte | Disponibilité du service | Disponibilité | 3 | 2 | 6 | **Traité** — `RateLimitFilter` couvre `/api/restaurants/search` et `/api/restaurants/map-points` |
| R5 | Comptes de test avec identifiants connus (`DataSeeder`) actifs en production | Comptes / accès admin | Confidentialité, Intégrité | 4 *(initialement 1)* | 4 | **16** *(initialement 4)* | **Traité** le 2026-07-29 — 3 comptes (dont `admin_test`, administrateur jusqu'au 2026-05-19) supprimés en production après le pentest ; initialement noté « Traité » à tort, cf. §2.1 |
| R6 | Exposition de stack traces / détails d'erreur interne dans les réponses API | Confidentialité (fuite d'architecture) | Confidentialité | 1 | 2 | 2 | **Traité** — `ResponseUtil.errorResponse()` masque systématiquement le détail des exceptions non métier |
| R7 | Panne du service NYC Open Data (fournisseur externe, SLA informel) | Disponibilité des données | Disponibilité | 2 | 2 | 4 | Non traité (pas de fallback documenté) |
| R8 | Absence de sauvegarde documentée pour MongoDB / PostgreSQL en cas de corruption | Intégrité, Disponibilité | Intégrité, Disponibilité | 2 | 3 | 6 | **Traité** — PR #8 (2026-07-23) |
| R9 | Rate limiting en mémoire (`ConcurrentHashMap` non borné) — fuite mémoire possible sous charge soutenue | Disponibilité (app elle-même) | Disponibilité | 1 | 2 | 2 | Connu, documenté en commentaire dans le code (`RateLimitFilter`), non corrigé |
| R10 | Absence de rotation planifiée des secrets (JWT, éventuels tokens NYC API) | Confidentialité | Confidentialité | 2 | 3 | 6 | Non traité |

**Risques prioritaires (score ≥ 8)** : R5 (comptes à identifiants connus, dont un ayant détenu les droits administrateur), R1 (secret JWT exposé) et R2 (non-conformité RGPD). R1 et R2 étaient identifiés dès l'analyse initiale ; R5 n'est passé au-dessus du seuil qu'après réévaluation par le pentest (§2.1).

### 2.1 Réévaluation après pentest (Bloc 4A.2, 2026-07-29)

Le test d'intrusion a invalidé deux statuts « Traité » de l'analyse initiale du 2026-07-22. Les deux erreurs ont la même nature : une mesure a été considérée comme suffisante sans vérifier son effet réel en production.

| Risque | Hypothèse de l'analyse initiale | Constat du pentest | Correction |
|---|---|---|---|
| R5 | `@Profile("dev")` empêche la création des comptes de test en production → P = 1 | Le garde-fou n'a été ajouté que le 2026-05-03 (commit `947c5f8`). Il empêche la *re-création* des comptes, mais pas la suppression de ceux insérés entre le 2026-03-29 et le 2026-05-03. 3 comptes sont restés connectables pendant environ 4 mois, dont `admin_test` avec les droits administrateur jusqu'au 2026-05-19 (cf. analyse forensic, Bloc 4A.2 §3) → P = 4, score 16 | Suppression des 3 comptes via `DELETE /api/users/me` ; identifiants revérifiés comme rejetés (`HTTP 400`) |
| R2 | L'ajout de `DELETE /api/users/me` suffit à couvrir le droit à l'effacement | La suppression échouait (`DataIntegrityViolationException`, FK `password_reset_tokens.user_id`) pour tout utilisateur ayant demandé une réinitialisation de mot de passe | Suppression en cascade des jetons de réinitialisation + test de non-régression (`deleteAccount_deletesPasswordResetTokensBeforeDeletingUser`), revérifié en production |

**Enseignement retenu** : un risque n'est désormais marqué « Traité » qu'après une vérification en production (preuve observable : requête rejetée, suppression effective), et non à la simple livraison du correctif dans le code. Le détail technique figure dans le dossier de pentest (Bloc 4A.2, §2.1 et §2.2).

---

## 3. Process d'audit

L'audit couvre l'ensemble du cycle de vie du système, en deux volets complémentaires. Les constats ci-dessous sont ceux de l'audit initial du 2026-07-22 ; la colonne « Suivi » indique leur état au 2026-10-01, après le plan de sécurisation (§4) et le pentest (Bloc 4A.2).

Déroulé de l'audit :

1. **Inventaire** des actifs et des flux (§1), à partir du code source, de `docs/architecture.md` et des consoles des hébergeurs.
2. **Audit organisationnel** : accès, secrets, traçabilité, dépendances (§3.1).
3. **Audit technique** : revue de la configuration de sécurité et du code (§3.2).
4. **Cotation** des risques identifiés (§2) puis **plan de sécurisation** priorisé (§4).
5. **Vérification par l'attaque** : pentest de l'instance de production (Bloc 4A.2), qui confirme ou invalide les statuts « Traité » (§2.1).
6. **Suivi** par indicateurs (§6) et réévaluation de la cartographie après chaque correctif.

### 3.1 Audit organisationnel

| Point de contrôle | Constat initial | Suivi (2026-10-01) |
|---|---|---|
| Gouvernance des accès | Un seul développeur détient tous les accès (Git, Railway, bases de données) — pas de séparation des rôles, risque de "single point of failure" humain | Inchangé (risque S1, Bloc 1.3) |
| Gestion des secrets | Secret JWT versionné dans `application.properties` ; autres secrets en variables d'environnement Railway — pas de gestionnaire de secrets dédié | Secret JWT sorti du dépôt (R1) ; secrets de sauvegarde gérés via Infisical |
| Traçabilité des actions admin | `AuditLogEntity` / `AuditService` en place depuis la v2.2.0 (2026-05-06) — les actions admin (changement de rôle, sync manuel) sont journalisées en PostgreSQL | Lacune révélée par l'analyse forensic (Bloc 4A.2, §3) : les **connexions** ne sont pas journalisées |
| Cycle de vie des dépendances | GitHub Dependabot activé — alertes automatiques sur CVE des dépendances Maven | Inchangé |
| Politique de mots de passe | BCrypt pour le hachage ; `ValidationUtil` impose 10 caractères minimum, une majuscule et un chiffre | Inchangé |

### 3.2 Audit technique

| Point de contrôle | Constat |
|---|---|
| Authentification | JWT stateless (access 15 min / refresh 7 jours), `SecurityConfig` avec `SessionCreationPolicy.STATELESS` — conforme aux bonnes pratiques REST |
| Autorisation | Contrôle par rôle (`hasRole()`) sur les endpoints sensibles (`/api/reports/**`, `/actuator/**`) ; ordre des règles vérifié pour éviter qu'un wildcard plus large ne masque une règle plus spécifique (ex. `/api/reports/stats` déclaré avant `/api/reports/**`) |
| Chiffrement en transit | HTTPS terminé par le proxy Railway ; pas de redirection forcée `X-Forwarded-Proto` documentée pour un déploiement hors Railway |
| Chiffrement au repos | Aucun chiffrement applicatif des données PostgreSQL/MongoDB au-delà de ce que fournit l'hébergeur |
| Gestion des erreurs | `ResponseUtil` centralise la réponse d'erreur et journalise le détail uniquement côté serveur |
| Rate limiting | Bucket4j, deux paliers (`/api/auth/**` strict, recherche/carte relâché) — étendu au-delà du seul endpoint d'authentification |
| Tests de sécurité automatisés | Scan de secrets (gitleaks) en CI ; pas d'analyse statique du code (SAST) ni de test dynamique (DAST) |
| Maintien en conditions opérationnelles | Tâches planifiées (`CronScheduler`, `SyncService`) consultables via `GET /api/admin/cron/status`, mais pas d'alerting automatique en cas d'échec de sync |
| Sécurité de la base PostgreSQL (Supabase) | Row-Level Security absente sur les 5 tables (signalé par l'advisor Supabase) — réactivée à chaque démarrage via `schema.sql` depuis le 2026-08-05 |
| Journalisation des authentifications | Aucune trace des connexions réussies ou échouées (pas d'action `LOGIN` dans `AuditAction`) — empêche toute analyse a posteriori d'un accès frauduleux |

---

## 4. Plan de sécurisation

| Priorité | Action | Ressources nécessaires | Risque(s) couvert(s) | État au 2026-10-01 |
|---|---|---|---|---|
| **1 (urgent)** | Supprimer en production les comptes à identifiants connus et ajouter un contrôle qui alerte si un compte `*_test` existe en base de production | 1-2h | R5 | Comptes supprimés le 2026-07-29 ; contrôle automatique à faire |
| **1 (urgent)** | Retirer `jwt.secret` de `application.properties`, le régénérer (64+ caractères), l'injecter uniquement via variable d'environnement Railway (`JWT_SECRET`) ; purger l'historique Git si possible | 1-2h, aucune dépendance externe | R1 | Fait le 2026-07-22 (valeur supprimée du fichier ; historique Git non purgé, d'où l'importance de la rotation R10) |
| **1 (urgent)** | Ajouter `DELETE /api/users/me` (suppression de compte + anonymisation des rapports liés) et documenter une politique de rétention dans une page de confidentialité | 1 jour de dev | R2 | Endpoint fait (corrigé le 2026-07-29) ; politique de confidentialité et de rétention **non publiée** |
| 2 | Journaliser les authentifications (succès et échecs, horodatage, IP tronquée) dans `audit_log` | 2-3h de dev | R5, forensic | À faire (lacune révélée par le pentest) |
| 2 | Migrer le stockage des photos de `app.uploads.dir` (disque local) vers un object storage (Cloudflare R2 ou S3-compatible), avec URLs signées à durée limitée et contrôle du type de fichier | Compte R2/S3, migration du code d'upload | R3 | À faire |
| 2 | Documenter une stratégie de sauvegarde régulière pour MongoDB et PostgreSQL (au minimum : export planifié + rétention 30 jours) | Configuration Railway ou script cron externe | R8 | Fait le 2026-07-23 (PostgreSQL ; MongoDB re-dérivable de la source) |
| 3 | Ajouter une politique de rotation des secrets (JWT au minimum annuelle, ou immédiate en cas de suspicion de fuite) | Procédure documentée, pas de dev | R10 | À faire |
| 3 | Remplacer les `ConcurrentHashMap` non bornées de `RateLimitFilter` par un cache à expiration (Guava `CacheBuilder.expireAfterAccess`) — déjà noté en commentaire dans le code source | 2-3h de dev | R9 | À faire |
| 4 | Ajouter un fallback "dernière donnée connue" avec horodatage visible en cas d'échec de sync NYC Open Data | 1 jour de dev | R7 | À faire |
| 4 | Ajouter une analyse statique du code (SAST, ex. CodeQL) en CI ; compléter Dependabot par un scan des dépendances (SCA, ex. OWASP Dependency-Check) | Configuration GitHub Actions | Vulnérabilités applicatives non détectées en revue | À faire |

---

## 5. Protections déjà développées (preuves concrètes)

Ces protections sont en place dans le code et en production au 2026-10-01 :

- **`DataSeeder`** gated par `@Profile("dev")` (`src/main/java/com/st4r4x/startup/DataSeeder.java:30`) depuis le 2026-05-03 — les comptes de test à identifiants connus ne sont plus créés en profil de production. Ce garde-fou ne purge pas les comptes déjà insérés : ceux créés avant cette date ont été supprimés le 2026-07-29 (cf. §2.1).
- **`RateLimitFilter`** (Bucket4j, `src/main/java/com/st4r4x/security/RateLimitFilter.java`) — deux paliers de limitation, étendus aux endpoints de recherche et de carte en plus de l'authentification.
- **`ResponseUtil.errorResponse()`** — masquage systématique des stack traces côté client, journalisation complète côté serveur uniquement.
- **JWT stateless + rôles** (`SecurityConfig`, `JwtAuthenticationFilter`) — pas de session serveur, contrôle d'accès par rôle sur les endpoints sensibles.
- **`AuditService` / `AuditLogEntity`** — traçabilité des actions administrateur en base PostgreSQL, consultable via `GET /api/admin/audit`.
- **GitHub Dependabot** — veille automatisée sur les CVE des dépendances (alimente aussi le dossier de veille, Bloc 1.1).
- **Cookies `HttpOnly` / `Secure` / `SameSite=Strict`** pour les jetons JWT (depuis la v2.4.0) — les jetons ne sont plus lisibles par JavaScript.
- **Échappement HTML** (`escapeHtml()`, `fragments/ux-utils.html`) des champs de rapport avant insertion dans le DOM — correctif du XSS trouvé au pentest.
- **Row-Level Security PostgreSQL** réactivée à chaque démarrage (`schema.sql`, depuis le 2026-08-05) — ferme l'accès aux tables via l'API publique de Supabase.
- **Sauvegardes PostgreSQL quotidiennes chiffrées** (GPG AES256, `.github/workflows/backup-postgres.yml`, rétention 30 jours).

---

## 6. Indicateurs de suivi

| Indicateur | Source | Fréquence | Seuil d'alerte |
|---|---|---|---|
| Nombre de requêtes bloquées par `RateLimitFilter` (HTTP 429) | Logs applicatifs | Hebdomadaire | Pic anormal (> 3× la moyenne glissante) = signal de scraping actif |
| Nombre de CVE ouvertes non résolues (Dependabot) | GitHub Security tab | À chaque alerte | Toute CVE critique/haute = traitement sous 7 jours |
| Résultat du dernier sync NYC Open Data (`GET /api/restaurants/sync-status`) | Endpoint applicatif | Quotidien (sync à 02:00) | Échec de sync depuis > 48h |
| Comptes à identifiants connus (`*_test`) présents en base de production | Requête SQL sur `users` | Mensuel, et après chaque déploiement | Toute valeur > 0 = suppression immédiate |
| Échecs de connexion par compte (une fois la journalisation des authentifications en place) | `audit_log` | Hebdomadaire | Rafale d'échecs sur un même compte = tentative de force brute |
| Nombre d'actions admin journalisées (`AuditService`) | PostgreSQL `audit_log` | Mensuel | Volume anormal sur un compte admin (indice de compromission) |
| Âge du secret JWT en production | Procédure manuelle (à formaliser) | Annuel | > 12 mois sans rotation |
| Job CI `secrets` (gitleaks) en échec | GitHub Actions | À chaque push/PR | Tout échec = secret détecté, bloquer le merge |
| Job CI `backup-postgres` en échec | GitHub Actions | Quotidien (03:17 UTC) | Tout échec = vérifier la connectivité Supabase (pooler Supavisor) et relancer manuellement (`gh workflow run backup-postgres.yml`) |

Ces indicateurs sont exploitables pour la prise de décision : ils permettent d'objectiver si le plan de sécurisation (section 4) produit un effet mesurable (ex. baisse du nombre de 429 après ajustement des seuils, absence de CVE critique ouverte plus de 7 jours).

---

## Renvoi croisé

Le plan de sécurité opérationnel détaillé (niveau code, configuration) est développé dans le dossier technique de l'application (Bloc 3.2) pour éviter la duplication — ce dossier (4A.1) porte l'analyse de risque et la décision stratégique, le dossier technique porte la mise en œuvre. La vérification par l'attaque et l'analyse forensic figurent dans le dossier de pentest (Bloc 4A.2).
