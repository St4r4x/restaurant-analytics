# Plan de changement

**Certification** : RNCP niveau 7 — Expert en informatique et système d'information (3W Academy)
**Bloc** : 2 — Piloter et manager les projets informatiques
**Livrable** : Plan de changement
**Candidat** : Arnaud Thery
**Projet** : `restaurant-analytics`

> Comme pour les Blocs 1.3, 2.1-2.3, ce document est traité en solo sur la base de `restaurant-analytics`, alors que le référentiel prévoit une évaluation en groupe de 3 — choix assumé, documenté dans `CHECKLIST.md`, en attendant une réponse d'Aflokkat sur ce point. Un contexte solo ne supprime pas la notion de parties prenantes : il change simplement leur nature (pas de collègues, mais des dépendances externes réelles) — c'est ce que ce document rend explicite plutôt que de le contourner.

---

## 1. Parties prenantes

### 1.1 Équipe projet

Une seule personne : le candidat, qui cumule tous les rôles habituellement répartis dans une équipe.

| Casquette | Responsabilité |
|---|---|
| Product Owner | Priorisation des fonctionnalités, arbitrages de périmètre (ex. arrêt d'Elasticsearch) |
| Développeur | Implémentation, tests |
| Ops / SRE | Déploiement, supervision Railway, gestion des coûts d'infrastructure |
| Référent sécurité | Durcissement JWT/RGPD, revue des dépendances, scan de secrets |
| QA | Écriture et maintenance de la suite de tests, revue des PR avant fusion |

### 1.2 Parties prenantes externes (distinctes de l'équipe projet)

```mermaid
flowchart LR
    Dev[Candidat — équipe projet]
    Users[Utilisateurs finaux<br/>anonyme / client / contrôleur / admin]
    NYC[NYC Open Data<br/>fournisseur de données]
    Infra[Railway / MongoDB Atlas /<br/>Supabase / Resend]
    Jury[Jury de certification<br/>3W Academy / Aflokkat]

    Dev -->|livre des fonctionnalités| Users
    Users -->|retours, rapports d'hygiène| Dev
    NYC -->|données d'inspection| Dev
    Dev -->|dépend de| Infra
    Dev -->|preuves techniques| Jury
    Jury -->|critères d'évaluation| Dev
```

| Partie prenante | Rôle vis-à-vis du projet | Attente principale |
|---|---|---|
| Utilisateurs finaux (4 profils) | Consommateurs du service | Disponibilité, exactitude des données, sécurité des comptes |
| NYC Open Data | Fournisseur de données source | Aucune, relation à sens unique (API publique gratuite) |
| Railway / MongoDB Atlas / Supabase / Resend | Fournisseurs d'infrastructure | Paiement des factures, respect des quotas d'usage |
| Jury de certification (3W Academy / Aflokkat) | Évaluateur externe | Preuves techniques vérifiables, conformité au référentiel |

---

## 2. Types de messages selon les parties prenantes

| Partie prenante | Type de message | Canal |
|---|---|---|
| Utilisateurs finaux | Confirmation d'action (bookmark, rapport soumis), erreurs | Notifications toast in-app (`showToast()`), emails transactionnels (Resend) |
| Utilisateurs finaux | Nouveauté / évolution du service | Numéro de version affiché en pied de page (`v2.4.1`), `CHANGELOG.md` public sur GitHub |
| NYC Open Data | Aucun message sortant — uniquement des requêtes de lecture programmées | — |
| Fournisseurs d'infrastructure | Alertes d'incident, dépassement de quota | Dashboards Railway/Supabase, emails automatiques des fournisseurs |
| Jury de certification | Preuves techniques structurées | Dossiers `certification/*.md`, code source public sur GitHub, démo live |
| Futur mainteneur (soi-même après une pause, ou un tiers hypothétique) | Contexte de reprise en main | `CLAUDE.md`, `docs/`, `CHANGELOG.md` — approche déjà identifiée comme mesure face au risque S1 (Bloc 1.3) |

---

## 3. Plan de communication

Le projet n'a pas de réunions au sens classique (pas d'équipe à synchroniser), mais des points de communication réels, outillés :

| Outil | Usage |
|---|---|
| `CHANGELOG.md` | Historique public et daté de chaque changement livré — sert de compte rendu d'avancement asynchrone |
| Pull requests GitHub | Chaque fonctionnalité documentée (description, plan de test) avant fusion — équivalent d'un point de revue, même en solo (auto-revue disciplinée par la CI obligatoire) |
| `docs/superpowers/specs/` et `plans/` | Décisions de conception argumentées, consultables a posteriori |
| Lien "Signaler un problème" (bouton flottant + pied de page) | Canal de retour utilisateur direct vers une issue GitHub |
| Swagger UI (`/swagger-ui.html`) | Communication technique de l'API vers un développeur tiers potentiel |

---

## 4. Plannings

Planning macro déjà établi au Bloc 2.3 (Gantt des 7 phases, `bloc2-3-dossier-planification-bilan.md`, section 1.1). Ce plan de changement s'appuie dessus sans le dupliquer : chaque nouvelle fonctionnalité suit le cycle court spec → plan → implémentation → revue CI → documentation (quelques jours à deux semaines par fonctionnalité, cf. historique `CHANGELOG.md`), imbriqué dans les phases macro.

---

## 5. Indicateurs d'avancement

| Indicateur | Source | Fréquence |
|---|---|---|
| Version applicative (`app.semver`) | `application.properties`, affichée en pied de page et via `GET /api/restaurants/health` | À chaque release |
| Statut CI (build, tests, couverture) | GitHub Actions, badge de couverture JaCoCo sur chaque PR | À chaque push/PR |
| Entrées `CHANGELOG.md` | Dépôt Git | À chaque fonctionnalité livrée |
| Coût d'infrastructure observé | Métriques Railway (mémoire, services actifs) | Revue ponctuelle, formalisation trimestrielle préconisée (Bloc 1.3, §5) |
| Couverture de code | Rapport JaCoCo (seuil CI 38 %, mesuré 50,1 % au 01/10/2026) | À chaque build |

---

## 6. Plan de formation et de valorisation des connaissances

### 6.1 Montée en compétences (auto-portée)

Sans équipe à former, la montée en compétences est individuelle et documentée plutôt qu'implicite :

- **Changement de méthode en cours de projet** — passage du scaffolding GSD au workflow Superpowers (mai 2026, détaillé au Bloc 2.3) : une compétence méthodologique acquise et adoptée durablement, pas juste testée puis oubliée.
- **Montée en compétence sécurité** — accumulation progressive des mesures du Bloc 4A.1 (JWT, RGPD, scan de secrets, sauvegardes chiffrées), chacune documentée avec sa justification dans `CHANGELOG.md` plutôt qu'appliquée sans trace.
- **Veille technique continue** — le Bloc 1.1 (dossier de veille) constitue lui-même un livrable de cette activité de formation continue.

### 6.2 Valorisation des connaissances

| Support | Rôle de valorisation |
|---|---|
| `docs/` (architecture, api, ui, configuration, deployment) | Connaissance technique transmissible à un tiers, tenue à jour à chaque fonctionnalité |
| `docs/superpowers/specs/` et `plans/` | Justification des choix de conception, réutilisable pour des décisions futures similaires |
| `certification/` | Formalisation académique des compétences acquises — ce dossier lui-même en est un exemple |
| `CLAUDE.md` | Contexte condensé pour une reprise de projet rapide, y compris par le candidat lui-même après une pause |

---

## 7. Transposition en contexte d'équipe

Le projet est mené seul, mais le référentiel évalue la capacité à piloter une équipe. Cette section décrit le dispositif qui serait mis en place si le projet passait à une équipe de trois personnes (hypothèse : un développeur back-end, un développeur front-end, le candidat en chef de projet et référent sécurité).

### 7.1 Organigramme cible

```mermaid
flowchart TB
    Sponsor[Commanditaire<br/>organisme ou client]
    CP[Chef de projet / référent sécurité<br/>le candidat]
    Back[Développeur back-end]
    Front[Développeur front-end]
    Sponsor --> CP
    CP --> Back
    CP --> Front
```

### 7.2 Réunions et outils

| Réunion | Participants | Durée | Outils pour la rendre efficace |
|---|---|---|---|
| Point quotidien | Équipe projet | 15 min | Tableau Kanban (GitHub Projects), trois questions fixes : fait, à faire, bloquant |
| Revue de fonctionnalité | Équipe projet | 30 min | Démonstration sur l'environnement de recette, pull request ouverte en support |
| Comité de pilotage | Commanditaire, chef de projet | 1 h par mois | Ordre du jour envoyé 48 h avant, indicateurs du §5, relevé de décisions diffusé le jour même |
| Rétrospective | Équipe projet | 45 min toutes les 2 semaines | Format « garder / arrêter / essayer », une action d'amélioration suivie à la fois |

### 7.3 Management, motivation et situations de handicap

- **Objectifs et responsabilités** : chaque membre porte un périmètre explicite (back-end, front-end) et relit le code de l'autre, ce qui répartit la connaissance et réduit le risque de dépendance à une seule personne (S1, Bloc 1.3).
- **Motivation** : fonctionnalités attribuées selon les intérêts déclarés quand c'est possible, reconnaissance du travail lors des revues, possibilité de proposer des sujets d'amélioration technique.
- **Situations de handicap** : recueil confidentiel des besoins à l'arrivée, aménagements des outils (contraste et taille de police de l'éditeur, lecteur d'écran compatible avec les outils de revue), des horaires et du format des réunions (comptes rendus écrits systématiques, réunions en visioconférence sous-titrées), en lien avec le référent handicap de l'entreprise.

### 7.4 Plan de formation d'un nouvel arrivant

| Étape | Contenu | Support |
|---|---|---|
| Jour 1 | Installation locale et premier lancement | `docs/deployment.md`, procédure du Bloc 3.2 §3 |
| Jours 1-2 | Architecture, modèle de données, conventions | `docs/architecture.md`, Bloc 3.1 |
| Semaine 1 | Première fonctionnalité simple, en binôme avec le chef de projet | Cycle spec → plan → pull request |
| Semaine 2 | Sensibilisation sécurité : risques R1 à R10, constats du pentest | Blocs 4A.1 et 4A.2 |

---

## Renvoi croisé

- Note de cadrage : [`bloc2-2-note-de-cadrage.md`](bloc2-2-note-de-cadrage.md)
- Dossier de planification + bilan (Gantt macro) : [`bloc2-3-dossier-planification-bilan.md`](bloc2-3-dossier-planification-bilan.md)
- Analyse stratégique (risque S1, dépendance à une personne clé) : [`bloc1-3-analyse-strategique.md`](bloc1-3-analyse-strategique.md)
