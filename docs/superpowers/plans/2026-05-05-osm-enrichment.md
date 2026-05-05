# OSM Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrich MongoDB restaurant documents with phone, website, and opening hours from OpenStreetMap's Overpass API, running as a background job after each sync cycle, with results shown on the restaurant detail page.

**Architecture:** `OsmEnrichmentService` queries the Overpass API (1 req/s rate limit), matches by name normalization, and patches MongoDB documents with `osmPhone`, `osmWebsite`, `osmOpeningHours`, `osmEnrichedAt` fields. `SyncService` calls `enrichNew()` after each import. An admin endpoint triggers full re-enrichment. The restaurant detail page shows a Contact section when any OSM field is present.

**Tech Stack:** Java 25, Spring Boot 4, MongoDB driver (raw `Document` updates, no POJO), Overpass API (`https://overpass-api.de/api/interpreter`), Jackson for JSON parsing, `RestTemplate` for HTTP

---

### Task 1: Add OSM fields to Restaurant domain class

**Files:**
- Modify: `src/main/java/com/st4r4x/domain/Restaurant.java`

- [ ] **Step 1: Write a failing test**

Create `src/test/java/com/st4r4x/domain/RestaurantOsmFieldsTest.java`:

```java
package com.st4r4x.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class RestaurantOsmFieldsTest {

    @Test
    void osmFields_defaultToNull() {
        Restaurant r = new Restaurant();
        assertThat(r.getOsmPhone()).isNull();
        assertThat(r.getOsmWebsite()).isNull();
        assertThat(r.getOsmOpeningHours()).isNull();
        assertThat(r.getOsmEnrichedAt()).isNull();
    }

    @Test
    void osmFields_roundTrip() {
        Restaurant r = new Restaurant();
        Instant now = Instant.now();
        r.setOsmPhone("+1-212-555-0100");
        r.setOsmWebsite("https://example.com");
        r.setOsmOpeningHours("Mo-Fr 11:00-22:00");
        r.setOsmEnrichedAt(now);

        assertThat(r.getOsmPhone()).isEqualTo("+1-212-555-0100");
        assertThat(r.getOsmWebsite()).isEqualTo("https://example.com");
        assertThat(r.getOsmOpeningHours()).isEqualTo("Mo-Fr 11:00-22:00");
        assertThat(r.getOsmEnrichedAt()).isEqualTo(now);
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -Dtest=RestaurantOsmFieldsTest -q 2>&1 | tail -10
```
Expected: compilation error (methods not found).

- [ ] **Step 3: Add OSM fields to Restaurant.java**

In `src/main/java/com/st4r4x/domain/Restaurant.java`, after the existing `grades` field and before the constructors, add:

```java
@BsonProperty("osm_phone")
private String osmPhone;

@BsonProperty("osm_website")
private String osmWebsite;

@BsonProperty("osm_opening_hours")
private String osmOpeningHours;

@BsonProperty("osm_enriched_at")
private java.time.Instant osmEnrichedAt;
```

At the bottom of the class, before the closing `}`, add getters and setters:

```java
public String getOsmPhone() { return osmPhone; }
public void setOsmPhone(String osmPhone) { this.osmPhone = osmPhone; }

public String getOsmWebsite() { return osmWebsite; }
public void setOsmWebsite(String osmWebsite) { this.osmWebsite = osmWebsite; }

public String getOsmOpeningHours() { return osmOpeningHours; }
public void setOsmOpeningHours(String osmOpeningHours) { this.osmOpeningHours = osmOpeningHours; }

public java.time.Instant getOsmEnrichedAt() { return osmEnrichedAt; }
public void setOsmEnrichedAt(java.time.Instant osmEnrichedAt) { this.osmEnrichedAt = osmEnrichedAt; }
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -Dtest=RestaurantOsmFieldsTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/domain/Restaurant.java \
        src/test/java/com/st4r4x/domain/RestaurantOsmFieldsTest.java
git commit -m "feat(osm): add osm enrichment fields to Restaurant domain"
```

---

### Task 2: Create OsmEnrichmentService

**Files:**
- Create: `src/main/java/com/st4r4x/sync/OsmEnrichmentService.java`
- Create: `src/test/java/com/st4r4x/sync/OsmEnrichmentServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/st4r4x/sync/OsmEnrichmentServiceTest.java`:

```java
package com.st4r4x.sync;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OsmEnrichmentServiceTest {

    @Test
    void normalizeName_lowercasesAndStripsNonAlpha() {
        assertThat(OsmEnrichmentService.normalizeName("Joe's Pizza!")).isEqualTo("joes pizza");
        assertThat(OsmEnrichmentService.normalizeName("THE GOLDEN DRAGON")).isEqualTo("the golden dragon");
        assertThat(OsmEnrichmentService.normalizeName(null)).isEqualTo("");
        assertThat(OsmEnrichmentService.normalizeName("  Cafe  ")).isEqualTo("cafe");
    }

    @Test
    void similarity_exactMatch_returns1() {
        assertThat(OsmEnrichmentService.similarity("joes pizza", "joes pizza")).isEqualTo(1.0);
    }

    @Test
    void similarity_completelyDifferent_returnsLow() {
        assertThat(OsmEnrichmentService.similarity("joes pizza", "dragon palace")).isLessThan(0.4);
    }

    @Test
    void similarity_closeMatch_returnsHigh() {
        assertThat(OsmEnrichmentService.similarity("joes pizza", "joe pizza")).isGreaterThan(0.8);
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
mvn test -Dtest=OsmEnrichmentServiceTest -q 2>&1 | tail -10
```
Expected: compilation error (class not found).

- [ ] **Step 3: Create OsmEnrichmentService.java**

Create `src/main/java/com/st4r4x/sync/OsmEnrichmentService.java`:

```java
package com.st4r4x.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.st4r4x.config.MongoClientFactory;
import com.st4r4x.config.AppConfig;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

@Service
public class OsmEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(OsmEnrichmentService.class);
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final double SIMILARITY_THRESHOLD = 0.75;

    private final MongoCollection<Document> collection;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public OsmEnrichmentService(MongoClientFactory mongoClientFactory, AppConfig appConfig) {
        MongoDatabase db = mongoClientFactory.getClient().getDatabase(appConfig.getMongoDatabase());
        this.collection = db.getCollection(appConfig.getMongoCollection());
        this.restTemplate = new RestTemplate();
    }

    /** Enrich only restaurants not yet enriched. Called after each sync. */
    public void enrichNew() {
        List<Document> pending = collection
                .find(not(exists("osm_enriched_at")))
                .projection(new Document("restaurant_id", 1).append("name", 1).append("borough", 1))
                .into(new ArrayList<>());

        logger.info("OSM enrichment: {} restaurants pending", pending.size());
        enrichList(pending);
    }

    /** Re-enrich all restaurants. Triggered by admin endpoint. */
    @Async
    public void enrichAll() {
        List<Document> all = collection
                .find()
                .projection(new Document("restaurant_id", 1).append("name", 1).append("borough", 1))
                .into(new ArrayList<>());

        logger.info("OSM full re-enrichment: {} restaurants", all.size());
        enrichList(all);
    }

    private void enrichList(List<Document> restaurants) {
        int enriched = 0;
        for (Document doc : restaurants) {
            try {
                String name = doc.getString("name");
                String borough = doc.getString("borough");
                String restaurantId = doc.getString("restaurant_id");
                if (name == null || borough == null || restaurantId == null) continue;

                OsmResult result = queryOverpass(name, borough);
                Document update = new Document("osm_enriched_at", Instant.now());
                if (result != null) {
                    if (result.phone != null) update.append("osm_phone", result.phone);
                    if (result.website != null) update.append("osm_website", result.website);
                    if (result.openingHours != null) update.append("osm_opening_hours", result.openingHours);
                    enriched++;
                }
                collection.updateOne(
                        eq("restaurant_id", restaurantId),
                        new Document("$set", update)
                );
                Thread.sleep(1000); // Overpass rate limit: 1 req/s
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("OSM enrichment interrupted");
                return;
            } catch (Exception e) {
                logger.debug("OSM enrichment failed for {}: {}", doc.getString("name"), e.getMessage());
            }
        }
        logger.info("OSM enrichment complete: {}/{} matched", enriched, restaurants.size());
    }

    private OsmResult queryOverpass(String name, String borough) throws Exception {
        double[] bbox = boroughBbox(borough);
        if (bbox == null) return null;

        String query = String.format(
                "[out:json][timeout:10];node[\"amenity\"=\"restaurant\"][\"name\"][bbox:%f,%f,%f,%f];out tags;",
                bbox[0], bbox[1], bbox[2], bbox[3]);

        String url = OVERPASS_URL + "?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        String response = restTemplate.getForObject(url, String.class);
        if (response == null) return null;

        JsonNode root = objectMapper.readTree(response);
        JsonNode elements = root.path("elements");
        String normalizedTarget = normalizeName(name);

        OsmResult best = null;
        double bestScore = SIMILARITY_THRESHOLD;

        for (JsonNode el : elements) {
            JsonNode tags = el.path("tags");
            String osmName = tags.path("name").asText(null);
            if (osmName == null) continue;
            double score = similarity(normalizedTarget, normalizeName(osmName));
            if (score > bestScore) {
                bestScore = score;
                best = new OsmResult(
                        tags.path("phone").asText(null),
                        tags.path("website").asText(null),
                        tags.path("opening_hours").asText(null)
                );
            }
        }
        return best;
    }

    // Package-private for testing
    static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
    }

    static double similarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshtein(a, b) / maxLen;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] curr = new int[b.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = curr;
        }
        return prev[b.length()];
    }

    private double[] boroughBbox(String borough) {
        if (borough == null) return null;
        switch (borough) {
            case "Manhattan":     return new double[]{40.700, -74.020, 40.882, -73.907};
            case "Brooklyn":      return new double[]{40.570, -74.042, 40.739, -73.833};
            case "Queens":        return new double[]{40.541, -73.962, 40.800, -73.700};
            case "Bronx":         return new double[]{40.785, -73.933, 40.918, -73.748};
            case "Staten Island": return new double[]{40.477, -74.259, 40.651, -74.034};
            default:              return null;
        }
    }

    private static class OsmResult {
        final String phone;
        final String website;
        final String openingHours;
        OsmResult(String phone, String website, String openingHours) {
            this.phone = phone;
            this.website = website;
            this.openingHours = openingHours;
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
mvn test -Dtest=OsmEnrichmentServiceTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/sync/OsmEnrichmentService.java \
        src/test/java/com/st4r4x/sync/OsmEnrichmentServiceTest.java
git commit -m "feat(osm): add OsmEnrichmentService with Overpass API integration"
```

---

### Task 3: Wire OsmEnrichmentService into SyncService

**Files:**
- Modify: `src/main/java/com/st4r4x/sync/SyncService.java`

- [ ] **Step 1: Write a failing test**

Create `src/test/java/com/st4r4x/sync/SyncServiceOsmTest.java`:

```java
package com.st4r4x.sync;

import com.st4r4x.cache.RestaurantCacheService;
import com.st4r4x.dao.RestaurantWriteDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceOsmTest {

    @Mock NycOpenDataClient apiClient;
    @Mock RestaurantWriteDAO restaurantWriteDAO;
    @Mock RestaurantCacheService cacheService;
    @Mock OsmEnrichmentService osmEnrichmentService;
    @InjectMocks SyncService syncService;

    @Test
    void runSync_callsEnrichNew_afterSuccess() {
        doNothing().when(apiClient).streamPages(any());
        syncService.runSync();
        verify(osmEnrichmentService, times(1)).enrichNew();
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -Dtest=SyncServiceOsmTest -q 2>&1 | tail -10
```
Expected: FAIL — `enrichNew()` is never called.

- [ ] **Step 3: Add OsmEnrichmentService to SyncService**

In `src/main/java/com/st4r4x/sync/SyncService.java`:

Add import:
```java
import com.st4r4x.sync.OsmEnrichmentService;
```

Add field after `cacheService`:
```java
private final OsmEnrichmentService osmEnrichmentService;
```

Update the constructor signature and body:
```java
@Autowired
public SyncService(NycOpenDataClient apiClient, RestaurantWriteDAO restaurantWriteDAO,
                   RestaurantCacheService cacheService, OsmEnrichmentService osmEnrichmentService) {
    this.apiClient = apiClient;
    this.restaurantWriteDAO = restaurantWriteDAO;
    this.cacheService = cacheService;
    this.osmEnrichmentService = osmEnrichmentService;
}
```

At the end of `runSync()`, just before `return result;`, add:
```java
if (result.isSuccess()) {
    osmEnrichmentService.enrichNew();
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -Dtest=SyncServiceOsmTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/sync/SyncService.java \
        src/test/java/com/st4r4x/sync/SyncServiceOsmTest.java
git commit -m "feat(osm): wire OsmEnrichmentService into SyncService post-sync"
```

---

### Task 4: Add admin endpoint for full re-enrichment

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/AdminController.java`
- Create: `src/test/java/com/st4r4x/controller/AdminOsmEnrichTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/st4r4x/controller/AdminOsmEnrichTest.java`:

```java
package com.st4r4x.controller;

import com.st4r4x.sync.OsmEnrichmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminOsmEnrichTest {

    @Autowired MockMvc mvc;
    @MockBean OsmEnrichmentService osmEnrichmentService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void postOsmEnrich_triggersEnrichAll_returns202() throws Exception {
        mvc.perform(post("/api/admin/osm-enrich"))
                .andExpect(status().isAccepted());
        verify(osmEnrichmentService, times(1)).enrichAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    void postOsmEnrich_forbiddenForUser() throws Exception {
        mvc.perform(post("/api/admin/osm-enrich"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -Dtest=AdminOsmEnrichTest -q 2>&1 | tail -10
```
Expected: FAIL — endpoint does not exist.

- [ ] **Step 3: Add endpoint to AdminController**

In `src/main/java/com/st4r4x/controller/AdminController.java`, add imports:

```java
import com.st4r4x.sync.OsmEnrichmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
```

Add field:
```java
@Autowired
private OsmEnrichmentService osmEnrichmentService;
```

Add endpoint method:
```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/api/admin/osm-enrich")
public ResponseEntity<Map<String, Object>> triggerOsmEnrich() {
    osmEnrichmentService.enrichAll();
    Map<String, Object> body = new HashMap<>();
    body.put("status", "accepted");
    body.put("message", "OSM enrichment started in background");
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -Dtest=AdminOsmEnrichTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/controller/AdminController.java \
        src/test/java/com/st4r4x/controller/AdminOsmEnrichTest.java
git commit -m "feat(osm): add POST /api/admin/osm-enrich endpoint"
```

---

### Task 5: Show OSM data on restaurant detail page

**Files:**
- Modify: `src/main/resources/templates/restaurant.html`
- Possibly modify: `src/main/java/com/st4r4x/service/RestaurantService.java` (if `toView()` doesn't include OSM fields)

- [ ] **Step 1: Verify toView() includes OSM fields**

Open `src/main/java/com/st4r4x/service/RestaurantService.java` and find `toView()`. If it builds a `Map` manually, add:

```java
if (r.getOsmPhone() != null)        view.put("osmPhone", r.getOsmPhone());
if (r.getOsmWebsite() != null)      view.put("osmWebsite", r.getOsmWebsite());
if (r.getOsmOpeningHours() != null) view.put("osmOpeningHours", r.getOsmOpeningHours());
```

- [ ] **Step 2: Add Contact section CSS**

In `restaurant.html`, inside the existing `<style>` block, add:

```css
.contact-section { background: white; border: 1px solid #e8e0d8; padding: 24px; margin-bottom: 16px; }
```

- [ ] **Step 3: Add Contact section HTML placeholder**

In `restaurant.html`, find the last `.content-card` closing `</div>` before the footer. Insert after it:

```html
<div id="osm-contact-section" class="contact-section" style="display:none">
  <div class="section-label">Contact</div>
  <div class="section-title">Restaurant Info</div>
  <div id="osm-phone"></div>
  <div id="osm-website"></div>
  <div id="osm-hours"></div>
</div>
```

- [ ] **Step 4: Populate Contact section in JS using safe DOM methods**

In `restaurant.html`, inside the `.then(resp => { ... })` block (after `renderInspections(r.grades);`), add the following. It uses safe DOM API (`textContent`, `setAttribute`, `createElement`) — no raw HTML injection from third-party data:

```javascript
(function renderOsmContact(r) {
    var section = document.getElementById('osm-contact-section');
    var hasData = false;

    function makeLabel(text) {
        var el = document.createElement('div');
        el.style.cssText = 'font-size:0.75em;font-weight:700;letter-spacing:0.1em;text-transform:uppercase;color:#888;margin-bottom:2px';
        el.textContent = text;
        return el;
    }

    if (r.osmPhone) {
        hasData = true;
        var wrap = document.createElement('div');
        wrap.style.marginBottom = '8px';
        wrap.appendChild(makeLabel('Phone'));
        var a = document.createElement('a');
        a.href = 'tel:' + r.osmPhone.replace(/[^+\d]/g, '');
        a.textContent = r.osmPhone;
        a.style.cssText = 'color:#c0392b;text-decoration:none;font-weight:600';
        wrap.appendChild(a);
        document.getElementById('osm-phone').appendChild(wrap);
    }

    if (r.osmWebsite) {
        hasData = true;
        var wrap2 = document.createElement('div');
        wrap2.style.marginBottom = '8px';
        wrap2.appendChild(makeLabel('Website'));
        var a2 = document.createElement('a');
        a2.href = r.osmWebsite;
        a2.textContent = r.osmWebsite.replace(/^https?:\/\//, '').replace(/\/$/, '');
        a2.target = '_blank';
        a2.rel = 'noopener noreferrer';
        a2.style.cssText = 'color:#c0392b;text-decoration:none;font-weight:600';
        wrap2.appendChild(a2);
        document.getElementById('osm-website').appendChild(wrap2);
    }

    if (r.osmOpeningHours) {
        hasData = true;
        var wrap3 = document.createElement('div');
        wrap3.appendChild(makeLabel('Hours'));
        var span = document.createElement('span');
        span.style.fontSize = '0.9em';
        span.textContent = r.osmOpeningHours;
        wrap3.appendChild(span);
        document.getElementById('osm-hours').appendChild(wrap3);
    }

    if (hasData) section.style.display = 'block';
})(r);
```

- [ ] **Step 5: Build and smoke-test**

```bash
mvn clean package -DskipTests -q && echo "BUILD OK"
```

Start the app, open any restaurant detail page. Confirm the Contact section is hidden when no OSM data is present. To test with data, run in mongosh:

```javascript
db.restaurants.updateOne(
  { restaurant_id: "41156006" },
  { $set: { osm_phone: "+1-212-555-0100", osm_website: "https://example.com",
            osm_opening_hours: "Mo-Fr 11:00-22:00", osm_enriched_at: new Date() } }
)
```

Navigate to that restaurant's detail page and verify the Contact section appears with correct links.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/restaurant.html \
        src/main/java/com/st4r4x/service/RestaurantService.java
git commit -m "feat(osm): show phone, website, hours on restaurant detail page"
```

---

### Task 6: Enable async and verify full build

**Files:**
- Modify: `src/main/java/com/st4r4x/Application.java` (or main config class)

- [ ] **Step 1: Check if @EnableAsync is already present**

```bash
grep -rn "@EnableAsync" src/main/java/
```

If found: skip Step 2.

- [ ] **Step 2: Add @EnableAsync**

Find the main `Application.java`. Add:

```java
import org.springframework.scheduling.annotation.EnableAsync;
```

Add `@EnableAsync` annotation on the class.

- [ ] **Step 3: Run all tests**

```bash
mvn test -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`, no failures.

- [ ] **Step 4: Commit if changed**

```bash
git add src/main/java/com/st4r4x/Application.java
git commit -m "feat(osm): enable async execution for background enrichment"
```
