# Frontend Redesign — Design Spec

## Goal

Replace the purple-gradient aesthetic across all Thymeleaf templates with a consistent "Clean Civic" design system: white navbar + red accent, Playfair Display + Inter typography, off-white page background.

## Design System Tokens

### Colors
```
--color-bg:        #f8f5f0   /* page background */
--color-surface:   #ffffff   /* card / navbar background */
--color-border:    #e8e0d8   /* light border */
--color-border-alt:#f0ebe3   /* row separator */
--color-text:      #1a1a1a   /* primary text */
--color-text-muted:#888888   /* secondary text */
--color-red:       #c0392b   /* brand accent */
--color-dark:      #1a1a1a   /* hero / footer background */
--color-green:     #2e7d32   /* grade A / good */
--color-amber:     #f57f17   /* grade B / warning */
```

### Typography
- **Display**: `Playfair Display` (weights 700, 900) — brand name, section titles, KPI numbers
- **Body**: `Inter` (weights 400, 500, 600) — all other text

CDN import (shared via `ux-utils.html`):
```html
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
```

### Bootstrap
Bootstrap 5.3.3 via CDN (`bootstrap.min.css` + `bootstrap.bundle.min.js`), loaded in `ux-utils.html`.  
`Chart.js` 4.4.0 stays as-is (analytics page only).

---

## Shared Fragments

### `fragments/ux-utils.html`
Keep existing JS (version badge, token refresh, etc.). Add:
- Bootstrap 5.3.3 CSS CDN link
- Google Fonts import
- Global CSS block: `body { font-family: 'Inter', sans-serif; background: #f8f5f0; color: #1a1a1a; }`
- Remove any old inline `<style>` that sets body background to the gradient

### `fragments/navbar.html`
Replace gradient navbar with Bootstrap navbar:
```
- bg: white, border-bottom: 3px solid #c0392b, position: sticky top-0
- Brand: "NYC Restaurant Inspector" in Playfair Display 900 + sub-line "Health Department Data" in Inter uppercase red
- Links: uppercase Inter 600, 0.82em, color #444, active = color #c0392b + 3px red bottom border
- Auth area: "Sign In" button with bg #c0392b, white text, no border-radius
- Hamburger: Bootstrap navbar-toggler, collapses to vertical menu on mobile
- Active-link JS and auth-state JS keep the same logic, updated to Bootstrap classes
```

---

## Page-by-Page Specifications

### `landing.html`
Current: purple gradient full-page background, plain card.  
New layout:
1. **Hero section** — `bg: #1a1a1a`, padding 72px 0 64px, diagonal hatch overlay (repeating-linear-gradient with red at 6% opacity)
   - Eyebrow: `NEW YORK CITY · OPEN DATA · REAL-TIME` — red, 0.72em, letter-spacing 0.15em
   - H1: "Know before you **eat.**" — Playfair Display 900, 3.2em, `em` text in red
   - Sub-text: rgba(255,255,255,0.65)
   - Search bar: full-width input (no border-radius) + red "Search" button flush right
2. **Stat strip** — `bg: #c0392b`, white text, 4 KPI columns (Restaurants, Grade A%, Avg Score, At-Risk) with vertical dividers, Playfair Display numbers
3. **Recently Inspected** section — off-white bg, section-label + section-title pattern, 3-column Bootstrap grid of restaurant cards
   - Cards: white, `border-top: 3px solid #e8e0d8`, hover → `border-top-color: #c0392b`
   - Grade badge floated right: 36×36, bg tinted per grade (A=green, B=amber, C=red)
4. **Footer** — `bg: #1a1a1a`, white/40% text, version badge monospace

### `index.html` (search page)
Current: purple gradient, single card layout.  
New layout:
1. **Page header** — dark `#1a1a1a` strip, eyebrow + H1 "Find a Restaurant"
2. **Search bar** — prominent, same hero-style search input + red button
3. **Results grid** — Bootstrap row of restaurant cards (same card pattern as landing). Empty state: italic muted text.

### `analytics.html`
Current: purple gradient, white cards with blue accent.  
New layout (matches `layout-analytics.html` mockup):
1. **Page header** — dark strip, eyebrow "City-Wide Statistics", H1 "NYC Hygiene Analytics"
2. **KPI row** — 4 cards, `border-top: 3px solid #c0392b`, Playfair numbers; green/red coloring for grade A% / at-risk count
3. **Row 2 (3 columns)**:
   - **Borough Grade Distribution** (col-md-5): horizontal bar chart per borough, bars colored green/amber/red by % grade A
   - **Worst Cuisines by Avg Score** (col-md-4): numbered ranking list with small red progress bars
   - **At-Risk Restaurants** (col-md-3): compact table, grade badges (C=red tint, Z=amber tint)
4. Chart.js `<canvas>` for borough donut chart if desired (optional, keep if already present)

### `login.html`
Current: purple gradient page, centered white card.  
New: center column on off-white bg
- `border-top: 3px solid #c0392b` on card
- Brand logo in Playfair Display above the form
- Input fields: Bootstrap form-control
- Submit button: `bg: #c0392b`, full-width

### `restaurant.html` (detail page)
Current: unknown styling (not yet read in full).  
New:
- Page header strip (dark) with restaurant name as H1
- Grade badge prominently displayed (large, 64×64)
- Details in 2-column Bootstrap grid: left = address/cuisine/borough, right = inspection history table
- Bookmark button: red outlined button → red filled on active

### `inspection-map.html`
Current: purple gradient header, Leaflet map.  
New:
- Page header strip (dark), H1 "Inspection Map"
- Map container below, full-width, no background change needed
- Filter bar above map: Bootstrap form-inline, off-white bg

### `my-bookmarks.html`
Current: unknown styling.  
New:
- Page header strip (dark), H1 "My Bookmarks"
- Bootstrap table or card grid of bookmarked restaurants
- Empty state: centered muted text + CTA button (red)

### `profile.html`
Current: unknown styling.  
New:
- Page header strip, H1 "Your Profile"
- Bootstrap card with form fields (username, email, change password section)
- Save button: red

### `dashboard.html` (controller role)
Current: unknown styling.  
New:
- Page header strip, H1 "Controller Dashboard"
- KPI cards (same pattern)
- Table of pending/uncontrolled restaurants

### `uncontrolled.html`
Current: unknown styling.  
New:
- Page header strip, H1 "Uncontrolled Restaurants"
- Filterable Bootstrap table

### `admin.html`
Current: unknown styling.  
New:
- Page header strip, H1 "Admin Panel"
- Tabbed layout (Bootstrap nav-tabs): Users | Sync | System
- Tables with action buttons (outline-red for danger actions)

---

## Shared CSS Patterns

```css
/* Added inline in each template OR in ux-utils.html global block */

.navbar-civic { background: #fff; border-bottom: 3px solid #c0392b; }

.page-header { background: #1a1a1a; color: white; padding: 32px 0; }
.page-header .eyebrow { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; }
.page-header h1 { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 900; margin: 0; }

.kpi-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #c0392b; padding: 20px 24px; }
.kpi-number { font-family: 'Playfair Display', serif; font-size: 2.2em; font-weight: 700; line-height: 1; }
.kpi-label { font-size: 0.72em; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: #888; margin-top: 6px; }

.content-card { background: white; border: 1px solid #e8e0d8; padding: 24px; }
.section-label { font-size: 0.7em; font-weight: 700; letter-spacing: 0.15em; text-transform: uppercase; color: #c0392b; margin-bottom: 6px; }
.section-title { font-family: 'Playfair Display', serif; font-size: 1.4em; font-weight: 700; margin-bottom: 20px; }

.rest-card { background: white; border: 1px solid #e8e0d8; border-top: 3px solid #e8e0d8; padding: 20px; transition: border-top-color 0.15s; cursor: pointer; }
.rest-card:hover { border-top-color: #c0392b; }

.grade-badge { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; font-weight: 900; border-radius: 2px; }
.grade-a { background: #e8f5e9; color: #2e7d32; }
.grade-b { background: #fff8e1; color: #f57f17; }
.grade-c { background: #ffebee; color: #c62828; }

footer.civic-footer { background: #1a1a1a; color: rgba(255,255,255,0.45); padding: 24px 0; font-size: 0.78em; }
```

These classes will be defined in a `<style>` block in each template (to keep templates self-contained and avoid a separate CSS file, consistent with existing approach).

---

## Constraints

- All templates remain Thymeleaf (`xmlns:th`, `th:replace`, `th:text`, etc.) — no framework change
- No separate `.css` file — styles inline per template, shared utilities in `ux-utils.html`
- Bootstrap loaded via CDN in `ux-utils.html` — no npm/build step
- JS logic (auth state, active link, fetch calls) unchanged — only CSS/HTML structure changes
- `Chart.js` retained on `analytics.html` and `restaurant.html`
- `Leaflet` retained on `inspection-map.html` and `restaurant.html`
- Version badge in `ux-utils.html` unchanged

---

## Out of Scope

- Backend changes
- API changes
- New features
- Dark mode toggle
