---
created: 2026-03-31T12:11:36.449Z
title: Translate entire project to English
area: general
files:
  - src/main/resources/templates/index.html
  - src/main/resources/templates/restaurant.html
  - src/main/resources/templates/inspection-map.html
  - src/main/resources/templates/my-bookmarks.html
  - src/main/resources/templates/hygiene-radar.html
  - src/main/resources/templates/login.html
  - src/main/resources/templates/inspection.html
  - src/main/java/com/aflokkat/controller/ViewController.java
---

## Problem

The project mixes French and English: HTML templates use French labels, comments, and UI text (e.g. `lang="fr"`, "Fiche Restaurant", "Connexion", inline comments in French). Java source files also contain some French comments. The project guidelines (CLAUDE.md) specify English for code comments, and the codebase should be consistent for an academic project.

## Solution

1. Update all Thymeleaf HTML templates: change `lang="fr"` to `lang="en"`, translate all visible UI text (titles, labels, buttons, empty states, error messages) to English
2. Translate French inline comments in Java source files to English
3. Update `<title>` tags in all templates
4. Keep consistent tone with existing English copy defined in `03-UI-SPEC.md` (copywriting contract)
