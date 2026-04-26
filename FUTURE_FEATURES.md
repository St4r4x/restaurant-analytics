# Future Features

## Analytics & Data

- **Trend analysis** — grade evolution over time per restaurant (sparkline charts)
- **Heatmap by violation type** — map overlay showing density of specific health code violations
- **Seasonal patterns** — inspection score trends by month/season across the city
- **Cuisine risk index** — aggregate risk score per cuisine type based on historical violations
- **Neighborhood rankings** — health score leaderboard at ZIP code / neighborhood level

## User Features

- **Notifications** — alert users when a bookmarked restaurant gets a new grade or inspection
- **Restaurant comparison** — side-by-side view of two restaurants (grade history, violation count)
- **Personal inspection history** — timeline of all restaurants a user has visited/bookmarked
- **Public reports feed** — anonymized stream of recent controller reports (opt-in)

## Search & Discovery

- **Advanced filters** — combine borough + cuisine + grade + distance in one query
- **"Clean near me"** — geolocation-based search returning only grade A within N km
- **Voice search** — Web Speech API integration for hands-free restaurant lookup
- **Offline mode** — PWA with service worker caching last-viewed restaurants

## Infrastructure

- **WebSocket live updates** — push new inspection results to open dashboard sessions in real time
- **Multi-language support** — i18n for the frontend (French, Spanish at minimum)
- **Export to PDF** — generate a printable inspection report card per restaurant
- **Rate limiting per user** — replace global IP rate limit with per-JWT token bucket
- **Dark mode** — CSS custom properties toggle, persisted in localStorage
