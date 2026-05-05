# Design: Elasticsearch Search, Navbar Fix, OSM Enrichment

**Date:** 2026-05-05  
**Status:** Approved

---

## Overview

Three independent improvements:

1. **Elasticsearch + Autocomplete** — replace MongoDB text search with ES for relevance, speed, and autocomplete UX
2. **Navbar consistency fix** — self-contained navbar fragment immune to per-page CSS drift
3. **OpenStreetMap enrichment** — background job enriching restaurant documents with phone, website, opening hours from OSM

---

## Feature 1: Elasticsearch + Autocomplete

### Architecture

Elasticsearch 8 runs as a 5th Docker service alongside MongoDB, PostgreSQL, Redis, and the app. It is a **read-only replica** — MongoDB remains the single source of truth. A new `ElasticsearchSyncService` pushes restaurant documents to ES after every sync cycle and once at startup if the index is empty.

### ES Index & Mapping

- Index name: `restaurants`
- Fields indexed: `camis`, `dba` (restaurant name), `cuisine_description`, `boro`, `street`, `zipcode`
- Analyzer: `standard` on all text fields
- Boost: `dba^3`, `cuisine_description^2`, `street^1`, `boro^1`

### New Endpoint

`GET /api/restaurants/autocomplete?q=&limit=`

- No authentication required
- Backed by a `multi_match` query with `fuzziness: AUTO`
- Returns up to 10 suggestions: `{ camis, dba, cuisine_description, boro, street }`

### Frontend Behavior

- Debounced 250ms, triggers at minimum 2 characters
- Dropdown appears below the search bar with up to 8 suggestions
- Restaurant name matches → click navigates to `/restaurant/{camis}`
- Cuisine / address matches → click fills the search bar and triggers existing full search
- Keyboard navigation (↑↓ arrows, Enter, Escape) supported

### Sync Flow

```
SyncService (after NYC import)
  └─> ElasticsearchSyncService.reindex()
        └─> bulk-index all MongoDB restaurants into ES
```

On app startup: if ES index has 0 documents, trigger `reindex()` automatically.

### Docker

```yaml
elasticsearch:
  image: elasticsearch:8.x
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false
  mem_limit: 512m
```

New env var on app container: `ELASTICSEARCH_URI` (default: `http://elasticsearch:9200`).

### New Java components

| Component | Location | Responsibility |
|---|---|---|
| `ElasticsearchConfig` | `config/` | Build `ElasticsearchClient` (ES Java API Client 8.x) from `ELASTICSEARCH_URI` |
| `ElasticsearchSyncService` | `sync/` | Reindex all restaurants into ES after sync |
| Autocomplete method | `RestaurantDAO` / `AnalyticsDAO` | Execute `multi_match` query against ES |
| `GET /api/restaurants/autocomplete` | `RestaurantController` | Autocomplete endpoint |
| Autocomplete dropdown | `index.html` | Debounced input handler + dropdown UI |

---

## Feature 2: Navbar Consistency Fix

### Problem

Each page defines its own `.container` CSS class with varying `max-width` and `padding`. The navbar fragment uses `<div class="container">`, inheriting whichever override is active on that page — causing alignment and width to shift across pages.

### Fix

Replace `<div class="container">` inside `fragments/navbar.html` with `<div class="navbar-container">`. Define `.navbar-container` once inside the navbar fragment's own `<style>` block:

```css
.navbar-container {
  max-width: 860px;
  margin: 0 auto;
  padding: 0 16px;
  width: 100%;
}
```

### Scope

- Only `fragments/navbar.html` changes
- All pages (`admin.html`, `my-bookmarks.html`, `dashboard.html`, `analytics.html`, etc.) pick up the fix automatically via `th:replace`
- No page templates need to be touched

---

## Feature 3: OpenStreetMap Enrichment

### Data Source

**Overpass API** — free, no API key required, rate limit: 1 request/second.

### Data Fetched

Per restaurant (where available):
- `osmPhone`
- `osmWebsite`
- `osmOpeningHours`
- `osmEnrichedAt` (timestamp, used to skip already-processed restaurants)

**Photos are out of scope** — Wikimedia Commons tags exist on some OSM nodes but NYC restaurant coverage is near zero.

### MongoDB Storage

New optional fields added directly to the `Restaurant` document. No migration needed (schemaless). Fields are absent rather than null when not enriched.

### Matching Strategy

Query Overpass with `[out:json]` filtering by `amenity=restaurant` within a bounding box for the restaurant's borough. Match candidates by normalizing both names to lowercase with punctuation stripped. Take the best match above a similarity threshold.

### `OsmEnrichmentService`

- `enrichNew()` — processes only restaurants where `osmEnrichedAt` is null; called by `SyncService` after each import
- `enrichAll()` — full re-enrichment; triggered via admin endpoint only
- Rate limiting: `Thread.sleep(1000)` between Overpass requests

### Sync Integration

```
SyncService (after NYC import)
  └─> OsmEnrichmentService.enrichNew()
        └─> Overpass API (1 req/s)
              └─> MongoDB update (osmPhone, osmWebsite, osmOpeningHours, osmEnrichedAt)
```

### Admin Endpoint

`POST /api/admin/osm-enrich` — triggers `enrichAll()` in a background thread. Requires `ROLE_ADMIN`.

### Frontend

On `restaurant.html`, show a "Contact" section if any OSM field is present:
- Phone → `<a href="tel:...">` link
- Website → external link with `target="_blank"`
- Opening hours → plain text

Section is hidden entirely when no OSM data is available.

### New Java Components

| Component | Location | Responsibility |
|---|---|---|
| `OsmEnrichmentService` | `sync/` | Overpass queries, name matching, MongoDB updates |
| `POST /api/admin/osm-enrich` | `AdminController` | Trigger full re-enrichment |
| OSM fields | `Restaurant.java` | New optional fields |
| Contact section | `restaurant.html` | Conditional display of OSM data |

### Known Limitations

- Estimated ~30–40% match rate for NYC restaurants in OSM
- No photos available via OSM for NYC
- Full enrichment of 50k+ restaurants takes several hours; runs silently in the background

---

## Implementation Order

1. **Navbar fix** — isolated, 5-minute change, zero risk
2. **OSM enrichment** — self-contained background service, no new infrastructure
3. **Elasticsearch** — most complex, new Docker service, saved for last
