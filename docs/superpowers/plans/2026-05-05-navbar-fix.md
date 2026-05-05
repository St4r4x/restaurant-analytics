# Navbar Consistency Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the navbar render with consistent alignment and width on every page by making its container self-contained inside the fragment.

**Architecture:** Replace the generic Bootstrap `.container` div inside `fragments/navbar.html` with a `.navbar-container` class defined inside the fragment's own `<style>` block — immune to per-page CSS overrides.

**Tech Stack:** Thymeleaf HTML fragment, Bootstrap 5, inline CSS

---

### Task 1: Fix navbar fragment container

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html:4-5`

- [ ] **Step 1: Replace the container div class and add scoped style**

In `src/main/resources/templates/fragments/navbar.html`, change line 4-5:

```html
<nav th:fragment="navbar" class="navbar-civic navbar navbar-expand-lg px-0 sticky-top">
  <div class="container">
```

to:

```html
<nav th:fragment="navbar" class="navbar-civic navbar navbar-expand-lg px-0 sticky-top">
  <div class="navbar-container">
```

Then inside the existing `<style>` block (after the last `.btn-signin:hover` rule, before `</style>`), add:

```css
.navbar-container { max-width: 860px; margin: 0 auto; padding: 0 16px; width: 100%; }
```

- [ ] **Step 2: Verify all pages still include the fragment unchanged**

Run:
```bash
grep -rn 'th:replace="fragments/navbar' src/main/resources/templates/
```
Expected: every `.html` template shows `th:replace="fragments/navbar :: navbar"` — none reference `.container` inside the nav.

- [ ] **Step 3: Build**

Run:
```bash
mvn clean package -DskipTests -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Smoke-test visually**

Start the app (`mvn spring-boot:run`) and open these pages in a browser. Verify the navbar brand + links sit at the same horizontal position on all of them:
- `http://localhost:8080/` (landing — has narrow custom `.container`)
- `http://localhost:8080/admin` (admin — has its own `.container { max-width: 860px }`)
- `http://localhost:8080/my-bookmarks` (bookmarks — has `.container { max-width: 900px; padding: 0 16px }`)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/fragments/navbar.html
git commit -m "fix(ui): self-contain navbar container to fix cross-page alignment"
```
