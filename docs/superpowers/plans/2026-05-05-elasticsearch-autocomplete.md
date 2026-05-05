# Elasticsearch + Autocomplete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Elasticsearch 8 as a read-only search replica of MongoDB, with a `/api/restaurants/autocomplete` endpoint powering a debounced dropdown in the search bar on `landing.html`.

**Architecture:** ES 8 runs as a 5th Docker service. `ElasticsearchSyncService` bulk-indexes all MongoDB restaurants into ES after each sync and on startup if the index is empty. `GET /api/restaurants/autocomplete?q=` runs a `multi_match` fuzzy query. The frontend dropdown shows name/cuisine/address suggestions; clicking a name navigates to the restaurant page, clicking cuisine/address triggers the existing full search.

**Tech Stack:** Java 25, Spring Boot 4, Elasticsearch Java API Client 8.x (`co.elastic.clients:elasticsearch-java`), Jackson, Docker Compose, Vanilla JS (no jQuery)

---

### Task 1: Add Elasticsearch dependency and Docker service

**Files:**
- Modify: `pom.xml`
- Modify: `docker-compose.yml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Add ES Java API Client to pom.xml**

In `pom.xml`, inside `<dependencies>`, add:

```xml
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.13.4</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

(Jackson is likely already present transitively — the second entry ensures it's explicit. If Maven complains about a duplicate, remove it.)

- [ ] **Step 2: Add Elasticsearch service to docker-compose.yml**

In `docker-compose.yml`, add a new service after `redis:`:

```yaml
  elasticsearch:
    image: elasticsearch:8.13.4
    container_name: elasticsearch
    restart: unless-stopped
    networks:
      - restaurant-network
    ports:
      - "9200:9200"
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms256m -Xmx256m
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:9200/_cluster/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 60s
    deploy:
      resources:
        limits:
          memory: 512m
```

Also add `elasticsearch` to the `app` service's `depends_on` block:
```yaml
      elasticsearch:
        condition: service_healthy
```

And add `ELASTICSEARCH_URI: http://elasticsearch:9200` to the `app` service's `environment` block.

- [ ] **Step 3: Add ES config to application.properties**

In `src/main/resources/application.properties`, add:

```properties
elasticsearch.uri=http://localhost:9200
```

- [ ] **Step 4: Verify Maven resolves the dependency**

```bash
mvn dependency:resolve -q 2>&1 | grep -i elastic
```
Expected: `co.elastic.clients:elasticsearch-java:jar:8.13.4`

- [ ] **Step 5: Commit**

```bash
git add pom.xml docker-compose.yml src/main/resources/application.properties
git commit -m "feat(es): add Elasticsearch 8 dependency and Docker service"
```

---

### Task 2: Create ElasticsearchConfig

**Files:**
- Create: `src/main/java/com/st4r4x/config/ElasticsearchConfig.java`

- [ ] **Step 1: Write a failing test**

Create `src/test/java/com/st4r4x/config/ElasticsearchConfigTest.java`:

```java
package com.st4r4x.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchConfigTest {

    @Test
    void buildClient_withLocalUri_returnsNonNullClient() {
        ElasticsearchClient client = ElasticsearchConfig.buildClient("http://localhost:9200");
        assertThat(client).isNotNull();
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -Dtest=ElasticsearchConfigTest -q 2>&1 | tail -10
```
Expected: compilation error (class not found).

- [ ] **Step 3: Create ElasticsearchConfig.java**

Create `src/main/java/com/st4r4x/config/ElasticsearchConfig.java`:

```java
package com.st4r4x.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.uri:http://localhost:9200}")
    private String elasticsearchUri;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        return buildClient(elasticsearchUri);
    }

    public static ElasticsearchClient buildClient(String uri) {
        URI parsed = URI.create(uri);
        RestClient restClient = RestClient.builder(
                new HttpHost(parsed.getHost(), parsed.getPort(), parsed.getScheme())
        ).build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -Dtest=ElasticsearchConfigTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/config/ElasticsearchConfig.java \
        src/test/java/com/st4r4x/config/ElasticsearchConfigTest.java
git commit -m "feat(es): add ElasticsearchConfig bean"
```

---

### Task 3: Create ElasticsearchSyncService

**Files:**
- Create: `src/main/java/com/st4r4x/sync/ElasticsearchSyncService.java`
- Create: `src/test/java/com/st4r4x/sync/ElasticsearchSyncServiceTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/st4r4x/sync/ElasticsearchSyncServiceTest.java`:

```java
package com.st4r4x.sync;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.st4r4x.domain.Restaurant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchSyncServiceTest {

    @Mock ElasticsearchClient esClient;
    @InjectMocks ElasticsearchSyncService syncService;

    @Test
    void toEsDoc_mapsRestaurantFields() {
        Restaurant r = new Restaurant("Joe Pizza", "Italian", "Manhattan");
        r.setRestaurantId("12345");

        ElasticsearchSyncService.EsRestaurantDoc doc = ElasticsearchSyncService.toEsDoc(r);

        assertThat(doc.getCamis()).isEqualTo("12345");
        assertThat(doc.getDba()).isEqualTo("Joe Pizza");
        assertThat(doc.getCuisineDescription()).isEqualTo("Italian");
        assertThat(doc.getBoro()).isEqualTo("Manhattan");
    }
}
```

Add the missing import at the top:
```java
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -Dtest=ElasticsearchSyncServiceTest -q 2>&1 | tail -10
```
Expected: compilation error.

- [ ] **Step 3: Create ElasticsearchSyncService.java**

Create `src/main/java/com/st4r4x/sync/ElasticsearchSyncService.java`:

```java
package com.st4r4x.sync;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.st4r4x.config.AppConfig;
import com.st4r4x.config.MongoClientFactory;
import com.st4r4x.domain.Address;
import com.st4r4x.domain.Restaurant;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;

@Service
public class ElasticsearchSyncService {

    static final String INDEX = "restaurants";
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSyncService.class);
    private static final int BULK_SIZE = 500;

    private final ElasticsearchClient esClient;
    private final MongoCollection<Restaurant> mongoCollection;

    @Autowired
    public ElasticsearchSyncService(ElasticsearchClient esClient,
                                     MongoClientFactory mongoClientFactory,
                                     AppConfig appConfig) {
        this.esClient = esClient;
        CodecRegistry registry = fromRegistries(getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        MongoDatabase db = mongoClientFactory.getClient()
                .getDatabase(appConfig.getMongoDatabase())
                .withCodecRegistry(registry);
        this.mongoCollection = db.getCollection(appConfig.getMongoCollection(), Restaurant.class);
    }

    @PostConstruct
    public void initIndexIfEmpty() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX)).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(INDEX)
                        .mappings(m -> m
                                .properties("camis",            p -> p.keyword(k -> k))
                                .properties("dba",              p -> p.text(t -> t.analyzer("standard").boost(3.0)))
                                .properties("cuisineDescription", p -> p.text(t -> t.analyzer("standard").boost(2.0)))
                                .properties("boro",             p -> p.text(t -> t.analyzer("standard")))
                                .properties("street",           p -> p.text(t -> t.analyzer("standard")))
                                .properties("zipcode",          p -> p.keyword(k -> k))
                        )
                );
                logger.info("Created ES index '{}'", INDEX);
            }
            long count = esClient.count(c -> c.index(INDEX)).count();
            if (count == 0) {
                logger.info("ES index '{}' is empty — triggering initial reindex", INDEX);
                reindex();
            }
        } catch (Exception e) {
            logger.warn("ES init check failed (ES may be unavailable): {}", e.getMessage());
        }
    }

    public void reindex() {
        try {
            List<Restaurant> batch = new ArrayList<>(BULK_SIZE);
            long total = 0;
            for (Restaurant r : mongoCollection.find()) {
                batch.add(r);
                if (batch.size() == BULK_SIZE) {
                    total += bulkIndex(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) total += bulkIndex(batch);
            logger.info("ES reindex complete: {} documents", total);
        } catch (Exception e) {
            logger.error("ES reindex failed: {}", e.getMessage(), e);
        }
    }

    private int bulkIndex(List<Restaurant> restaurants) throws Exception {
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (Restaurant r : restaurants) {
            if (r.getRestaurantId() == null) continue;
            EsRestaurantDoc doc = toEsDoc(r);
            br.operations(op -> op.index(idx -> idx
                    .index(INDEX)
                    .id(r.getRestaurantId())
                    .document(doc)
            ));
        }
        BulkResponse response = esClient.bulk(br.build());
        if (response.errors()) {
            logger.warn("ES bulk had errors in batch of {}", restaurants.size());
        }
        return restaurants.size();
    }

    static EsRestaurantDoc toEsDoc(Restaurant r) {
        EsRestaurantDoc doc = new EsRestaurantDoc();
        doc.setCamis(r.getRestaurantId());
        doc.setDba(r.getName());
        doc.setCuisineDescription(r.getCuisine());
        doc.setBoro(r.getBorough());
        Address addr = r.getAddress();
        if (addr != null) {
            doc.setStreet(addr.getStreet());
            doc.setZipcode(addr.getZipcode());
        }
        return doc;
    }

    public static class EsRestaurantDoc {
        private String camis;
        private String dba;
        private String cuisineDescription;
        private String boro;
        private String street;
        private String zipcode;

        public String getCamis() { return camis; }
        public void setCamis(String camis) { this.camis = camis; }
        public String getDba() { return dba; }
        public void setDba(String dba) { this.dba = dba; }
        public String getCuisineDescription() { return cuisineDescription; }
        public void setCuisineDescription(String cuisineDescription) { this.cuisineDescription = cuisineDescription; }
        public String getBoro() { return boro; }
        public void setBoro(String boro) { this.boro = boro; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public String getZipcode() { return zipcode; }
        public void setZipcode(String zipcode) { this.zipcode = zipcode; }
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -Dtest=ElasticsearchSyncServiceTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/sync/ElasticsearchSyncService.java \
        src/test/java/com/st4r4x/sync/ElasticsearchSyncServiceTest.java
git commit -m "feat(es): add ElasticsearchSyncService with bulk reindex"
```

---

### Task 4: Wire ElasticsearchSyncService into SyncService

**Files:**
- Modify: `src/main/java/com/st4r4x/sync/SyncService.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/st4r4x/sync/SyncServiceEsTest.java`:

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
class SyncServiceEsTest {

    @Mock NycOpenDataClient apiClient;
    @Mock RestaurantWriteDAO restaurantWriteDAO;
    @Mock RestaurantCacheService cacheService;
    @Mock OsmEnrichmentService osmEnrichmentService;
    @Mock ElasticsearchSyncService esSyncService;
    @InjectMocks SyncService syncService;

    @Test
    void runSync_callsEsReindex_afterSuccess() {
        doNothing().when(apiClient).streamPages(any());
        syncService.runSync();
        verify(esSyncService, times(1)).reindex();
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -Dtest=SyncServiceEsTest -q 2>&1 | tail -10
```
Expected: FAIL.

- [ ] **Step 3: Add ElasticsearchSyncService to SyncService**

In `src/main/java/com/st4r4x/sync/SyncService.java`:

Add field:
```java
private final ElasticsearchSyncService esSyncService;
```

Update constructor:
```java
@Autowired
public SyncService(NycOpenDataClient apiClient, RestaurantWriteDAO restaurantWriteDAO,
                   RestaurantCacheService cacheService, OsmEnrichmentService osmEnrichmentService,
                   ElasticsearchSyncService esSyncService) {
    this.apiClient = apiClient;
    this.restaurantWriteDAO = restaurantWriteDAO;
    this.cacheService = cacheService;
    this.osmEnrichmentService = osmEnrichmentService;
    this.esSyncService = esSyncService;
}
```

In the `if (result.isSuccess())` block at the end of `runSync()`, add:
```java
if (result.isSuccess()) {
    osmEnrichmentService.enrichNew();
    esSyncService.reindex();
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -Dtest=SyncServiceEsTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/sync/SyncService.java \
        src/test/java/com/st4r4x/sync/SyncServiceEsTest.java
git commit -m "feat(es): wire ElasticsearchSyncService into SyncService post-sync"
```

---

### Task 5: Add autocomplete endpoint

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/RestaurantController.java`
- Create: `src/test/java/com/st4r4x/controller/RestaurantControllerAutocompleteTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/st4r4x/controller/RestaurantControllerAutocompleteTest.java`:

```java
package com.st4r4x.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.st4r4x.sync.ElasticsearchSyncService.EsRestaurantDoc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RestaurantController.class)
class RestaurantControllerAutocompleteTest {

    @Autowired MockMvc mvc;
    @MockBean ElasticsearchClient esClient;

    @Test
    void autocomplete_returnsResultsFromES() throws Exception {
        // Build a minimal mock SearchResponse
        EsRestaurantDoc doc = new EsRestaurantDoc();
        doc.setCamis("12345");
        doc.setDba("Joe Pizza");
        doc.setCuisineDescription("Italian");
        doc.setBoro("Manhattan");
        doc.setStreet("Broadway");

        Hit<EsRestaurantDoc> hit = Hit.of(h -> h.index("restaurants").id("12345").source(doc));
        HitsMetadata<EsRestaurantDoc> meta = HitsMetadata.of(m -> m.hits(List.of(hit)).total(t -> t.value(1).relation(r -> r)));
        SearchResponse<EsRestaurantDoc> resp = SearchResponse.of(r -> r
                .took(1).timedOut(false).shards(s -> s.total(1).successful(1).failed(0))
                .hits(meta));

        when(esClient.search(any(), any(Class.class))).thenReturn(resp);

        mvc.perform(get("/api/restaurants/autocomplete?q=joe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].camis").value("12345"))
                .andExpect(jsonPath("$.data[0].dba").value("Joe Pizza"));
    }

    @Test
    void autocomplete_missingQ_returns400() throws Exception {
        mvc.perform(get("/api/restaurants/autocomplete"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
mvn test -Dtest=RestaurantControllerAutocompleteTest -q 2>&1 | tail -10
```
Expected: FAIL — endpoint not found.

- [ ] **Step 3: Add autocomplete endpoint to RestaurantController**

In `src/main/java/com/st4r4x/controller/RestaurantController.java`, add imports:

```java
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.st4r4x.sync.ElasticsearchSyncService;
import com.st4r4x.sync.ElasticsearchSyncService.EsRestaurantDoc;
```

Add field (inject alongside existing DAOs):
```java
@Autowired
private ElasticsearchClient esClient;
```

Add endpoint method:
```java
@Operation(summary = "Autocomplete suggestions from Elasticsearch")
@GetMapping("/autocomplete")
public ResponseEntity<Map<String, Object>> autocomplete(
        @RequestParam String q,
        @RequestParam(defaultValue = "8") int limit) {
    try {
        SearchResponse<EsRestaurantDoc> response = esClient.search(s -> s
                .index(ElasticsearchSyncService.INDEX)
                .size(limit)
                .query(query -> query
                        .multiMatch(mm -> mm
                                .query(q)
                                .fields("dba^3", "cuisineDescription^2", "street", "boro")
                                .fuzziness("AUTO")
                        )
                ),
                EsRestaurantDoc.class
        );

        List<Map<String, Object>> results = response.hits().hits().stream()
                .filter(hit -> hit.source() != null)
                .map(hit -> {
                    EsRestaurantDoc doc = hit.source();
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("camis", doc.getCamis());
                    item.put("dba", doc.getDba());
                    item.put("cuisineDescription", doc.getCuisineDescription());
                    item.put("boro", doc.getBoro());
                    item.put("street", doc.getStreet());
                    return item;
                })
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", "success");
        body.put("data", results);
        return ResponseEntity.ok(body);
    } catch (Exception e) {
        return errorResponse(e);
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
mvn test -Dtest=RestaurantControllerAutocompleteTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/st4r4x/controller/RestaurantController.java \
        src/test/java/com/st4r4x/controller/RestaurantControllerAutocompleteTest.java
git commit -m "feat(es): add GET /api/restaurants/autocomplete endpoint"
```

---

### Task 6: Add autocomplete dropdown to landing.html

**Files:**
- Modify: `src/main/resources/templates/landing.html`

- [ ] **Step 1: Add dropdown CSS**

In `landing.html`, inside the existing `<style>` block, add:

```css
.autocomplete-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: white;
  border: 1px solid #e0e0e0;
  border-top: none;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  z-index: 1000;
  max-height: 360px;
  overflow-y: auto;
}
.autocomplete-item {
  padding: 10px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
  font-size: 0.9em;
}
.autocomplete-item:hover, .autocomplete-item.active {
  background: #faf7f4;
}
.autocomplete-item .ac-name { font-weight: 600; color: #1a1a1a; }
.autocomplete-item .ac-meta { font-size: 0.8em; color: #888; margin-top: 2px; }
.search-bar-wrap { position: relative; max-width: 520px; }
```

- [ ] **Step 2: Wrap the search bar in a relative-positioned container**

In `landing.html`, find:
```html
      <div class="search-bar">
        <input id="search-input" type="text" placeholder="Search by restaurant name or address…">
        <button id="search-btn">Search</button>
      </div>
```

Replace with:
```html
      <div class="search-bar-wrap">
        <div class="search-bar">
          <input id="search-input" type="text" placeholder="Search by restaurant name or address…" autocomplete="off">
          <button id="search-btn">Search</button>
        </div>
        <div id="ac-dropdown" class="autocomplete-dropdown" style="display:none"></div>
      </div>
```

- [ ] **Step 3: Add autocomplete JS**

In `landing.html`, just before the closing `</script>` tag (after the existing `search-input` keydown listener), add:

```javascript
(function() {
  var input = document.getElementById('search-input');
  var dropdown = document.getElementById('ac-dropdown');
  var debounceTimer = null;
  var activeIndex = -1;
  var suggestions = [];

  function closeDropdown() {
    dropdown.style.display = 'none';
    dropdown.textContent = '';
    suggestions = [];
    activeIndex = -1;
  }

  function openDropdown(items) {
    suggestions = items;
    activeIndex = -1;
    dropdown.textContent = '';
    if (!items.length) { closeDropdown(); return; }

    items.forEach(function(item, i) {
      var div = document.createElement('div');
      div.className = 'autocomplete-item';
      div.dataset.index = i;

      var nameEl = document.createElement('div');
      nameEl.className = 'ac-name';
      nameEl.textContent = item.dba || '';
      div.appendChild(nameEl);

      var parts = [item.cuisineDescription, item.boro, item.street].filter(Boolean);
      if (parts.length) {
        var metaEl = document.createElement('div');
        metaEl.className = 'ac-meta';
        metaEl.textContent = parts.join(' · ');
        div.appendChild(metaEl);
      }

      div.addEventListener('mousedown', function(e) {
        e.preventDefault();
        selectItem(item);
      });

      dropdown.appendChild(div);
    });
    dropdown.style.display = 'block';
  }

  function setActive(index) {
    var items = dropdown.querySelectorAll('.autocomplete-item');
    items.forEach(function(el) { el.classList.remove('active'); });
    if (index >= 0 && index < items.length) {
      items[index].classList.add('active');
      activeIndex = index;
    }
  }

  function selectItem(item) {
    // Name match: navigate to restaurant page. Cuisine/address match: trigger search.
    var q = document.getElementById('search-input').value.trim().toLowerCase();
    var name = (item.dba || '').toLowerCase();
    closeDropdown();
    if (name.includes(q) || q.includes(name.substring(0, 4))) {
      window.location.href = '/restaurant/' + item.camis;
    } else {
      document.getElementById('search-input').value = item.dba;
      doSearch();
    }
  }

  input.addEventListener('input', function() {
    var q = input.value.trim();
    clearTimeout(debounceTimer);
    if (q.length < 2) { closeDropdown(); return; }
    debounceTimer = setTimeout(function() {
      fetch('/api/restaurants/autocomplete?q=' + encodeURIComponent(q) + '&limit=8')
        .then(function(r) { return r.json(); })
        .then(function(res) { openDropdown(res.data || []); })
        .catch(function() { closeDropdown(); });
    }, 250);
  });

  input.addEventListener('keydown', function(e) {
    if (!suggestions.length) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive(Math.min(activeIndex + 1, suggestions.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(Math.max(activeIndex - 1, 0)); }
    else if (e.key === 'Enter' && activeIndex >= 0) { e.stopImmediatePropagation(); selectItem(suggestions[activeIndex]); }
    else if (e.key === 'Escape') { closeDropdown(); }
  });

  document.addEventListener('click', function(e) {
    if (!e.target.closest('.search-bar-wrap')) closeDropdown();
  });
})();
```

- [ ] **Step 4: Build and smoke-test**

```bash
mvn clean package -DskipTests -q && echo "BUILD OK"
```

Start ES locally (`docker compose up -d elasticsearch`) and the app (`mvn spring-boot:run`). Open `http://localhost:8080`, type "pizza" in the search bar, verify the dropdown appears. Press ↓ to navigate, Enter to select, Escape to close.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/landing.html
git commit -m "feat(es): add autocomplete dropdown to landing page search bar"
```

---

### Task 7: Full integration verification

- [ ] **Step 1: Run all tests**

```bash
mvn test -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`, no failures.

- [ ] **Step 2: Start full Docker stack**

```bash
docker compose up -d
docker compose logs -f app | head -40
```
Expected: app starts, ES index created/populated, no errors.

- [ ] **Step 3: Verify ES index has documents**

```bash
curl -s http://localhost:9200/restaurants/_count | python3 -m json.tool
```
Expected: `"count"` > 0.

- [ ] **Step 4: Test autocomplete endpoint**

```bash
curl -s "http://localhost:8080/api/restaurants/autocomplete?q=pizza&limit=5" | python3 -m json.tool
```
Expected: JSON with `"status": "success"` and `"data"` array containing restaurant suggestions.

- [ ] **Step 5: Final commit**

```bash
git add .
git commit -m "feat(es): verify full Elasticsearch integration end-to-end"
```
