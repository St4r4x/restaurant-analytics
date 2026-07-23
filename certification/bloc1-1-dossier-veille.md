# Dossier de veille technologique

**Certification** : RNCP niveau 7 — Expert en informatique et système d'information (3W Academy)
**Bloc** : 1 — Analyser et définir une stratégie de systèmes d'information
**Livrable** : Dossier de veille (évaluation individuelle)
**Candidat** : Arnaud Thery
**Support technique** : `restaurant-analytics` (API REST + dashboard Spring Boot / MongoDB / Redis / Elasticsearch, déployé sur Railway)
**Période de veille couverte** : février 2026 – juillet 2026

---

## 1. Méthode de collecte

La veille a été menée selon deux logiques complémentaires, choisies en fonction du type de source :

### Collecte en mode *pull*

Je vais chercher activement l'information sur des canaux que je consulte régulièrement :

- **Documentation officielle** des technologies du projet (Spring Boot, MongoDB, Railway, OWASP) — consultée à chaque montée de version ou avant une décision d'architecture (ex. passage de Java 21 à Java 25, choix Elasticsearch vs `$text` MongoDB).
- **Recherche ciblée** (moteurs de recherche, GitHub Issues/Discussions, Stack Overflow) déclenchée par un problème concret rencontré sur le projet (ex. incompatibilité `--sun-misc-unsafe-memory-access=allow` avec Java 21 sur Railway Railpack).
- **Dépôts de référence** (OWASP Top 10, OWASP API Security Top 10) consultés ponctuellement pour vérifier la conformité du projet.

### Collecte en mode *push*

Je reçois l'information sans la solliciter activement, via des mécanismes d'abonnement :

- **Newsletters techniques** (GitHub Releases notifications sur les dépôts Spring Boot, MongoDB Java Driver, Elasticsearch — abonnement "Watch → Releases only").
- **Flux RSS/agrégateurs** (Hacker News, InfoQ) pour la détection de signaux faibles sur les évolutions réglementaires et l'IA agentique.
- **Alertes de sécurité** — GitHub Dependabot activé sur le dépôt `restaurant-analytics`, qui pousse une notification dès qu'une CVE est publiée sur une dépendance du `pom.xml`.

Le mode *push* (Dependabot notamment) est privilégié pour tout ce qui touche à la sécurité des dépendances, car il garantit qu'aucune alerte critique n'est manquée par absence de recherche active. Le mode *pull* est réservé à l'approfondissement d'un sujet une fois le signal détecté.

---

## 2. Fiabilité des sources et exclusion des données non pertinentes

Chaque source a été classée selon trois niveaux de fiabilité avant intégration dans ce dossier :

| Niveau | Type de source | Exemple | Traitement |
|---|---|---|---|
| **Haute** | Documentation officielle, spécifications, dépôt source du projet | `spring.io/blog`, OWASP.org, RFC | Retenue sans réserve |
| **Moyenne** | Articles techniques signés, retours d'expérience identifiables | Blogs d'ingénieurs (Baeldung, InfoQ), talks de conférence | Retenue si recoupée avec une source haute |
| **Faible / exclue** | Contenu non signé, généré sans expertise vérifiable, ou obsolète (> 2 versions majeures) | Forums anonymes non modérés, articles marketing sans données, tutoriels non datés | **Exclue** |

**Exemples concrets d'exclusion appliqués à ce dossier** :
- Plusieurs articles de blog affirmant que "Spring Boot n'a pas besoin de conteneurisation" ont été écartés : datés d'avant l'ère cloud-native généralisée, sans mise à jour, contredits par la documentation officielle Spring Boot sur le support natif des buildpacks.
- Des posts de forums non sourcés sur les "limites de MongoDB en production" ont été exclus faute de bench reproductible ; seules les données du blog technique MongoDB (avec méthodologie publiée) ont été retenues.
- Les contenus produits par IA générative sans revue humaine identifiable (détectés par l'absence de sources citées et un ton générique) ont été systématiquement écartés de ce dossier, quel que soit le sujet — risque de désinformation en cascade sur un domaine encore instable (agentic AI).

---

## 3. Thématiques suivies

Quatre thématiques ont été retenues parce qu'elles influent directement sur l'évolution du support technique `restaurant-analytics` et, plus largement, sur le métier d'expert en informatique et systèmes d'information.

### 3.1 Sécurité des API REST

**Ce qui a été observé** : l'OWASP API Security Top 10 (édition 2023, toujours la référence en 2026) place en tête les failles d'autorisation au niveau objet (*Broken Object Level Authorization*) et l'exposition excessive de données (*Excessive Data Exposure*) — deux catégories directement pertinentes pour une API exposant des données publiques (NYC Open Data) combinées à des données utilisateur sensibles (bookmarks, rapports d'inspection).

**Gain identifié** : le projet utilise déjà JWT (access 15 min / refresh 7 jours) et un filtre de rate limiting (`RateLimitFilter`), mais celui-ci ne couvre que `/api/auth/**`. La veille a permis d'identifier que les endpoints de recherche et de carte (`/api/restaurants/search`, `/api/analytics/heatmap`) sont les cibles les plus probables de scraping massif faute de limitation, un point déjà noté dans `docs/commercialisation.md` mais confirmé par la littérature comme un risque de premier plan.

**Risque si ignoré** : exposition à des attaques par déni de service applicatif à faible coût (pas besoin d'exploit sophistiqué, juste l'absence de limite).

### 3.2 Cloud-native et déploiement continu

**Ce qui a été observé** : la bascule de l'écosystème Java vers des runtimes cloud-native s'accélère — buildpacks (Railpack, Cloud Native Buildpacks), gestion automatique de la mémoire conteneurisée (`-XX:+UseContainerSupport`, `-XX:MaxRAMPercentage`), et abandon progressif des Dockerfiles écrits à la main au profit d'une détection automatique du build par la plateforme.

**Gain identifié** : cette veille a directement motivé deux changements déjà appliqués au projet — le renommage `Dockerfile` → `Dockerfile.ci` pour laisser Railway/Railpack détecter Maven automatiquement, et le cap mémoire JVM à 75 % de la RAM du conteneur pour éviter la croissance de heap non bornée (cf. CHANGELOG v2.2.2).

**Risque si ignoré** : dépendance à une configuration Docker manuelle fragile, qui casse silencieusement à chaque changement de version de la plateforme d'hébergement.

### 3.3 Réglementation IA et RGPD

**Ce qui a été observé** : le cadre réglementaire européen (RGPD déjà en vigueur, AI Act en cours de déploiement par paliers jusqu'en 2027) impose des obligations croissantes sur la traçabilité des traitements de données personnelles et, pour les systèmes utilisant de l'IA, sur la transparence des décisions automatisées.

**Gain identifié** : le projet stocke des données personnelles (email, username en PostgreSQL) sans politique de rétention documentée ni endpoint de suppression de compte — un manquement déjà identifié dans `docs/commercialisation.md` mais qui devient plus urgent à mesure que les autorités de contrôle intensifient les contrôles sur les petites structures, pas seulement les grands groupes.

**Risque si ignoré** : non-conformité RGPD bloquante pour toute commercialisation, même à faible échelle (le CCPA californien et le RGPD s'appliquent dès la première collecte d'utilisateurs, sans seuil de taille).

### 3.4 IA agentique (agentic AI)

**Ce qui a été observé** : généralisation des agents de développement autonomes (assistants de code capables d'exécuter des actions multi-étapes — lire, modifier, tester, committer) au-delà de la simple complétion de code. Cette évolution redéfinit une partie du métier d'expert SI : la valeur se déplace de l'écriture de code vers la définition du cadre (spécifications, revue, sécurité) dans lequel l'agent opère.

**Gain identifié** : le projet `restaurant-analytics` est lui-même développé avec ce type d'outillage (workflow Superpowers : brainstorm → plan → exécution documentés dans `docs/superpowers/`), ce qui constitue une preuve concrète d'adaptation à cette évolution plutôt qu'une simple observation théorique.

**Risque si ignoré** : perte de compétitivité pour un expert SI qui continuerait à cadrer les projets sans intégrer cet outillage — mais aussi risque inverse (déjà documenté par la littérature) de dérive qualité si l'agent n'est pas encadré par une revue humaine systématique.

---

## 4. Recommandations

| # | Recommandation | Thématique source | Priorité |
|---|---|---|---|
| 1 | Étendre le rate limiting existant (`RateLimitFilter`) aux endpoints `/api/restaurants/search` et `/api/analytics/heatmap` | Sécurité API | Haute |
| 2 | Documenter une politique de rétention des données personnelles et ajouter un endpoint `DELETE /api/users/me` | RGPD | Haute |
| 3 | Poursuivre la migration vers une configuration cloud-native pilotée par la plateforme (buildpacks) plutôt que des Dockerfiles maintenus à la main, pour toute nouvelle brique d'infrastructure | Cloud-native | Moyenne |
| 4 | Maintenir une revue humaine systématique sur tout code produit par un agent IA avant merge — déjà en place via le workflow Superpowers, à formaliser dans le dossier technique (Bloc 3.2) | IA agentique | Moyenne |

---

## 5. Sources consultées (sélection)

- OWASP API Security Top 10 — owasp.org
- Spring Boot Reference Documentation — docs.spring.io
- MongoDB Engineering Blog — mongodb.com/blog
- Railway Documentation (Railpack) — docs.railway.com
- CNIL — Guides RGPD pour développeurs — cnil.fr
- Règlement (UE) 2024/1689 (AI Act) — texte consolidé
- GitHub Dependabot Security Advisories — dépôt `restaurant-analytics`
