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
| **1** | Analyser et définir une stratégie de SI | Dossier de veille · Cahier des charges fonctionnel · Dossier d'analyse stratégique | ⚠️ 2/3 fait |
| **2** | Piloter et manager les projets informatiques | Cahier des charges techniques · Note de cadrage · Dossier de planification (+ bilan) · Plan de changement | ⚠️ 3/4 fait |
| **3** | Concevoir et développer une application informatique | Dossier de conception · Application développée + dossier technique | ✅ 2/2 fait |
| **4A** | Manager la cybersécurité (option choisie) | Dossier d'analyse des risques SI · Simulation de hacking/pentest | ⚠️ 1/2 fait |
| **—** | Épreuve orale finale | Diaporama + présentation 30 min devant jury | ❌ à préparer une fois les 4 blocs validés |

---

## BLOC 1 — Analyser et définir une stratégie de systèmes d'information

### 1.1 Dossier de veille ✅
**Fait** — `certification/bloc1-1-dossier-veille.md`
- [x] Méthode de collecte de flux d'information utilisée et **décrite** (pull ou push)
- [x] Données analysées avec exclusion justifiée des sources non fiables/non pertinentes
- [x] Vocabulaire adapté au public visé (compréhensible hors expert)
- [x] Recommandations argumentées intégrées

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

### 2.1 Cahier des charges techniques ✅
**Fait** — `certification/bloc2-1-cahier-des-charges-techniques.md` (+ `certification/screenshots/`)

**Critères d'évaluation**
- [x] Spécifications fonctionnelles détaillées
- [x] Contenu des écrans documenté
- [x] Contenu des bases de données documenté (Mongo + PostgreSQL)
- [x] Environnement informatique choisi et **justifié**
- [x] Interactions entre composants décrites (diagramme Mermaid validé avec `mmdc`)
- [x] Plan de développement logiciel présent

### 2.2 Note de cadrage ✅
**Fait** — `certification/bloc2-2-note-de-cadrage.md`

**Critères d'évaluation**
- [x] Contexte du projet `restaurant-analytics`
- [x] Méthode de conduite (workflow Superpowers : brainstorm → spec → plan → implémentation → revue CI → documentation)
- [x] Contraintes (solo dev, coût d'hébergement, alternance, double casquette académique/certification)
- [x] Risques projet + solutions de contournement
- [x] Ressources et délais (reconstitués depuis `CHANGELOG.md`)

### 2.3 Dossier de planification d'un projet informatique ✅ (inclut le bilan)
**Fait** — `certification/bloc2-3-dossier-planification-bilan.md`

**Critères d'évaluation — Planification**
- [x] Phases du projet définies avec précision et pertinentes (7 phases, Gantt Mermaid validé avec `mmdc`)
- [x] Planning, budget, déroulement analysés avec mesures correctives si besoin (3 écarts réels documentés : allers-retours de package, migration GSD→Superpowers, abandon de git-cliff)

**Critères d'évaluation — Bilan** (fait partie du même dossier)
- [x] Objectifs fixés vs résultats atteints
- [x] Bilan technique (pertinence des choix vs cahier des charges)
- [x] Bilan méthodologique (répartition des rôles, méthode adoptée)
- [x] Ressources planifiées vs utilisées
- [x] Date de fin prévue vs réelle + synthèse

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

### 3.1 Dossier de conception d'une application informatique ✅
**Fait** — `certification/bloc3-1-dossier-conception.md` (diagrammes Mermaid : cas d'utilisation, classes, activité — syntaxe validée avec `mmdc`)
- [x] Décomposition de l'application présentée
- [x] Modélisation UML : cas d'utilisation, activités, classes
- [x] Fonctionnement de l'application décrit
- [x] Données utilisées et structuration des informations échangées décrites

### 3.2 Application développée + dossier technique ✅
**Application livrée** : ✅ (`restaurant-analytics`, démo live, code sur GitHub)
**Dossier technique** : ✅ — `certification/bloc3-2-dossier-technique.md`
- [x] Revue de code formalisée (recommandations écrites, pas juste du code commenté)
- [x] Stratégie de tests documentée : niveaux, objectifs, responsabilités, critères d'entrée/sortie, risques, environnement de tests
- [x] Taux de couverture des tests vs objectifs fixés
- [x] Procédures d'installation avec scripts + plans de tests associés (déploiement continu)
- [x] Plan de sécurité
- [x] Plan de maintenance (+ support utilisateurs)

---

## BLOC 4A — Manager la cybersécurité des systèmes, applications et bases de données

### 4A.1 Dossier d'analyse des risques des systèmes d'information ✅
**Fait** — `certification/bloc4a-1-analyse-risques.md` (10 risques cartographiés, R1/R2/R4/R5/R6/R8 traités, R3 partiel, R7/R9/R10 restants avec plan de sécurisation)
- [x] Analyse des risques du SI maîtrisée (méthode + connaissances, contexte et parties prenantes pris en compte)
- [x] Cartographie des risques (confidentialité / intégrité / disponibilité)
- [x] Process d'audit organisationnel et technique pertinent et complet (tout le cycle de vie)
- [x] Plan de sécurisation avec mesures claires et ressources nécessaires
- [x] Protections développées adaptées aux risques identifiés
- [x] Indicateurs de suivi présentés, exploitables pour la prise de décision

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

1. ~~**Bloc 4A.1** (analyse des risques)~~ ✅ fait
2. ~~**Bloc 3.2** (dossier technique)~~ ✅ fait
3. ~~**Bloc 3.1** (UML)~~ ✅ fait
4. ~~**Bloc 1.1** (veille)~~ ✅ fait
5. ~~**Bloc 2.1 + 2.2** (cahier des charges technique + note de cadrage)~~ ✅ fait
6. ~~**Bloc 2.3** (planification + bilan)~~ ✅ fait
7. **Bloc 1.3** (analyse stratégique) — retravail léger d'un existant, prochaine étape logique
8. **Bloc 4A.2** (pentest) — question envoyée à Aflokkat sur le format attendu, en attente de réponse
9. **Bloc 2.4** (plan de changement) — question envoyée à Aflokkat sur la modalité groupe/solo, en attente de réponse
10. **Épreuve orale finale** — seulement une fois les 4 blocs validés
