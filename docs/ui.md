# UI & Frontend

## Pages

| URL | Auth Required | Description |
|-----|--------------|-------------|
| `/` | None (anonymous) | Landing: hero, stat strip, search bar, sample restaurants |
| `/` | JWT present | Customer dashboard: bookmarks strip + KPI tiles |
| `/login` | None | Login / Register tabs |
| `/profile` | Required | Role badge, bookmark count, report count (controllers only) |
| `/analytics` | None | City-wide analytics: KPI tiles, borough chart, cuisine rankings, at-risk table |
| `/dashboard` | CONTROLLER | Inspector dashboard: reports list, status tabs, New Report modal |
| `/restaurant/{camis}` | None | Restaurant detail: grade badge, score chart, inspection history timeline |
| `/inspection-map` | None | Interactive grade-coloured Leaflet map with marker clustering |
| `/my-bookmarks` | Required | Saved restaurants grid |
| `/admin` | ADMIN | Admin panel: sync controls, at-risk CSV download, report statistics |

All pages include a persistent sticky navbar (Logo · Search · Map · Analytics · auth area).  
All pages include `fragments/ux-utils.html` which provides skeleton shimmer CSS and the `showToast()` notification system.  
All pages are mobile-responsive at ≤ 768 px via a hamburger navbar and responsive grid breakpoints.

---

## Design System — Clean Civic

| Token | Value | Usage |
|-------|-------|-------|
| Background | `#f8f5f0` | Page background |
| Surface | `#ffffff` | Cards, modals |
| Primary text | `#1a1a1a` | Body copy |
| Accent / brand red | `#c0392b` | CTAs, active states, badges |
| Hero / footer bg | `#1a1a1a` | Dark sections |
| Grade A | `#2e7d32` | Green badge |
| Grade B | `#f57f17` | Amber badge |
| Grade C / Z | `#c62828` | Red badge |

### Typography

| Role | Font | Weight |
|------|------|--------|
| Headings, KPI numbers | Playfair Display | 700 / 900 |
| Body, labels, buttons | Inter | 400 / 500 / 600 |

Both fonts are loaded via Google Fonts CDN in `fragments/ux-utils.html`.

### CSS Approach

Bootstrap 5.3.3 is loaded via CDN. There is no separate compiled `.css` file — all component styles are written inline per Thymeleaf template. This keeps each template self-contained.

---

## Thymeleaf Templates

Templates live in `src/main/resources/templates/`.

| File | Page |
|------|------|
| `index.html` | Landing / customer dashboard |
| `login.html` | Login / register |
| `profile.html` | User profile |
| `analytics.html` | Analytics dashboard |
| `dashboard.html` | Inspector dashboard |
| `restaurant.html` | Restaurant detail |
| `map.html` | Inspection map |
| `bookmarks.html` | My bookmarks |
| `admin.html` | Admin panel |
| `fragments/navbar.html` | Sticky navbar fragment |
| `fragments/ux-utils.html` | Shared CSS + JS utilities |
