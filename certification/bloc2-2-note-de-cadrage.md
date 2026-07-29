# Note de cadrage

**Certification** : RNCP niveau 7 — Expert en informatique et système d'information (3W Academy)
**Bloc** : 2 — Piloter et manager les projets informatiques
**Livrable** : Note de cadrage
**Candidat** : Arnaud Thery
**Projet cadré** : `restaurant-analytics`

---

## 1. Contexte

`restaurant-analytics` est une application web permettant d'explorer les inspections d'hygiène des restaurants de New York, à partir des données publiques de la ville (NYC Open Data). Le projet est né d'un exercice académique (module big data / Aflokkat) puis a été prolongé en solo au-delà du périmètre initial, jusqu'à devenir le support technique principal de la certification RNCP Niveau 7 (Bloc 3 et Bloc 4A notamment).

L'application cible quatre profils : visiteur anonyme (recherche, carte, analytics), client authentifié (favoris), contrôleur d'hygiène (rapports de terrain), administrateur (supervision technique). Elle est déployée en continu en production (Railway), avec synchronisation nocturne automatique des données sources.

**Enjeu du cadrage** : le projet a débuté sans note de cadrage formelle (pratique courante en développement solo itératif) — ce document reconstitue a posteriori le cadrage qui aurait dû précéder la phase de développement intensif (mars 2026), en s'appuyant sur l'historique réel du projet (`CHANGELOG.md`, `docs/superpowers/`) plutôt que sur une intention projetée.

---

## 2. Méthode de conduite

Développement **itératif et incrémental**, en solo, structuré fonctionnalité par fonctionnalité plutôt qu'en phases fermées à la Waterfall :

1. **Brainstorm** — clarification du besoin avant toute décision technique
2. **Spec** — document de conception écrit, alternatives évaluées et écartées explicitement
3. **Plan** — découpage en étapes/commits atomiques
4. **Implémentation** sur branche dédiée, avec tests
5. **Revue automatisée** — pipeline CI complet avant fusion (`main` protégée, 5 vérifications requises : build, tests unitaires, tests d'intégration, scan de secrets, test de fumée E2E)
6. **Documentation** — mise à jour systématique de `CHANGELOG.md` et de la documentation technique

Cette méthode (outillée via les skills Superpowers, artefacts dans `docs/superpowers/specs/` et `docs/superpowers/plans/`) s'apparente à un **Kanban personnel** : pas de sprints à durée fixe, un flux continu de fonctionnalités priorisées, chacune livrée et déployée indépendamment. Cohérent avec un contexte solo où la synchronisation d'équipe (mêlées, sprints) n'apporte pas de valeur.

---

## 3. Contraintes

| Contrainte | Nature | Impact |
|---|---|---|
| **Développeur unique** | Ressource humaine | Pas de parallélisation possible entre fonctionnalités ; la revue de code est auto-portée, compensée par une CI stricte (tests + scan de secrets obligatoires avant fusion) |
| **Hébergement à coût maîtrisé** (Railway, plan à ressources limitées) | Budget | Décisions dictées par le coût : Elasticsearch coupé (non facturé) faute de trafic justifiant son coût mémoire (~1 Go/mois) ; limite mémoire du service applicatif dimensionnée avec marge sur le pic observé plutôt que sur un plan surdimensionné |
| **Données sources externes** (API NYC Open Data) | Technique | Le schéma et la disponibilité de l'API ne sont pas maîtrisés par le projet — le sync nocturne inclut un backoff exponentiel et un plafond configurable (`nyc.api.max_records`) pour absorber la variabilité de la source |
| **Double casquette du projet** (académique + support de certification) | Organisationnelle | Certaines décisions (documentation exhaustive, dossiers `certification/`) répondent à des exigences du référentiel RNCP plutôt qu'à un besoin utilisateur direct |
| **Alternance** (disponibilité du candidat) | Temporelle | Développement par sessions non contiguës, d'où l'intérêt d'une documentation à jour (`CLAUDE.md`, `docs/`) pour reprendre le contexte rapidement d'une session à l'autre |

---

## 4. Risques et solutions de contournement

| Risque | Probabilité | Impact | Solution de contournement |
|---|---|---|---|
| Régression silencieuse introduite par une fonctionnalité | Moyenne | Élevé | CI obligatoire avant fusion sur `main` (tests unitaires + intégration + E2E) ; illustré par la régression réelle du 28/07/2026 où la migration JWT a cassé l'accès anonyme à `/inspection-map` — détectée et corrigée en quelques heures grâce à une vérification manuelle post-déploiement, pas par la CI (aucun test frontend automatisé n'existe à ce jour) |
| Dérive de coût d'hébergement | Moyenne | Moyen | Revue périodique des métriques Railway (mémoire, services actifs) ; arbitrage explicite coût/fonctionnalité (cas Elasticsearch) plutôt qu'accumulation passive de services |
| Perte de contexte entre sessions de travail espacées | Élevée (contrainte alternance) | Moyen | `CLAUDE.md` et `docs/` tenus à jour à chaque fonctionnalité livrée ; `CHANGELOG.md` comme source de vérité chronologique |
| Documentation qui dérive du code réel | Moyenne | Moyen | Convention du projet : toute fonctionnalité livrée met à jour la documentation correspondante dans le même cycle (voir section 6, Bloc 2.1) — un écart résiduel identifié en cours de rédaction de ce document (`docs/ui.md` référençait un gabarit `map.html` renommé depuis en `inspection-map.html`) confirme que le contrôle n'est pas parfait et reste à fiabiliser |
| Automatisation de release mal calibrée | Faible | Faible | Une première tentative d'automatisation complète du changelog (`git-cliff`, mai 2026) a été abandonnée après constat qu'elle produisait un contenu moins exploitable que la rédaction manuelle — le remplacement (juillet 2026) n'automatise que le numéro de version et la découpe de section, pas le contenu |
| Fuite de identifiants/secrets dans le dépôt | Faible | Élevé | Scan de secrets (`gitleaks`) obligatoire en CI sur chaque push/PR |

---

## 5. Ressources

| Ressource | Détail |
|---|---|
| **Humaine** | 1 développeur (candidat), en alternance — pas d'équipe |
| **Infrastructure** | Railway (application + PostgreSQL), MongoDB Atlas, Redis, Elasticsearch (optionnel) ; GitHub Actions pour la CI/CD |
| **Outillage** | Java 25, Spring Boot 4.0.5, Maven, Docker Compose (environnement local), Claude Code (assistance au développement et à la revue) |
| **Services tiers** | NYC Open Data (source de données), Resend (envoi d'emails transactionnels), Supabase (hébergement PostgreSQL avec sauvegarde chiffrée quotidienne) |

---

## 6. Délais

Reconstitués depuis `CHANGELOG.md` (source de vérité chronologique du projet) :

| Période | Jalon |
|---|---|
| 2026-03-29 → 2026-04-13 | Socle applicatif : rôles, JWT, rapports contrôleur, découverte client, upgrade Java 25 / Spring Boot 4 |
| 2026-04-03 → 2026-04-12 | Enrichissement fonctionnel : page d'accueil, carte filtrable, admin, UX, tests d'intégration Testcontainers |
| 2026-05-03 → 2026-05-12 | v2.0 → v2.2.4 : refonte frontend, CI/CD 5 jobs, recherche Elasticsearch, enrichissement OSM |
| 2026-07-22 → 2026-07-28 | v2.3.0 → v2.4.0 : durcissement sécurité (RGPD, JWT httpOnly), sauvegardes chiffrées, réinitialisation de mot de passe |
| 2026-07-28 → 2026-07-29 | v2.4.1 : correction de régression post-déploiement, automatisation partielle des releases |

Durée totale observée : **~4 mois** entre le premier commit fonctionnel et la version courante, sans jalon de fin fixé a priori — le projet suit un rythme d'itération continue plutôt qu'un calendrier de projet figé, cohérent avec la contrainte de disponibilité en alternance (section 3).

---

## Renvoi croisé

- Cahier des charges techniques : [`bloc2-1-cahier-des-charges-techniques.md`](bloc2-1-cahier-des-charges-techniques.md)
- Historique complet : [`CHANGELOG.md`](../CHANGELOG.md)
- Méthode détaillée par fonctionnalité : `docs/superpowers/specs/` et `docs/superpowers/plans/`
- Analyse des risques SI (périmètre sécurité, distinct de ce cadrage projet) : [`bloc4a-1-analyse-risques.md`](bloc4a-1-analyse-risques.md)
