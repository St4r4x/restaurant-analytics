# Checklist de validation — RNCP Niveau 7 "Expert en informatique et système d'information"

> Certificateur : **3W Academy** · Formation dispensée via **Aflokkat**
> Référence : Référentiel d'évaluation + Règlement d'examen (dernière MAJ février 2022)
> Option choisie : **Bloc 4A — Manager la cybersécurité des systèmes, applications et bases de données**
> Support technique principal : [`restaurant-analytics`](https://github.com/St4r4x/restaurant-analytics)
>
> Ce document est un plan de travail personnel, pas un livrable officiel.
> Statut : ✅ Fait · ⚠️ Ébauche existante à retravailler · ❌ À produire

---

## Structure officielle de la certification

La certification est découpée en **4 blocs de compétences** (3 communs + 1 optionnel), chacun évalué **indépendamment** par un jury d'évaluation (mise en situation professionnelle reconstituée → acquis / non acquis). La certification n'est délivrée que si :

1. **Les 4 blocs sont validés** (max. 2 tentatives par bloc — en cas d'échec, prescription pédagogique obligatoire avant repassage)
2. **L'épreuve orale finale est validée** — présentation de 30 min devant le Jury de certification, avec diaporama, sur la mise en situation professionnelle "développement d'une application informatique"

⚠️ Fraude ou plagiat sur un bloc ou l'oral final = élimination définitive, non certifiable.

## Vue d'ensemble

| Bloc | Intitulé | Livrables requis | Statut global |
|---|---|---|---|
| **1** | Analyser et définir une stratégie de SI | Dossier de veille · Cahier des charges fonctionnel · Dossier d'analyse stratégique | ⚠️ 1/3 fait |
| **2** | Piloter et manager les projets informatiques | Cahier des charges techniques · Note de cadrage · Dossier de planification (+ bilan) · Plan de changement | ❌ 0/4 fait |
| **3** | Concevoir et développer une application informatique | Dossier de conception · Application développée + dossier technique | ⚠️ appli faite, dossiers à produire |
| **4A** | Manager la cybersécurité (option choisie) | Dossier d'analyse des risques SI · Simulation de hacking/pentest | ❌ 0/2 fait |
| **—** | Épreuve orale finale | Diaporama + présentation 30 min devant jury | ❌ à préparer une fois les 4 blocs validés |

---

## BLOC 1 — Analyser et définir une stratégie de systèmes d'information

### 1.1 Dossier de veille ❌
**Critères d'évaluation**
- [ ] Méthode de collecte de flux d'information utilisée et **décrite** (pull ou push)
- [ ] Données analysées avec exclusion justifiée des sources non fiables/non pertinentes
- [ ] Vocabulaire adapté au public visé (compréhensible hors expert)
- [ ] Recommandations argumentées intégrées

**À faire**
- [ ] Choisir 3-4 thématiques (sécurité API, cloud-native, réglementation IA/RGPD, agentic AI)
- [ ] Documenter la méthode de collecte
- [ ] Rédiger le dossier avec fiabilité des sources justifiée + recommandations

### 1.2 Cahier des charges fonctionnel ✅
- [x] Fait — doc Drive *"Cahier des charges fonctionnel"* (projet Aflokkat, groupe de 3)
- [ ] Relecture rapide de la cartographie du SI + inventaire des fonctions avant dépôt final

### 1.3 Dossier d'analyse stratégique ⚠️
**Base existante** : *"Etude de cas Aflokkat"* (Drive)

**Critères d'évaluation**
- [ ] Diagnostic du SI existant structuré
- [ ] Risques et impacts (probabilité + montant) évalués
- [ ] Cohérence avec obligations réglementaires et orientations stratégiques démontrée
- [ ] Priorités et évolutions préconisées listées explicitement

**À faire**
- [ ] Restructurer en 5 sections : enjeux/orientations, SI actuel, process améliorables, priorités, évolutions préconisées
- [ ] Ajouter une évaluation chiffrée des risques (probabilité × impact)
- [ ] Vérifier la cohérence RGPD

---

## BLOC 2 — Piloter et manager les projets informatiques

### 2.1 Cahier des charges techniques ⚠️
**Base existante** : `docs/architecture.md`, `docs/api.md`

**Critères d'évaluation**
- [ ] Spécifications fonctionnelles détaillées
- [ ] Contenu des écrans documenté
- [ ] Contenu des bases de données documenté (Mongo + PostgreSQL)
- [ ] Environnement informatique choisi et **justifié**
- [ ] Interactions entre composants décrites
- [ ] Plan de développement logiciel présent

**À faire**
- [ ] Compiler en un seul document + justifier chaque choix techno
- [ ] Documenter le contenu des écrans (captures + description, base : `docs/ui.md`)
- [ ] Schéma des interactions entre composants

### 2.2 Note de cadrage ❌
**Critères d'évaluation** : contexte, méthode de conduite, contraintes, risques + solutions de contournement, ressources, délais

**À faire**
- [ ] Contexte du projet `restaurant-analytics`
- [ ] Méthode de conduite (agile/lean — cohérent avec workflow Superpowers déjà utilisé)
- [ ] Contraintes (solo dev, données NYC Open Data, hébergement Railway gratuit)
- [ ] Risques projet + solutions de contournement
- [ ] Ressources et délais (a posteriori via CHANGELOG)

### 2.3 Dossier de planification d'un projet informatique ⚠️ (inclut le bilan)
**Base existante** : `CHANGELOG.md`, `docs/superpowers/specs` et `plans`

**Critères d'évaluation — Planification**
- [ ] Phases du projet définies avec précision et pertinentes
- [ ] Planning, budget, déroulement analysés avec mesures correctives si besoin

**Critères d'évaluation — Bilan** (fait partie du même dossier)
- [ ] Objectifs fixés vs résultats atteints
- [ ] Bilan technique (pertinence des choix vs cahier des charges)
- [ ] Bilan méthodologique (répartition des rôles, méthode adoptée)
- [ ] Ressources planifiées vs utilisées
- [ ] Date de fin prévue vs réelle + synthèse

**À faire**
- [ ] Reconstituer les phases a posteriori depuis le CHANGELOG (v2.0 → v2.2.x) en planning/Gantt simplifié
- [ ] Indiquer la méthode (spec → plan → exécution par feature, visible dans `docs/superpowers/`)
- [ ] Rédiger le bilan complet une fois la planification posée

### 2.4 Plan de changement ❌
**Critères d'évaluation** : parties prenantes (acteurs, rôles, organigramme), types de messages selon parties prenantes, plan de communication, plannings, indicateurs d'avancement, plan de formation

⚠️ **Point d'attention** : `restaurant-analytics` semble être un travail solo — ce bloc suppose un contexte d'équipe.

**À faire**
- [ ] Vérifier auprès d'Aflokkat si ce bloc peut s'appuyer sur le projet de groupe (cahier des charges fonctionnel, équipe de 3) plutôt que sur `restaurant-analytics`
- [ ] Rédiger le plan de changement sur la base retenue

---

## BLOC 3 — Concevoir et développer une application informatique

### 3.1 Dossier de conception d'une application informatique ⚠️
**Base existante** : `docs/architecture.md` (structure des packages, pas de diagrammes UML)

**Critères d'évaluation**
- [ ] Décomposition de l'application présentée
- [ ] Modélisation UML : cas d'utilisation, activités, classes
- [ ] Fonctionnement de l'application décrit
- [ ] Données utilisées et structuration des informations échangées décrites

**À faire**
- [ ] Diagramme de cas d'utilisation (rôles CUSTOMER / CONTROLLER / ADMIN)
- [ ] Diagramme de classes (domain: Restaurant/Address/InspectionRecord, entity: User/Bookmark/Report)
- [ ] Diagramme d'activité pour un flux clé (ex : sync NYC Open Data → Mongo → cache → API)
- [ ] Compiler en un dossier avec `docs/architecture.md` comme socle texte

### 3.2 Application développée + dossier technique ⚠️
**Application livrée** : ✅ (`restaurant-analytics`, démo live, code sur GitHub)

**Critères d'évaluation du dossier technique**
- [ ] Revue de code formalisée (recommandations écrites, pas juste du code commenté)
- [ ] Stratégie de tests documentée : niveaux, objectifs, responsabilités, critères d'entrée/sortie, risques, environnement de tests
- [ ] Taux de couverture des tests vs objectifs fixés
- [ ] Procédures d'installation avec scripts + plans de tests associés (déploiement continu)
- [ ] Plan de sécurité
- [ ] Plan de maintenance (+ support utilisateurs)

**À faire**
- [ ] Rédiger une revue de code formelle
- [ ] Rédiger une stratégie de tests formelle à partir des tests JUnit/Mockito existants
- [ ] Compléter `docs/deployment.md` en procédure complète (scripts + plans de tests)
- [ ] Rédiger un plan de sécurité dédié (renvoi croisé avec le Bloc 4A, sans dupliquer)
- [ ] Rédiger un plan de maintenance (cron jobs existants, supervision, support)

---

## BLOC 4A — Manager la cybersécurité des systèmes, applications et bases de données

### 4A.1 Dossier d'analyse des risques des systèmes d'information ❌
**Base existante** : `docs/commercialisation.md` (liste déjà des failles connues)

**Critères d'évaluation**
- [ ] Analyse des risques du SI maîtrisée (méthode + connaissances, contexte et parties prenantes pris en compte)
- [ ] Cartographie des risques (confidentialité / intégrité / disponibilité)
- [ ] Process d'audit organisationnel et technique pertinent et complet (tout le cycle de vie)
- [ ] Plan de sécurisation avec mesures claires et ressources nécessaires
- [ ] Protections développées adaptées aux risques identifiés
- [ ] Indicateurs de suivi présentés, exploitables pour la prise de décision

**À faire**
- [ ] Lister les vulnérabilités connues (credentials en dur du `DataSeeder`, secrets non rotés, rate limiting incomplet, stack traces potentiellement exposées, stockage photo non sécurisé, HTTPS non forcé hors Railway)
- [ ] Cartographie des risques (grille probabilité × impact × actif touché)
- [ ] Documenter un process d'audit (organisationnel : gouvernance, accès ; technique : code, infra, données)
- [ ] Rédiger le plan de sécurisation (actions priorisées)
- [ ] Implémenter au moins une partie des protections dans le code (preuve concrète)
- [ ] Définir des indicateurs de suivi

### 4A.2 Simulation de hacking et tests d'intrusion ❌
**À faire**
- [ ] Se renseigner auprès d'Aflokkat sur le format exact attendu (outil imposé ? environnement dédié ? encadrement ?)
- [ ] Planifier/réaliser le pentest et documenter les preuves numériques (accès, élévation de privilèges…)

---

## ÉPREUVE ORALE FINALE ❌ (après validation des 4 blocs)

**Modalités** : 30 minutes, diaporama projeté, devant le Jury de certification (2 professionnels externes + 1 représentant 3W Academy). Porte sur la mise en situation professionnelle reconstituée **"développement d'une application informatique"**.

**À faire (à préparer une fois les blocs validés)**
- [ ] Construire un diaporama de synthèse centré sur `restaurant-analytics` (contexte → conception → développement → sécurité)
- [ ] Préparer une présentation orale chronométrée à 30 min
- [ ] Anticiper les questions du jury sur les choix techniques et les compromis faits

---

## Ordre de traitement suggéré

1. **Bloc 4A** (cybersécurité) — le plus gros morceau, réutilise directement `docs/commercialisation.md`
2. **Bloc 3.2** (dossier technique) — converge en partie avec le Bloc 4A (plan de sécurité)
3. **Bloc 3.1** (UML) — rapide, clarifie le 3.2
4. **Bloc 2.1 + 2.2** (cahier des charges technique + note de cadrage) — s'appuient sur 3.1/3.2
5. **Bloc 2.3** (planification + bilan) — s'appuie sur 2.1/2.2
6. **Bloc 1.3** (analyse stratégique) — retravail léger d'un existant
7. **Bloc 1.1** (veille) — indépendant, à caser n'importe quand
8. **Bloc 2.4** (plan de changement) — à clarifier avec Aflokkat avant de rédiger quoi que ce soit
9. **Épreuve orale finale** — seulement une fois les 4 blocs validés
