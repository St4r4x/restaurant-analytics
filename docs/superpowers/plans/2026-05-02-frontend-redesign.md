# Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the purple-gradient aesthetic across all 11 Thymeleaf templates and 2 fragments with the Clean Civic design system (Bootstrap 5, Playfair Display + Inter, white navbar + red accent, off-white background).

**Architecture:** Bootstrap 5.3.3 and Google Fonts are loaded via CDN in `ux-utils.html` so all pages inherit them automatically. Shared CSS classes (`.page-header`, `.kpi-card`, `.content-card`, `.rest-card`, `.grade-badge`, `.civic-footer`, `.section-label`, `.section-title`) are defined inline in each template — no separate `.css` file, consistent with existing approach. All existing JS logic (auth state, fetch calls, role-based routing) is preserved unchanged in every template. All server-provided data rendered via JS uses `textContent` for plain strings (not innerHTML) to avoid XSS; innerHTML is only used with static string templates where values are escaped with a helper.

**Tech Stack:** Thymeleaf, Bootstrap 5.3.3 (CDN), Google Fonts (Playfair Display + Inter), Leaflet 1.9.4 (retained), Chart.js 4.4.0 (retained), MarkerCluster 1.5.3 (retained)

---

## File Map

| File | Action | What changes |
|---|---|---|
| `src/main/resources/templates/fragments/ux-utils.html` | Modify | Add Bootstrap CSS CDN + Google Fonts; add global body/font CSS; keep all JS |
| `src/main/resources/templates/fragments/navbar.html` | Rewrite | Replace gradient nav with Bootstrap navbar-civic; keep all auth/active-link JS |
| `src/main/resources/templates/landing.html` | Rewrite | Dark hero + stat strip + card grid; keep search JS |
| `src/main/resources/templates/login.html` | Rewrite | Centered card on off-white; Bootstrap tabs + form-control; keep all auth JS |
| `src/main/resources/templates/analytics.html` | Rewrite | Dark page header + KPI cards + 3-col layout; keep Chart.js fetch JS |
| `src/main/resources/templates/index.html` | Rewrite | Dark page header + bookmarks/nearby/KPI cards; keep fetch JS |
| `src/main/resources/templates/restaurant.html` | Rewrite | Dark page header + 2-col detail layout; keep Leaflet/Chart.js/bookmark JS |
| `src/main/resources/templates/inspection-map.html` | Rewrite | Dark page header + toolbar + full-height map; keep Leaflet/filter JS |
| `src/main/resources/templates/my-bookmarks.html` | Rewrite | Dark page header + card list; keep pagination/bookmark JS |
| `src/main/resources/templates/profile.html` | Rewrite | Dark page header + profile card; keep fetch JS |
| `src/main/resources/templates/dashboard.html` | Rewrite | Dark page header + KPI tiles + bookmarks/nearby cards; keep fetch JS |
| `src/main/resources/templates/uncontrolled.html` | Rewrite | Dark page header + Bootstrap table; keep filter/fetch JS |
| `src/main/resources/templates/admin.html` | Rewrite | Dark page header + Bootstrap cards; keep sync/user-management JS |

---

## Shared CSS Block

Every template's `<style>` block should include these rules (copy verbatim):

```
body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; }
.page-header { background: #1a1a1a; color: white; padding: 32px 0; margin-bottom: 0; }
.page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
.page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }
.kpi-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #c0392b; padding: 20px 24px; }
.kpi-number { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 700; line-height: 1; color: #1a1a1a; }
.kpi-number.green { color: #2e7d32; }
.kpi-number.red { color: #c0392b; }
.kpi-label { font-size: 0.72em; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: #888; margin-top: 6px; }
.content-card { background: white; border: 1px solid #e8e0d8; padding: 24px; }
.section-label { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 6px; }
.section-title { font-family: 'Playfair Display', serif; font-size: 1.4em; font-weight: 700; margin-bottom: 20px; }
.rest-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #e8e0d8; padding: 20px; transition: border-top-color 0.15s; cursor: pointer; height: 100%; }
.rest-card:hover { border-top-color: #c0392b; }
.grade-badge { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; font-weight: 900; font-size: 1.1em; border-radius: 2px; float: right; }
.grade-a { background: #e8f5e9; color: #2e7d32; }
.grade-b { background: #fff8e1; color: #f57f17; }
.grade-c { background: #ffebee; color: #c62828; }
.civic-footer { background: #1a1a1a; color: rgba(255,255,255,0.45); padding: 24px 0; font-size: 0.78em; }
```

---

## XSS Safety Rule

All JS in these templates renders data from the API into the DOM. Follow this rule in every template:

- Use `element.textContent = value` for plain text values (names, scores, addresses).
- Only use string concatenation with `innerHTML` for structural HTML where string values are wrapped with `esc(value)` — a helper that replaces `&`, `<`, `>`, `"` with HTML entities.
- Add this helper at the top of every `<script>` block that uses `innerHTML` with API data:

```javascript
function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
```

---

## Task 1: ux-utils.html — Add Bootstrap + Fonts

**Files:**
- Modify: `src/main/resources/templates/fragments/ux-utils.html`

- [ ] **Step 1: Insert CDN links at the start of the fragment**

Open `src/main/resources/templates/fragments/ux-utils.html`. The `<th:block th:fragment="ux-utils">` is on line 4 and is immediately followed by `<style>` on line 5.

Replace:
```
<th:block th:fragment="ux-utils">
<style>
```

With:
```
<th:block th:fragment="ux-utils">
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<style>
body { font-family: 'Inter', sans-serif; }
```

- [ ] **Step 2: Verify**

Run: `head -12 src/main/resources/templates/fragments/ux-utils.html`

Expected output: Bootstrap CDN link on line 5, Google Fonts on line 6, Bootstrap JS on line 7, `<style>` on line 8.

- [ ] **Step 3: Commit**

```
git add src/main/resources/templates/fragments/ux-utils.html
git commit -m "style: load Bootstrap 5 and Google Fonts via CDN in ux-utils fragment"
```

---

## Task 2: navbar.html — Clean Civic navbar

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`

**What to change:** Replace the entire file. The `th:fragment="navbar"` attribute must be kept. All JS (auth state check, active-link highlight, hamburger) is kept, only restructured for Bootstrap classes.

- [ ] **Step 1: Write the new navbar file**

Write `src/main/resources/templates/fragments/navbar.html` with this content:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<nav th:fragment="navbar" class="navbar-civic navbar navbar-expand-lg px-0 sticky-top">
  <div class="container">
    <a class="navbar-brand" href="/">
      NYC Restaurant Inspector
      <span>Health Department Data</span>
    </a>
    <button class="navbar-toggler border-0" type="button" id="hamburger-btn" aria-label="Toggle navigation">
      <span style="font-size:1.3em;color:#444">&#9776;</span>
    </button>
    <div class="collapse navbar-collapse" id="nav-links">
      <ul class="navbar-nav ms-auto align-items-lg-center gap-1">
        <li class="nav-item"><a class="nav-link" href="/" data-nav="/">Search</a></li>
        <li class="nav-item"><a class="nav-link" href="/inspection-map" data-nav="/inspection-map">Map</a></li>
        <li class="nav-item"><a class="nav-link" href="/analytics" data-nav="/analytics">Analytics</a></li>
        <li class="nav-item" id="nav-bookmarks" style="display:none"><a class="nav-link" href="/my-bookmarks" data-nav="/my-bookmarks">Bookmarks</a></li>
        <li class="nav-item" id="nav-dashboard" style="display:none"><a class="nav-link" href="/dashboard" data-nav="/dashboard">Dashboard</a></li>
        <li class="nav-item" id="nav-uncontrolled" style="display:none"><a class="nav-link" href="/uncontrolled" data-nav="/uncontrolled">Uncontrolled</a></li>
        <li class="nav-item" id="nav-admin" style="display:none"><a class="nav-link" href="/admin" data-nav="/admin">Admin</a></li>
      </ul>
      <span id="nav-auth" class="ms-3 d-flex align-items-center gap-2"></span>
    </div>
  </div>
<style>
.navbar-civic { background: #fff; border-bottom: 3px solid #c0392b; }
.navbar-civic .navbar-brand { font-family: 'Playfair Display', serif; font-weight: 900; font-size: 1.1em; color: #1a1a1a !important; letter-spacing: -0.01em; line-height: 1.1; padding: 12px 0; }
.navbar-civic .navbar-brand span { display: block; font-size: 0.52em; font-family: 'Inter', sans-serif; font-weight: 700; letter-spacing: 0.12em; color: #c0392b; text-transform: uppercase; }
.navbar-civic .nav-link { font-size: 0.8em; font-weight: 600; color: #444 !important; letter-spacing: 0.04em; text-transform: uppercase; padding: 16px 10px !important; }
.navbar-civic .nav-link:hover { color: #c0392b !important; }
.navbar-civic .nav-link.active { color: #c0392b !important; border-bottom: 3px solid #c0392b; margin-bottom: -3px; }
.btn-signin { font-size: 0.82em; font-weight: 600; letter-spacing: 0.04em; background: #c0392b; color: white !important; border: none; padding: 7px 18px; border-radius: 2px; text-transform: uppercase; text-decoration: none; }
.btn-signin:hover { background: #a93226; }
</style>
<script>
  (function() {
    var t = localStorage.getItem('accessToken');
    var el = document.getElementById('nav-auth');
    if (!t) {
      el.innerHTML = '<a href="/login" class="btn-signin">Sign In</a>';
    } else {
      try {
        var payload = JSON.parse(atob(t.split('.')[1]));
        var sub = payload.sub;
        // sub is trusted (from our own JWT) — escaping for safety
        var safeUser = sub.replace(/</g,'&lt;').replace(/>/g,'&gt;');
        el.innerHTML = '<a href="/profile" style="font-size:0.85em;color:#444;text-decoration:none;font-weight:600">' + safeUser + '</a>'
          + ' <button onclick="(function(){localStorage.removeItem(\'accessToken\');localStorage.removeItem(\'refreshToken\');window.location.href=\'/login\';})()" '
          + 'style="font-size:0.8em;font-weight:600;letter-spacing:0.04em;background:none;border:1px solid #ddd;color:#666;padding:5px 12px;border-radius:2px;cursor:pointer;text-transform:uppercase">Sign Out</button>';
        document.getElementById('nav-bookmarks').style.display = 'block';
        if (payload.role === 'ROLE_CONTROLLER') {
          document.getElementById('nav-dashboard').style.display = 'block';
          document.getElementById('nav-uncontrolled').style.display = 'block';
        }
        if (payload.role === 'ROLE_ADMIN') {
          document.getElementById('nav-admin').style.display = 'block';
        }
      } catch(e) {
        el.innerHTML = '<a href="/login" class="btn-signin">Sign In</a>';
      }
    }
    document.querySelectorAll('nav a[data-nav]').forEach(function(link) {
      if (window.location.pathname === link.getAttribute('data-nav')) {
        link.classList.add('active');
      }
    });
  })();
  var hb = document.getElementById('hamburger-btn');
  if (hb) {
    hb.addEventListener('click', function() {
      document.getElementById('nav-links').classList.toggle('show');
    });
  }
</script>
</nav>
</body>
</html>
```

- [ ] **Step 2: Verify navbar renders**

Start the app (`mvn spring-boot:run -q &`) and open `http://localhost:8080`.

Expected: white navbar, 3px red bottom border, serif "NYC Restaurant Inspector" brand, uppercase links, red "SIGN IN" button top-right.

- [ ] **Step 3: Commit**

```
git add src/main/resources/templates/fragments/navbar.html
git commit -m "style: replace gradient navbar with Clean Civic Bootstrap navbar"
```

---

## Task 3: landing.html

**Files:**
- Modify: `src/main/resources/templates/landing.html`

**What to change:** Replace the entire file. This page currently has a dark gradient body with a plain card. The new version has a dark hero, red stat strip, and restaurant card grid.

Key API calls to preserve and verify match the existing file (read it first):
- `/api/analytics/kpis` → populates `#kpi-total`, `#kpi-pct`, `#kpi-avg`, `#kpi-risk`
- `/api/restaurants?sort=inspectionDate&limit=6` or similar → populates `#recent-grid`
- Search: `/api/restaurants?name=...&limit=12` → populates `#results-grid`

- [ ] **Step 1: Read the current file to confirm API endpoint URLs**

Run: `grep -n "fetch(" src/main/resources/templates/landing.html`

Note the exact URLs used. Use those same URLs in the new JS (do not invent new endpoints).

- [ ] **Step 2: Write the new landing.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NYC Restaurant Inspector</title>
  <th:block th:replace="fragments/ux-utils :: ux-utils"></th:block>
  <style>
    body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; }
    .hero { background: #1a1a1a; color: white; padding: 72px 0 64px; position: relative; overflow: hidden; }
    .hero::before { content: ''; position: absolute; top: 0; right: 0; bottom: 0; width: 40%; background: repeating-linear-gradient(-45deg, transparent, transparent 10px, rgba(192,57,43,0.06) 10px, rgba(192,57,43,0.06) 11px); }
    .hero-eyebrow { font-size: 0.72em; font-weight: 700; letter-spacing: 0.15em; color: #c0392b; text-transform: uppercase; margin-bottom: 16px; }
    .hero h1 { font-family: 'Playfair Display', serif; font-weight: 900; font-size: 3.2em; line-height: 1.05; margin-bottom: 20px; }
    .hero h1 em { color: #c0392b; font-style: normal; }
    .hero-sub { font-size: 1em; color: rgba(255,255,255,0.65); max-width: 480px; line-height: 1.6; margin-bottom: 32px; }
    .search-bar { display: flex; max-width: 520px; box-shadow: 0 4px 24px rgba(0,0,0,0.4); }
    .search-bar input { flex: 1; border: none; padding: 14px 20px; font-size: 0.95em; border-radius: 0; outline: none; font-family: 'Inter', sans-serif; }
    .search-bar button { background: #c0392b; color: white; border: none; padding: 14px 24px; font-weight: 700; font-size: 0.85em; letter-spacing: 0.06em; text-transform: uppercase; cursor: pointer; white-space: nowrap; font-family: 'Inter', sans-serif; }
    .search-bar button:hover { background: #a93226; }
    .stat-strip { background: #c0392b; color: white; padding: 18px 0; }
    .stat-number { font-family: 'Playfair Display', serif; font-size: 1.8em; font-weight: 700; line-height: 1; }
    .stat-label { font-size: 0.7em; letter-spacing: 0.1em; text-transform: uppercase; opacity: 0.85; margin-top: 2px; }
    .stat-divider { border-left: 1px solid rgba(255,255,255,0.25); }
    .section-label { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
    .section-title { font-family: 'Playfair Display', serif; font-size: 1.9em; font-weight: 700; color: #1a1a1a; margin-bottom: 24px; }
    .rest-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #e8e0d8; padding: 20px; transition: border-top-color 0.15s; cursor: pointer; height: 100%; }
    .rest-card:hover { border-top-color: #c0392b; }
    .grade-badge { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; font-weight: 900; font-size: 1.1em; border-radius: 2px; float: right; }
    .grade-a { background: #e8f5e9; color: #2e7d32; }
    .grade-b { background: #fff8e1; color: #f57f17; }
    .grade-c { background: #ffebee; color: #c62828; }
    .rest-name { font-weight: 700; font-size: 0.95em; margin-bottom: 4px; }
    .rest-meta { font-size: 0.78em; color: #888; }
    .rest-score { font-size: 0.78em; color: #555; margin-top: 8px; }
    .rest-link { font-size: 0.78em; color: #c0392b; text-decoration: none; font-weight: 600; margin-top: 10px; display: block; }
    .civic-footer { background: #1a1a1a; color: rgba(255,255,255,0.45); padding: 24px 0; font-size: 0.78em; }
    #results-section { display: none; }
  </style>
</head>
<body>
  <div th:replace="fragments/navbar :: navbar"></div>

  <section class="hero">
    <div class="container">
      <div class="hero-eyebrow">New York City · Open Data · Real-Time</div>
      <h1>Know before<br>you <em>eat.</em></h1>
      <p class="hero-sub">Explore hygiene inspection records for every restaurant in New York City. Live data from the NYC Department of Health.</p>
      <div class="search-bar">
        <input id="search-input" type="text" placeholder="Search by restaurant name or address…">
        <button id="search-btn">Search</button>
      </div>
    </div>
  </section>

  <div class="stat-strip">
    <div class="container">
      <div class="row g-0 text-center">
        <div class="col stat-item">
          <div class="stat-number" id="kpi-total">—</div>
          <div class="stat-label">Restaurants</div>
        </div>
        <div class="col stat-item stat-divider">
          <div class="stat-number" id="kpi-pct">—</div>
          <div class="stat-label">Grade A</div>
        </div>
        <div class="col stat-item stat-divider">
          <div class="stat-number" id="kpi-avg">—</div>
          <div class="stat-label">Avg Score</div>
        </div>
        <div class="col stat-item stat-divider">
          <div class="stat-number" id="kpi-risk">—</div>
          <div class="stat-label">At-Risk</div>
        </div>
      </div>
    </div>
  </div>

  <section id="results-section" style="padding: 48px 0;">
    <div class="container">
      <div class="section-label">Search Results</div>
      <div class="section-title" id="results-title">Results</div>
      <div class="row g-3" id="results-grid"></div>
    </div>
  </section>

  <section id="recent-section" style="padding: 56px 0;">
    <div class="container">
      <div class="section-label">Recently Inspected</div>
      <div class="section-title">Restaurants Near You</div>
      <div class="row g-3" id="recent-grid">
        <div class="col-12 text-center" style="color:#888;font-size:0.9em;padding:32px 0">Loading…</div>
      </div>
    </div>
  </section>

  <footer class="civic-footer">
    <div class="container d-flex justify-content-between align-items-center">
      <div>NYC Restaurant Inspector · Data from NYC Open Data</div>
      <div style="font-family:monospace;font-size:0.85em;color:rgba(255,255,255,0.2)">v0.2.0</div>
    </div>
  </footer>

  <script>
    function esc(s) {
      return String(s == null ? '' : s)
        .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    // KPI strip — USE THE SAME URL THE ORIGINAL FILE USES (confirmed in Step 1)
    fetch('/api/analytics/kpis')
      .then(function(r) { return r.json(); })
      .then(function(res) {
        if (res.status === 'success') {
          document.getElementById('kpi-total').textContent = res.totalRestaurants != null ? res.totalRestaurants.toLocaleString() : '—';
          document.getElementById('kpi-pct').textContent   = res.percentGradeA   != null ? res.percentGradeA.toFixed(1) + '%' : '—';
          document.getElementById('kpi-avg').textContent   = res.avgScore         != null ? res.avgScore.toFixed(1) : '—';
          document.getElementById('kpi-risk').textContent  = res.atRiskCount      != null ? res.atRiskCount.toLocaleString() : '—';
        }
      })
      .catch(function() {});

    function gradeClass(g) {
      if (g === 'A') return 'grade-a';
      if (g === 'B') return 'grade-b';
      return 'grade-c';
    }

    function restaurantCardHtml(r) {
      var grade = r.latestGrade || (r.grades && r.grades.length ? r.grades[0].grade : '—') || '—';
      var inspected = r.latestInspectionDate ? new Date(r.latestInspectionDate).toLocaleDateString('en-US', {month:'short',year:'numeric'}) : '';
      return '<div class="col-md-4">'
        + '<div class="rest-card">'
        + '<div class="grade-badge ' + gradeClass(grade) + '">' + esc(grade) + '</div>'
        + '<div class="rest-name">' + esc(r.name) + '</div>'
        + '<div class="rest-meta">' + esc(r.cuisineDescription || '') + (r.borough ? ' · ' + esc(r.borough) : '') + '</div>'
        + (r.latestScore != null ? '<div class="rest-score">Score: ' + esc(String(r.latestScore)) + (inspected ? ' · Inspected ' + esc(inspected) : '') + '</div>' : '')
        + '<a class="rest-link" href="/restaurant/' + esc(r.restaurantId) + '">View Details →</a>'
        + '</div></div>';
    }

    // Recently inspected — USE THE SAME URL THE ORIGINAL FILE USES (confirmed in Step 1)
    fetch('/api/restaurants?sort=inspectionDate&limit=6')
      .then(function(r) { return r.json(); })
      .then(function(res) {
        var grid = document.getElementById('recent-grid');
        var restaurants = (res.data || res.restaurants || []).slice(0, 6);
        if (!restaurants.length) { grid.innerHTML = ''; return; }
        grid.innerHTML = restaurants.map(restaurantCardHtml).join('');
      })
      .catch(function() { document.getElementById('recent-grid').innerHTML = ''; });

    function doSearch() {
      var q = document.getElementById('search-input').value.trim();
      if (!q) return;
      document.getElementById('results-section').style.display = 'block';
      document.getElementById('recent-section').style.display = 'none';
      document.getElementById('results-title').textContent = 'Results for "' + q + '"';
      var grid = document.getElementById('results-grid');
      grid.innerHTML = '<div class="col-12 text-center" style="padding:32px 0;color:#888">Searching…</div>';
      // USE THE SAME SEARCH URL THE ORIGINAL FILE USES (confirmed in Step 1)
      fetch('/api/restaurants?name=' + encodeURIComponent(q) + '&limit=12')
        .then(function(r) { return r.json(); })
        .then(function(res) {
          var restaurants = res.data || res.restaurants || [];
          if (!restaurants.length) {
            grid.innerHTML = '<div class="col-12" style="color:#888;font-size:0.9em;padding:32px 0;text-align:center">No restaurants found.</div>';
            return;
          }
          grid.innerHTML = restaurants.map(restaurantCardHtml).join('');
        })
        .catch(function() {
          grid.innerHTML = '<div class="col-12" style="color:#c0392b;font-size:0.9em;padding:16px 0;text-align:center">Search failed. Please try again.</div>';
        });
    }

    document.getElementById('search-btn').addEventListener('click', doSearch);
    document.getElementById('search-input').addEventListener('keydown', function(e) {
      if (e.key === 'Enter') doSearch();
    });
  </script>
</body>
</html>
```

**Important:** In Step 1 you confirmed the exact fetch URLs. If they differ from the ones above (e.g. the search uses `/api/restaurants/search?q=...` instead of `?name=...`), update the fetch calls to match. The HTML structure does not change.

- [ ] **Step 3: Verify in browser**

Open `http://localhost:8080`. Expected: dark hero section, red stat strip with live KPI numbers, card grid below.

- [ ] **Step 4: Commit**

```
git add src/main/resources/templates/landing.html
git commit -m "style: redesign landing page — dark hero, red stat strip, card grid"
```

---

## Task 4: login.html

**Files:**
- Modify: `src/main/resources/templates/login.html`

**What to change:** Replace dark `#161b22` styling with off-white centered card. The entire `<script>` block is preserved byte-for-byte — the only change is `<style>` and HTML structure.

- [ ] **Step 1: Write new login.html**

The file has two logical sections: style+structure (change these) and the script block (copy unchanged from the current file). Read the current script block first:

Run: `sed -n '/<script>/,/<\/script>/p' src/main/resources/templates/login.html`

Then write the new file:

```html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Sign In — NYC Restaurant Inspector</title>
    <th:block th:replace="fragments/ux-utils :: ux-utils"></th:block>
    <style>
      body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
      .auth-wrap { width: 100%; max-width: 420px; padding: 24px 16px; }
      .auth-brand { text-align: center; margin-bottom: 28px; }
      .auth-brand-name { font-family: 'Playfair Display', serif; font-weight: 900; font-size: 1.5em; color: #1a1a1a; line-height: 1.1; }
      .auth-brand-sub { font-size: 0.7em; font-weight: 700; letter-spacing: 0.12em; color: #c0392b; text-transform: uppercase; margin-top: 4px; }
      .auth-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #c0392b; padding: 28px 28px 24px; }
      .auth-tabs { display: flex; border-bottom: 1px solid #e8e0d8; margin-bottom: 24px; }
      .auth-tab { flex: 1; background: none; border: none; padding: 9px 0; font-size: 0.88rem; font-weight: 600; font-family: 'Inter', sans-serif; letter-spacing: 0.04em; text-transform: uppercase; color: #888; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; transition: color .15s, border-color .15s; }
      .auth-tab.active { color: #c0392b; border-bottom-color: #c0392b; }
      .form-section { display: none; }
      .form-section.active { display: block; }
      .field { margin-bottom: 14px; }
      .field label { display: block; font-size: 0.8rem; font-weight: 600; color: #555; margin-bottom: 5px; letter-spacing: 0.02em; text-transform: uppercase; }
      .field input { width: 100%; padding: 10px 12px; background: white; border: 1px solid #e8e0d8; color: #1a1a1a; font-size: 0.9rem; font-family: 'Inter', sans-serif; outline: none; border-radius: 0; transition: border-color .15s; }
      .field input:focus { border-color: #c0392b; }
      .field input::placeholder { color: #aaa; }
      .hint { font-size: 0.75rem; color: #aaa; margin-top: 3px; }
      .submit-btn { width: 100%; padding: 11px; margin-top: 6px; background: #c0392b; border: none; color: #fff; font-size: 0.88rem; font-weight: 700; font-family: 'Inter', sans-serif; letter-spacing: 0.06em; text-transform: uppercase; cursor: pointer; border-radius: 0; transition: background .15s; }
      .submit-btn:hover { background: #a93226; }
      .submit-btn:disabled { background: #e8e0d8; cursor: default; color: #aaa; }
      .error-msg { margin-top: 12px; padding: 9px 12px; background: #ffebee; border: 1px solid #ef9a9a; color: #c62828; font-size: 0.82rem; display: none; }
      .success-msg { margin-top: 12px; padding: 9px 12px; background: #e8f5e9; border: 1px solid #a5d6a7; color: #2e7d32; font-size: 0.82rem; display: none; }
    </style>
  </head>
  <body>
    <div class="auth-wrap">
      <div class="auth-brand">
        <div class="auth-brand-name">NYC Restaurant<br>Inspector</div>
        <div class="auth-brand-sub">Health Department Data</div>
      </div>
      <div class="auth-card">
        <div class="auth-tabs">
          <button class="auth-tab active" id="tabLogin" onclick="switchTab('login')">Sign In</button>
          <button class="auth-tab" id="tabRegister" onclick="switchTab('register')">Create Account</button>
        </div>
        <div class="form-section active" id="sectionLogin">
          <div class="field">
            <label for="loginUsername">Username</label>
            <input id="loginUsername" type="text" placeholder="your username" autocomplete="username" />
          </div>
          <div class="field">
            <label for="loginPassword">Password</label>
            <input id="loginPassword" type="password" placeholder="••••••••" autocomplete="current-password" />
          </div>
          <button class="submit-btn" id="loginBtn">Sign In</button>
          <div class="error-msg" id="loginError"></div>
        </div>
        <div class="form-section" id="sectionRegister">
          <div class="field">
            <label for="regUsername">Username</label>
            <input id="regUsername" type="text" placeholder="choose a username" autocomplete="username" />
          </div>
          <div class="field">
            <label for="regEmail">Email</label>
            <input id="regEmail" type="email" placeholder="you@example.com" autocomplete="email" />
          </div>
          <div class="field">
            <label for="regPassword">Password</label>
            <input id="regPassword" type="password" placeholder="••••••••" autocomplete="new-password" />
          </div>
          <div class="field">
            <label for="regSignupCode">Inspector Code <span style="color:#aaa;font-weight:400">(optional)</span></label>
            <input id="regSignupCode" type="text" placeholder="leave empty for customer account" />
            <div class="hint">Leave blank for customer account. Enter inspector code for controller role.</div>
          </div>
          <button class="submit-btn" id="registerBtn">Create Account</button>
          <div class="error-msg" id="registerError"></div>
          <div class="success-msg" id="registerSuccess"></div>
        </div>
      </div>
    </div>
    <!-- PASTE FULL SCRIPT BLOCK FROM ORIGINAL FILE HERE (extracted in step above) -->
  </body>
</html>
```

After writing the HTML structure, append the full `<script>...</script>` block from the original file (obtained from `sed` command above) before `</body>`.

- [ ] **Step 2: Test both tabs**

Open `http://localhost:8080/login`. Expected: white card on off-white bg, red top border, Playfair Display brand, "SIGN IN" / "CREATE ACCOUNT" uppercase tabs, red active underline.

Test login and register forms still work (submit calls `/api/auth/login` and `/api/auth/register`).

- [ ] **Step 3: Commit**

```
git add src/main/resources/templates/login.html
git commit -m "style: redesign login/register page — Clean Civic auth card"
```

---

## Task 5: analytics.html

**Files:**
- Modify: `src/main/resources/templates/analytics.html`

**What to change:** Replace `<style>` and HTML structure. Preserve all fetch JS.

- [ ] **Step 1: Read the current analytics.html JS to get exact API URLs**

Run: `grep -n "fetch(" src/main/resources/templates/analytics.html`

Note all fetch URLs and the element IDs they populate.

- [ ] **Step 2: Write new analytics.html**

Structure to implement (use exact element IDs that JS already uses — check in Step 1):

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NYC Hygiene Analytics</title>
  <th:block th:replace="fragments/ux-utils :: ux-utils"></th:block>
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
  <style>
    body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; }
    .page-header { background: #1a1a1a; color: white; padding: 32px 0; }
    .page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
    .page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }
    .kpi-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #c0392b; padding: 20px 24px; }
    .kpi-number { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 700; line-height: 1; color: #1a1a1a; }
    .kpi-number.green { color: #2e7d32; }
    .kpi-number.red { color: #c0392b; }
    .kpi-label { font-size: 0.72em; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: #888; margin-top: 6px; }
    .content-card { background: white; border: 1px solid #e8e0d8; padding: 24px; height: 100%; }
    .section-label { font-size: 0.68em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 6px; }
    .section-title { font-family: 'Playfair Display', serif; font-size: 1.4em; font-weight: 700; margin-bottom: 20px; }
    .borough-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
    .borough-name { width: 100px; font-size: 0.82em; font-weight: 600; flex-shrink: 0; }
    .borough-bar-wrap { flex: 1; background: #f0ebe3; border-radius: 1px; height: 8px; }
    .borough-bar { height: 8px; border-radius: 1px; background: #2e7d32; }
    .borough-pct { font-size: 0.75em; color: #888; width: 40px; text-align: right; flex-shrink: 0; }
    .rank-item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid #f0ebe3; }
    .rank-num { font-family: 'Playfair Display', serif; font-size: 1.1em; font-weight: 700; color: #c0392b; width: 24px; flex-shrink: 0; }
    .rank-name { font-size: 0.88em; font-weight: 600; flex: 1; }
    .rank-score { font-size: 0.82em; color: #888; }
    .rank-bar-wrap { width: 80px; background: #f0ebe3; border-radius: 1px; height: 4px; }
    .rank-bar { height: 4px; background: #c0392b; border-radius: 1px; }
    .risk-table { font-size: 0.84em; width: 100%; border-collapse: collapse; }
    .risk-table th { font-size: 0.72em; letter-spacing: 0.08em; text-transform: uppercase; color: #888; font-weight: 600; border-bottom: 2px solid #e8e0d8; padding: 10px 12px; text-align: left; }
    .risk-table td { padding: 10px 12px; border-bottom: 1px solid #f0ebe3; vertical-align: middle; }
    .risk-table tr:hover td { background: #faf7f4; }
    .badge-grade { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; font-weight: 900; font-size: 0.88em; border-radius: 2px; }
    .badge-c { background: #ffebee; color: #c62828; }
    .badge-z { background: #fff3e0; color: #e65100; }
    .civic-footer { background: #1a1a1a; color: rgba(255,255,255,0.45); padding: 24px 0; font-size: 0.78em; }
  </style>
</head>
<body>
  <div th:replace="fragments/navbar :: navbar"></div>

  <div class="page-header">
    <div class="container">
      <div class="eyebrow">City-Wide Statistics</div>
      <h1>NYC Hygiene Analytics</h1>
    </div>
  </div>

  <div style="background:#f8f5f0; padding:32px 0">
    <div class="container">
      <div class="row g-3 mb-4">
        <div class="col-6 col-md-3"><div class="kpi-card"><div class="kpi-number" id="kpi-total">—</div><div class="kpi-label">Total Restaurants</div></div></div>
        <div class="col-6 col-md-3"><div class="kpi-card"><div class="kpi-number green" id="kpi-pct">—</div><div class="kpi-label">Grade A</div></div></div>
        <div class="col-6 col-md-3"><div class="kpi-card"><div class="kpi-number" id="kpi-avg">—</div><div class="kpi-label">Avg Score</div></div></div>
        <div class="col-6 col-md-3"><div class="kpi-card"><div class="kpi-number red" id="kpi-risk">—</div><div class="kpi-label">At-Risk</div></div></div>
      </div>
      <div class="row g-3">
        <div class="col-md-5">
          <div class="content-card">
            <div class="section-label">Grade Distribution</div>
            <div class="section-title">By Borough</div>
            <div id="borough-bars"><div style="color:#aaa;font-size:0.88em">Loading…</div></div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="content-card">
            <div class="section-label">Worst Average Score</div>
            <div class="section-title">By Cuisine</div>
            <div id="cuisine-list"><div style="color:#aaa;font-size:0.88em">Loading…</div></div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="content-card">
            <div class="section-label">Needs Attention</div>
            <div class="section-title">At-Risk</div>
            <table class="risk-table">
              <thead><tr><th>Restaurant</th><th>Grade</th></tr></thead>
              <tbody id="atrisk-body"><tr><td colspan="2" style="color:#aaa">Loading…</td></tr></tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>

  <footer class="civic-footer">
    <div class="container d-flex justify-content-between">
      <div>NYC Restaurant Inspector · Data from NYC Open Data</div>
      <div style="font-family:monospace;font-size:0.85em;color:rgba(255,255,255,0.2)">v0.2.0</div>
    </div>
  </footer>

  <!-- PASTE FULL SCRIPT BLOCK FROM ORIGINAL FILE HERE -->
  <!-- Verify element IDs match: kpi-total, kpi-pct, kpi-avg, kpi-risk,
       borough-bars, cuisine-list, atrisk-body -->
  <!-- If the original JS uses different IDs, update EITHER the HTML IDs above
       OR the JS references — do not have mismatches -->
</body>
</html>
```

- [ ] **Step 3: Verify in browser**

Open `http://localhost:8080/analytics`. Expected: dark page header, 4 KPI cards, borough bars, cuisine ranking, at-risk table with grade badges.

- [ ] **Step 4: Commit**

```
git add src/main/resources/templates/analytics.html
git commit -m "style: redesign analytics page — KPI cards, borough bars, cuisine ranking"
```

---

## Task 6: index.html (dashboard)

**Files:**
- Modify: `src/main/resources/templates/index.html`

**Note:** Despite the filename `index.html`, this is the authenticated dashboard page (`/`). It shows bookmarks, nearby restaurants, and city KPI tiles.

- [ ] **Step 1: Read the full current index.html**

Run: `cat src/main/resources/templates/index.html`

Note element IDs used by JS: `bookmarks-grid`, `nearby-section`, `nearby-grid`, `kpi-total`, `kpi-pct`, `kpi-avg`, `kpi-risk`. Keep these IDs in the new HTML.

- [ ] **Step 2: Replace the file**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Dashboard — NYC Restaurant Inspector</title>
  <th:block th:replace="fragments/ux-utils :: ux-utils"></th:block>
  <style>
    body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; }
    .page-header { background: #1a1a1a; color: white; padding: 32px 0; }
    .page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
    .page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }
    .content-card { background: white; border: 1px solid #e8e0d8; padding: 24px; margin-bottom: 16px; }
    .section-label { font-size: 0.68em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 6px; }
    .section-title { font-family: 'Playfair Display', serif; font-size: 1.4em; font-weight: 700; margin-bottom: 20px; }
    .kpi-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #c0392b; padding: 20px 24px; }
    .kpi-number { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 700; line-height: 1; color: #1a1a1a; }
    .kpi-number.green { color: #2e7d32; }
    .kpi-number.amber { color: #f57f17; }
    .kpi-number.red { color: #c0392b; }
    .kpi-label { font-size: 0.72em; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: #888; margin-top: 6px; }
    .mini-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #e8e0d8; padding: 16px; transition: border-top-color 0.15s; }
    .mini-card:hover { border-top-color: #c0392b; }
    .civic-footer { background: #1a1a1a; color: rgba(255,255,255,0.45); padding: 24px 0; font-size: 0.78em; }
  </style>
</head>
<body>
  <div th:replace="fragments/navbar :: navbar"></div>

  <div class="page-header">
    <div class="container">
      <div class="eyebrow">Your Account</div>
      <h1>Your Dashboard</h1>
    </div>
  </div>

  <div style="padding: 32px 0;">
    <div class="container">
      <div class="content-card">
        <div class="section-label">Saved</div>
        <div class="section-title">Your Bookmarks</div>
        <div id="bookmarks-grid" class="row g-3">
          <div class="col-12"><div class="skel" style="height:80px;border-radius:2px"></div></div>
        </div>
      </div>

      <div id="nearby-section" class="content-card" style="display:none;">
        <div class="section-label">Location</div>
        <div class="section-title">Nearby</div>
        <div id="nearby-grid" class="row g-3"></div>
      </div>

      <div class="content-card">
        <div class="section-label">City-Wide</div>
        <div class="section-title">NYC Hygiene Overview</div>
        <div class="row g-3">
          <div class="col-6 col-md-3"><div class="kpi-card"><div class="kpi-number" id="kpi-total">—</div><div class="kpi-label">Total Restaurants</div></div></div>
          <div class="col-6 col-md-3"><div class="kpi-card"><div class="kpi-number green" id="kpi-pct">—</div><div class="kpi-label">% Grade A</div></div></div>
          <div class="col-6 col-md-3"><div class="kpi-card"><div class="kpi-number amber" id="kpi-avg">—</div><div class="kpi-label">Avg Score</div></div></div>
          <div class="col-6 col-md-3"><div class="kpi-card"><div class="kpi-number red" id="kpi-risk">—</div><div class="kpi-label">At-Risk Count</div></div></div>
        </div>
      </div>
    </div>
  </div>

  <footer class="civic-footer">
    <div class="container d-flex justify-content-between align-items-center">
      <div>NYC Restaurant Inspector · Data from NYC Open Data</div>
    </div>
  </footer>

  <!-- PASTE FULL SCRIPT BLOCK FROM ORIGINAL FILE HERE UNCHANGED -->
  <!-- The JS uses: token, fetchWithAuth, gradeBadgeHtml, renderRestaurantCards,
       bookmarks-grid, nearby-section, nearby-grid, kpi-total, kpi-pct, kpi-avg, kpi-risk -->
</body>
</html>
```

- [ ] **Step 3: Verify (must be logged in)**

Open `http://localhost:8080/`. Expected: dark header "Your Dashboard", bookmarks grid, KPI tiles with Playfair numbers.

- [ ] **Step 4: Commit**

```
git add src/main/resources/templates/index.html
git commit -m "style: redesign dashboard (index.html) — page header, content cards, KPI tiles"
```

---

## Task 7: restaurant.html

**Files:**
- Modify: `src/main/resources/templates/restaurant.html`

**What to change:** Replace `<style>` and HTML structure. Keep Leaflet, Chart.js, and all JS unchanged.

- [ ] **Step 1: Read the full current restaurant.html**

Run: `cat src/main/resources/templates/restaurant.html`

Note: the element IDs used by JS (restaurant name, cuisine, borough, address, score, trend, grade badge, map container, chart canvas, inspection tbody, bookmark button). You must use these exact same IDs in the new HTML structure.

- [ ] **Step 2: Replace style + structure, keep all JS**

New `<style>` block:

```css
body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; }
.page-header { background: #1a1a1a; color: white; padding: 32px 0; }
.page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: rgba(255,255,255,0.5); margin-bottom: 8px; }
.page-header h1 { font-family: 'Playfair Display', serif; font-size: 2em; font-weight: 900; margin: 0; word-break: break-word; }
.back-link { color: rgba(255,255,255,0.65); text-decoration: none; font-size: 0.82em; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
.back-link:hover { color: white; }
.content-card { background: white; border: 1px solid #e8e0d8; padding: 24px; margin-bottom: 16px; }
.section-label { font-size: 0.68em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 6px; }
.section-title { font-family: 'Playfair Display', serif; font-size: 1.3em; font-weight: 700; margin-bottom: 16px; }
.grade-badge-lg { display: inline-flex; align-items: center; justify-content: center; width: 64px; height: 64px; font-family: 'Playfair Display', serif; font-weight: 900; font-size: 2em; border-radius: 2px; float: right; margin-left: 16px; }
.grade-a-lg { background: #e8f5e9; color: #2e7d32; }
.grade-b-lg { background: #fff8e1; color: #f57f17; }
.grade-c-lg { background: #ffebee; color: #c62828; }
.trend-badge { display: inline-block; padding: 4px 10px; font-size: 0.78em; font-weight: 600; border-radius: 2px; }
.trend-improving { background: #e8f5e9; color: #2e7d32; }
.trend-worsening { background: #ffebee; color: #b71c1c; }
.trend-stable    { background: #f5f5f5; color: #757575; }
.insp-table { width: 100%; font-size: 0.84em; border-collapse: collapse; }
.insp-table th { font-size: 0.72em; letter-spacing: 0.08em; text-transform: uppercase; color: #888; font-weight: 600; border-bottom: 2px solid #e8e0d8; padding: 8px 12px; text-align: left; }
.insp-table td { padding: 8px 12px; border-bottom: 1px solid #f0ebe3; vertical-align: middle; }
.insp-table tr:hover td { background: #faf7f4; }
.btn-bookmark-civic { font-size: 0.82em; font-weight: 600; letter-spacing: 0.04em; text-transform: uppercase; border: 1px solid #c0392b; color: #c0392b; background: white; padding: 7px 18px; border-radius: 0; cursor: pointer; transition: background 0.15s, color 0.15s; }
.btn-bookmark-civic:hover, .btn-bookmark-civic.bookmarked { background: #c0392b; color: white; }
.civic-footer { background: #1a1a1a; color: rgba(255,255,255,0.45); padding: 24px 0; font-size: 0.78em; }
```

New HTML layout (the `id` attributes on `<h1>`, detail table cells, map div, chart canvas, tbody, bookmark button MUST match what the JS sets — use whatever IDs the JS uses, taken from Step 1):

```html
<div th:replace="fragments/navbar :: navbar"></div>

<div class="page-header">
  <div class="container">
    <div class="eyebrow"><a href="/" class="back-link">← Back to Search</a></div>
    <h1 id="[USE ID FROM ORIGINAL JS]">Loading…</h1>
  </div>
</div>

<div style="padding: 32px 0;">
  <div class="container">
    <div class="row g-3">
      <div class="col-md-5">
        <div class="content-card">
          <div class="section-label">Details</div>
          <div id="[GRADE BADGE ID FROM ORIGINAL]" style="float:right"></div>
          <div class="section-title" id="[RESTAURANT NAME CARD ID FROM ORIGINAL]">—</div>
          <!-- detail rows matching original IDs -->
          <div style="margin-top:16px">
            <button class="btn-bookmark-civic" id="bookmark-btn" style="display:none">☆ Bookmark</button>
          </div>
        </div>
        <div class="content-card">
          <div class="section-label">Location</div>
          <div id="map" style="height:240px"></div>
        </div>
      </div>
      <div class="col-md-7">
        <div class="content-card">
          <div class="section-label">Score History</div>
          <div class="section-title">Inspection Trend</div>
          <canvas id="[CHART CANVAS ID FROM ORIGINAL]" style="max-height:200px"></canvas>
        </div>
        <div class="content-card">
          <div class="section-label">Inspection Records</div>
          <div class="section-title">History</div>
          <div style="overflow-x:auto">
            <table class="insp-table">
              <thead><tr><th>Date</th><th>Score</th><th>Grade</th><th>Violations</th></tr></thead>
              <tbody id="[TBODY ID FROM ORIGINAL]"><tr><td colspan="4" style="color:#aaa;padding:16px 12px">Loading…</td></tr></tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>

<footer class="civic-footer">
  <div class="container d-flex justify-content-between align-items-center">
    <div>NYC Restaurant Inspector · Data from NYC Open Data</div>
  </div>
</footer>

<!-- PASTE FULL SCRIPT BLOCK FROM ORIGINAL FILE HERE UNCHANGED -->
```

Replace all `[USE ID FROM ORIGINAL JS]` placeholders with the actual IDs found in Step 1 — do not leave any placeholder text in the file.

- [ ] **Step 3: Verify in browser**

Open a restaurant detail page e.g. `http://localhost:8080/restaurant/30075445`. Expected: dark header with restaurant name, white cards, Leaflet map, score chart, inspection table.

- [ ] **Step 4: Commit**

```
git add src/main/resources/templates/restaurant.html
git commit -m "style: redesign restaurant detail page — Clean Civic two-column layout"
```

---

## Task 8: inspection-map.html

**Files:**
- Modify: `src/main/resources/templates/inspection-map.html`

**What to change:** Replace dark `#1a1a2e` toolbar and body with white navbar + white toolbar strip. Body keeps `height:100vh;display:flex;flex-direction:column` so map fills remaining space.

- [ ] **Step 1: Read the full current file**

Run: `cat src/main/resources/templates/inspection-map.html`

Note: the `#toolbar` div contains filter `<select>` elements and a `#status` span. Copy these elements exactly into the new toolbar.

- [ ] **Step 2: Replace `<style>` and page structure**

New `<style>`:

```css
body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; height: 100vh; display: flex; flex-direction: column; margin: 0; }
#toolbar { background: white; border-bottom: 1px solid #e8e0d8; padding: 10px 20px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; z-index: 1000; }
#toolbar-title { font-family: 'Playfair Display', serif; font-weight: 700; font-size: 1em; color: #1a1a1a; white-space: nowrap; margin-right: 4px; }
#toolbar select { padding: 6px 10px; background: white; border: 1px solid #e8e0d8; color: #1a1a1a; font-size: 0.85em; font-family: 'Inter', sans-serif; cursor: pointer; border-radius: 0; }
#toolbar select:focus { outline: none; border-color: #c0392b; }
#status { font-size: 0.78em; color: #888; margin-left: auto; white-space: nowrap; }
#map-wrapper { flex: 1; position: relative; }
#map { width: 100%; height: 100%; }
.legend { position: absolute; bottom: 30px; right: 10px; background: white; border: 1px solid #e8e0d8; padding: 12px 16px; z-index: 1000; font-size: 0.82em; }
.legend-item { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.legend-dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }
```

New HTML structure (keep original filter selects and script):

```html
<div th:replace="fragments/navbar :: navbar"></div>
<div id="toolbar">
  <div id="toolbar-title">NYC Inspection Map</div>
  <!-- PASTE ORIGINAL FILTER SELECT ELEMENTS HERE (borough, grade, cuisine selects from Step 1) -->
  <span id="status">Loading…</span>
</div>
<div id="map-wrapper">
  <div id="map"></div>
  <div class="legend">
    <div class="legend-item"><div class="legend-dot" style="background:#2e7d32"></div> Grade A</div>
    <div class="legend-item"><div class="legend-dot" style="background:#f57f17"></div> Grade B</div>
    <div class="legend-item"><div class="legend-dot" style="background:#c0392b"></div> Grade C / Z</div>
  </div>
</div>
<!-- PASTE FULL SCRIPT BLOCK FROM ORIGINAL FILE HERE UNCHANGED -->
```

- [ ] **Step 3: Verify in browser**

Open `http://localhost:8080/inspection-map`. Expected: Clean Civic navbar, white toolbar with filter selects, Leaflet map fills remaining height, legend bottom-right.

- [ ] **Step 4: Commit**

```
git add src/main/resources/templates/inspection-map.html
git commit -m "style: redesign inspection map — white toolbar, Clean Civic aesthetic"
```

---

## Task 9: my-bookmarks.html

**Files:**
- Modify: `src/main/resources/templates/my-bookmarks.html`

**What to change:** Replace `<style>` and structural HTML. All JS (pagination, gradeBadgeHtml, renderBookmarks, removeBookmark, fetchWithAuth) is preserved exactly as-is — just moved into the new structure.

- [ ] **Step 1: Replace the full file**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>My Bookmarks — NYC Restaurant Inspector</title>
    <th:block th:replace="fragments/ux-utils :: ux-utils"></th:block>
    <style>
      body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; }
      .page-header { background: #1a1a1a; color: white; padding: 32px 0; }
      .page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
      .page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }
      .content-card { background: white; border: 1px solid #e8e0d8; padding: 24px; margin-bottom: 16px; }
      .bm-item { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-bottom: 1px solid #f0ebe3; }
      .bm-item:last-child { border-bottom: none; }
      .bm-item:hover { background: #faf7f4; }
      .btn-bookmark { min-width: 36px; min-height: 36px; background: none; border: none; cursor: pointer; font-size: 1em; color: #f5a623; }
      .btn-view { text-decoration: none; color: #c0392b; font-weight: 600; font-size: 0.78em; white-space: nowrap; }
      .civic-footer { background: #1a1a1a; color: rgba(255,255,255,0.45); padding: 24px 0; font-size: 0.78em; }
    </style>
  </head>
  <body>
    <div th:replace="fragments/navbar :: navbar"></div>
    <div class="page-header">
      <div class="container">
        <div class="eyebrow">Your Account</div>
        <h1>My Bookmarks</h1>
      </div>
    </div>
    <div style="padding: 32px 0;">
      <div class="container" style="max-width: 760px;">
        <div class="content-card" id="bookmarks-card">
          <div id="bookmarks-list">
            <div class="skel" style="height:52px;border-radius:2px;margin-bottom:8px"></div>
            <div class="skel" style="height:52px;border-radius:2px;margin-bottom:8px"></div>
            <div class="skel" style="height:52px;border-radius:2px;margin-bottom:8px"></div>
          </div>
          <div id="pagination-controls" style="margin-top:8px"></div>
        </div>
      </div>
    </div>
    <footer class="civic-footer">
      <div class="container d-flex justify-content-between align-items-center">
        <div>NYC Restaurant Inspector · Data from NYC Open Data</div>
      </div>
    </footer>
    <!-- PASTE FULL SCRIPT BLOCK FROM ORIGINAL FILE HERE UNCHANGED -->
    <!-- JS uses: allBookmarks, currentPage, PAGE_SIZE, getAuthHeaders,
         handleFetchErrorResponse, fetchWithAuth, gradeBadgeHtml, renderBookmarks,
         renderBookmarkPage, renderPagination, goPage, removeBookmark, loadBookmarks,
         #bookmarks-list, #pagination-controls -->
  </body>
</html>
```

Note: the `.bm-item` class replaces the original `.top-restaurant-item`. Update the JS's `renderBookmarkPage` function to use `class="bm-item"` instead of the original class, OR keep the original class name in both HTML and JS.

Safest approach: keep the original class name from the JS — just update the CSS to use that class name with the new styles.

- [ ] **Step 2: Verify (must be logged in)**

Open `http://localhost:8080/my-bookmarks`. Expected: dark page header, white card with bookmark rows, pagination if > 20 items.

- [ ] **Step 3: Commit**

```
git add src/main/resources/templates/my-bookmarks.html
git commit -m "style: redesign bookmarks page — Clean Civic layout"
```

---

## Task 10: profile.html

**Files:**
- Modify: `src/main/resources/templates/profile.html`

- [ ] **Step 1: Read the current profile.html**

Run: `cat src/main/resources/templates/profile.html`

Note element IDs used by JS: `p-username`, `p-email`, `p-role`, `p-stats`, `bookmarks-grid`. Use these same IDs.

- [ ] **Step 2: Replace the file**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Your Profile — NYC Restaurant Inspector</title>
  <th:block th:replace="fragments/ux-utils :: ux-utils"></th:block>
  <style>
    body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; }
    .page-header { background: #1a1a1a; color: white; padding: 32px 0; }
    .page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
    .page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }
    .content-card { background: white; border: 1px solid #e8e0d8; padding: 24px; margin-bottom: 16px; }
    .section-label { font-size: 0.68em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 6px; }
    .section-title { font-family: 'Playfair Display', serif; font-size: 1.4em; font-weight: 700; margin-bottom: 20px; }
    .mini-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #e8e0d8; padding: 16px; transition: border-top-color 0.15s; }
    .mini-card:hover { border-top-color: #c0392b; }
    .error-msg { color: #c0392b; font-size: 0.85em; margin-top: 8px; }
    .civic-footer { background: #1a1a1a; color: rgba(255,255,255,0.45); padding: 24px 0; font-size: 0.78em; }
  </style>
</head>
<body>
  <div th:replace="fragments/navbar :: navbar"></div>
  <div class="page-header">
    <div class="container">
      <div class="eyebrow">Your Account</div>
      <h1>Your Profile</h1>
    </div>
  </div>
  <div style="padding: 32px 0;">
    <div class="container" style="max-width: 680px;">
      <div class="content-card text-center">
        <div style="font-size:3em;color:#e8e0d8;margin-bottom:16px">&#128100;</div>
        <div id="p-username" style="font-family:'Playfair Display',serif;font-size:1.8em;font-weight:700;margin-bottom:4px">—</div>
        <div id="p-email" style="font-size:0.9em;color:#888;margin-bottom:16px">—</div>
        <div id="p-role" style="margin-bottom:24px"></div>
        <div id="p-stats" style="display:flex;justify-content:center;gap:32px"></div>
      </div>
      <div class="content-card" id="bookmarks-section">
        <div class="section-label">Saved</div>
        <div class="section-title">Your Bookmarks</div>
        <div id="bookmarks-grid" class="row g-2">
          <div class="col-4"><div class="skel" style="height:80px;border-radius:2px"></div></div>
          <div class="col-4"><div class="skel" style="height:80px;border-radius:2px"></div></div>
          <div class="col-4"><div class="skel" style="height:80px;border-radius:2px"></div></div>
        </div>
      </div>
    </div>
  </div>
  <footer class="civic-footer">
    <div class="container d-flex justify-content-between align-items-center">
      <div>NYC Restaurant Inspector · Data from NYC Open Data</div>
    </div>
  </footer>
  <!-- PASTE FULL SCRIPT BLOCK FROM ORIGINAL FILE HERE UNCHANGED -->
</body>
</html>
```

- [ ] **Step 3: Verify (must be logged in)**

Open `http://localhost:8080/profile`. Expected: dark page header, white card, Playfair username display, bookmarks grid.

- [ ] **Step 4: Commit**

```
git add src/main/resources/templates/profile.html
git commit -m "style: redesign profile page — Clean Civic layout"
```

---

## Task 11: dashboard.html (controller)

**Files:**
- Modify: `src/main/resources/templates/dashboard.html`

- [ ] **Step 1: Read the full current dashboard.html**

Run: `cat src/main/resources/templates/dashboard.html`

Note all element IDs, tab structure, and fetch calls. You preserve all JS.

- [ ] **Step 2: Replace the `<style>` block**

Replace everything in `<style>...</style>` with:

```css
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'Inter', sans-serif; background: #f8f5f0; min-height: 100vh; }
.container { max-width: 860px; margin: 0 auto; padding: 24px 16px; }
.page-header { background: #1a1a1a; color: white; padding: 32px 0; }
.page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
.page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }
.card { background: white; border: 1px solid #e8e0d8; padding: 20px 24px; margin-bottom: 16px; }
.btn { cursor: pointer; border: none; padding: 7px 14px; font-size: 0.82em; font-weight: 600; font-family: 'Inter', sans-serif; border-radius: 0; }
.btn-primary { background: #c0392b; color: white; }
.btn-primary:hover { background: #a93226; }
.btn-secondary { background: #f0ebe3; color: #1a1a1a; border: 1px solid #e8e0d8; }
.btn-sm { padding: 4px 10px; font-size: 0.78em; }
.spinner { width: 32px; height: 32px; border: 3px solid #e8e0d8; border-top-color: #c0392b; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 32px auto; }
@keyframes spin { to { transform: rotate(360deg); } }
.tab { cursor: pointer; padding: 6px 16px; font-size: 0.85em; font-weight: 600; border: none; background: transparent; color: #888; border-bottom: 2px solid transparent; font-family: 'Inter', sans-serif; }
.tab.active { color: #c0392b; border-bottom-color: #c0392b; }
.report-card { background: #faf7f4; border: 1px solid #f0ebe3; padding: 10px 14px; margin-bottom: 8px; display: flex; align-items: center; gap: 10px; }
.edit-panel { background: #faf7f4; padding: 16px; margin-bottom: 8px; border-left: 4px solid #c0392b; }
.grade-btn { cursor: pointer; padding: 5px 12px; border: 2px solid #e8e0d8; font-weight: 700; font-size: 0.85em; background: white; font-family: 'Inter', sans-serif; border-radius: 0; }
.grade-btn.selected { border-color: #c0392b; background: #ffebee; color: #c0392b; }
label { font-size: 0.82em; font-weight: 600; color: #555; display: block; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.04em; }
input[type=text], select, textarea { width: 100%; padding: 7px 10px; border: 1px solid #e8e0d8; font-size: 0.88em; font-family: 'Inter', sans-serif; outline: none; border-radius: 0; }
input[type=text]:focus, select:focus, textarea:focus { border-color: #c0392b; }
.autocomplete-dropdown { position: absolute; z-index: 50; background: white; border: 1px solid #e8e0d8; box-shadow: 0 4px 16px rgba(0,0,0,0.1); max-height: 220px; overflow-y: auto; width: 100%; }
.autocomplete-item { padding: 9px 12px; cursor: pointer; font-size: 0.88em; }
.autocomplete-item:hover { background: #faf7f4; }
.error-msg { color: #c0392b; font-size: 0.82em; margin-top: 4px; }
@media (max-width: 768px) { .container { padding: 12px 8px !important; } .report-card { flex-wrap: wrap; } .edit-panel { padding: 12px; } }
```

- [ ] **Step 3: Replace the page header HTML**

Find the existing white-text heading (currently inside the gradient body: `<h1 style="color:white...">Inspector Dashboard</h1>`) and the `<div class="container" style="padding-top: 72px">` after the navbar.

Replace both with:

```html
<div class="page-header">
  <div class="container" style="padding-top:0;padding-bottom:0">
    <div class="eyebrow">Controller</div>
    <h1>Inspector Dashboard</h1>
  </div>
</div>
<div class="container" style="padding-top:32px">
```

- [ ] **Step 4: Verify (must be logged in as CONTROLLER)**

Open `http://localhost:8080/dashboard`. Expected: dark page header, white cards on off-white bg, red primary buttons, red tab underline.

- [ ] **Step 5: Commit**

```
git add src/main/resources/templates/dashboard.html
git commit -m "style: redesign controller dashboard — Clean Civic layout"
```

---

## Task 12: uncontrolled.html

**Files:**
- Modify: `src/main/resources/templates/uncontrolled.html`

- [ ] **Step 1: Replace `<style>` block**

```css
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'Inter', sans-serif; background: #f8f5f0; min-height: 100vh; }
.container { max-width: 1200px; margin: 0 auto; padding: 24px; }
.page-header { background: #1a1a1a; color: white; padding: 32px 0; }
.page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
.page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }
.page-header p { color: rgba(255,255,255,0.65); font-size: 0.88em; margin-top: 6px; }
.card { background: white; border: 1px solid #e8e0d8; padding: 24px; }
table { width: 100%; border-collapse: collapse; font-size: 0.84em; }
th { font-size: 0.72em; letter-spacing: 0.08em; text-transform: uppercase; color: #888; font-weight: 600; border-bottom: 2px solid #e8e0d8; padding: 10px 12px; text-align: left; }
td { padding: 10px 12px; border-bottom: 1px solid #f0ebe3; }
tr:hover td { background: #faf7f4; }
.grade-badge { display: inline-block; padding: 2px 8px; font-size: 0.82em; font-weight: 700; border-radius: 2px; }
.error-msg { color: #c0392b; padding: 16px; text-align: center; }
select { padding: 8px 12px; border: 1px solid #e8e0d8; font-size: 0.88em; background: white; color: #1a1a1a; font-family: 'Inter', sans-serif; border-radius: 0; }
@media (max-width: 768px) { .container { padding: 12px 8px !important; } }
```

- [ ] **Step 2: Replace the header strip**

Find the existing `<div style="background: rgba(255,255,255,0.15)...">` header strip (the one inside the gradient body) and replace it with:

```html
<div class="page-header">
  <div class="container" style="padding-top:0;padding-bottom:0">
    <div class="eyebrow">Controller</div>
    <h1>Uncontrolled Restaurants</h1>
    <p>Restaurants with grade C/Z or no inspection in the past 12 months</p>
  </div>
</div>
<div class="container" style="padding-top:24px">
```

Remove `style="padding-top: 72px"` from the original container.

- [ ] **Step 3: Verify (must be logged in as CONTROLLER)**

Open `http://localhost:8080/uncontrolled`. Expected: dark page header, white card table on off-white bg.

- [ ] **Step 4: Commit**

```
git add src/main/resources/templates/uncontrolled.html
git commit -m "style: redesign uncontrolled restaurants page — Clean Civic layout"
```

---

## Task 13: admin.html

**Files:**
- Modify: `src/main/resources/templates/admin.html`

- [ ] **Step 1: Replace `<style>` block**

```css
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'Inter', sans-serif; background: #f8f5f0; min-height: 100vh; }
.container { max-width: 860px; margin: 0 auto; padding: 24px 16px; }
.page-header { background: #1a1a1a; color: white; padding: 32px 0; }
.page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 8px; }
.page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }
.card { background: white; border: 1px solid #e8e0d8; padding: 20px 24px; margin-bottom: 20px; }
.card h2 { font-size: 1.05em; font-weight: 700; color: #1a1a1a; margin-bottom: 12px; font-family: 'Playfair Display', serif; }
.btn { cursor: pointer; border: none; padding: 7px 14px; font-size: 0.82em; font-weight: 600; font-family: 'Inter', sans-serif; border-radius: 0; }
.btn-primary { background: #c0392b; color: white; }
.btn-primary:hover { background: #a93226; }
.btn-secondary { background: #f0ebe3; color: #1a1a1a; border: 1px solid #e8e0d8; }
.spinner { display: inline-block; width: 16px; height: 16px; border: 2px solid rgba(192,57,43,0.3); border-top-color: #c0392b; border-radius: 50%; animation: spin 0.8s linear infinite; vertical-align: middle; margin-right: 6px; }
@keyframes spin { to { transform: rotate(360deg); } }
.status-text { font-size: 0.85em; color: #888; margin-top: 8px; }
.sync-result { display: none; font-size: 0.85em; font-weight: 600; margin-top: 8px; }
.btn-row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.desc-text { font-size: 0.88em; color: #555; margin-bottom: 14px; }
.badge-row { display: flex; flex-wrap: wrap; gap: 4px; margin: 6px 0; align-items: center; }
.badge-label { font-size: 0.82em; font-weight: 600; color: #555; min-width: 80px; }
@media (max-width: 768px) { .container { padding: 12px 8px !important; } .btn-row { flex-direction: column; align-items: flex-start; } }
```

- [ ] **Step 2: Replace the page heading**

Find `<div style="margin-bottom: 20px"><h1 style="color:white...">Admin Panel</h1>...` and `<div class="container" style="padding-top: 72px">`.

Replace with:

```html
<div class="page-header">
  <div class="container" style="padding-top:0;padding-bottom:0">
    <div class="eyebrow">System</div>
    <h1>Admin Panel</h1>
  </div>
</div>
<div class="container" style="padding-top:32px">
```

- [ ] **Step 3: Verify (must be logged in as ADMIN)**

Open `http://localhost:8080/admin`. Expected: dark page header, white cards on off-white bg, red primary buttons.

- [ ] **Step 4: Commit**

```
git add src/main/resources/templates/admin.html
git commit -m "style: redesign admin panel — Clean Civic layout"
```

---

## Task 14: Final checks + .gitignore

**Files:**
- Modify: `.gitignore`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add .superpowers to .gitignore**

Run: `grep -n "superpowers" .gitignore` — if not present, add it:

```
echo '' >> .gitignore
echo '# Superpowers visual companion' >> .gitignore
echo '.superpowers/' >> .gitignore
```

- [ ] **Step 2: Run full test suite**

Run: `mvn test -q 2>&1 | tail -5`

Expected: `BUILD SUCCESS` — all 166 tests pass.

- [ ] **Step 3: Visual smoke test**

Run the app and verify no purple gradient appears anywhere:

```
mvn spring-boot:run -q &
```

Check these pages:
- `http://localhost:8080/` — hero + stat strip
- `http://localhost:8080/login` — auth card
- `http://localhost:8080/analytics` — KPI cards
- `http://localhost:8080/inspection-map` — white toolbar
- `http://localhost:8080/my-bookmarks` (logged in) — bookmark list

- [ ] **Step 4: Update CHANGELOG.md**

Add under `## [Unreleased]`:

```
### Frontend Redesign (2026-05-02)
- Replace purple-gradient aesthetic with Clean Civic design system across all 11 templates and 2 fragments
- Bootstrap 5.3.3 + Playfair Display/Inter typography loaded via CDN in ux-utils fragment
- White navbar with 3px red bottom border, dark page headers (#1a1a1a), off-white page background (#f8f5f0)
- KPI cards with red top border and Playfair Display numbers on analytics, dashboard, landing pages
- Grade badges (A/B/C) with semantic colors (green/amber/red) consistent across all pages
```

- [ ] **Step 5: Final commit**

```
git add .gitignore CHANGELOG.md
git commit -m "chore: add .superpowers to .gitignore; update CHANGELOG for frontend redesign"
```
