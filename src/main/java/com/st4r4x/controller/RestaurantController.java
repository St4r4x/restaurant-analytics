package com.st4r4x.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.st4r4x.cache.RestaurantCacheService;
import com.st4r4x.dao.AnalyticsDAO;
import com.st4r4x.dao.RestaurantDAO;
import com.st4r4x.aggregation.AggregationCount;
import com.st4r4x.dto.HeatmapPoint;
import com.st4r4x.dto.TopRestaurantEntry;
import com.st4r4x.domain.Restaurant;
import com.st4r4x.entity.AuditAction;
import com.st4r4x.service.AuditService;
import com.st4r4x.service.RestaurantService;
import com.st4r4x.sync.ElasticsearchSyncService;
import com.st4r4x.sync.ElasticsearchSyncService.EsRestaurantDoc;
import com.st4r4x.sync.SyncResult;
import com.st4r4x.sync.SyncService;
import com.st4r4x.util.ResponseUtil;
import org.bson.Document;

/**
 * REST API controller for MongoDB restaurant data
 */
@Tag(name = "Restaurants", description = "NYC restaurant inspection data — analytics, sync and leaderboard")
@RestController
@RequestMapping("/api/restaurants")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RestaurantController {

    @Autowired
    private RestaurantDAO restaurantDAO;

    @Autowired
    private AnalyticsDAO analyticsDAO;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SyncService syncService;

    @Autowired
    private RestaurantCacheService cacheService;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private AuditService auditService;

    /**
     * USE CASE 1: Restaurant count per borough
     */
    @Operation(summary = "Restaurant count per borough", description = "Returns the number of restaurants in each NYC borough, sorted by count. Result is cached in Redis for 1 hour.")
    @GetMapping("/by-borough")
    public ResponseEntity<Map<String, Object>> getByBorough() {
        try {
            List<AggregationCount> data = cacheService.getOrLoadByBorough(
                    restaurantService::getRestaurantCountByBorough);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Global statistics
     */
    @Operation(summary = "Global statistics", description = "Total restaurant count and breakdown by borough.")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("totalRestaurants", restaurantService.countAll());
            response.put("boroughStats", restaurantService.getStatisticsByBorough());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Health check — probes MongoDB and Redis connectivity.
     * Returns 200 when all dependencies are reachable, 503 if any are down.
     */
    @Operation(summary = "Health check")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("version", com.st4r4x.config.AppConfig.getAppVersion());

        String mongoStatus = "OK";
        try {
            com.st4r4x.config.MongoClientFactory.getInstance()
                .getDatabase(com.st4r4x.config.AppConfig.getMongoDatabase())
                .runCommand(new org.bson.Document("ping", 1));
        } catch (Exception e) {
            mongoStatus = "UNAVAILABLE";
        }

        String redisStatus = "OK";
        try {
            cacheService.ping();
        } catch (Exception e) {
            redisStatus = "UNAVAILABLE";
        }

        response.put("mongo", mongoStatus);
        response.put("redis", redisStatus);

        boolean healthy = "OK".equals(mongoStatus) && "OK".equals(redisStatus);
        response.put("status", healthy ? "OK" : "DEGRADED");
        return ResponseEntity.status(healthy ? 200 : 503).body(response);
    }

    /**
     * HYGIENE RADAR: Returns the worst restaurants with their GPS coordinates
     */
    @Operation(summary = "Hygiene Radar", description = "Returns restaurants from the worst-scoring cuisines, optionally filtered by borough, cuisine and max score. Useful for finding the most violation-prone establishments.")
    @GetMapping("/hygiene-radar")
    public ResponseEntity<Map<String, Object>> getHygieneRadar(
            @RequestParam(required = false) String borough,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Double maxScore,
            @RequestParam(defaultValue = "25") int restaurantLimit,
            @RequestParam(required = false) String cuisine) {
        try {
            List<Restaurant> restaurants = restaurantService.getHygieneRadarRestaurants(borough, limit, maxScore, restaurantLimit, cuisine);
            List<Map<String, Object>> views = restaurants.stream().map(RestaurantService::toView).collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("borough", borough);
            response.put("data", views);
            response.put("count", views.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * All distinct cuisine types (for populating filter dropdowns)
     */
    @Operation(summary = "All distinct cuisine types", description = "Returns a sorted list of all cuisine types present in the dataset. Useful for populating filter dropdowns.")
    @GetMapping("/cuisines")
    public ResponseEntity<Map<String, Object>> getCuisines() {
        try {
            List<String> data = restaurantService.getDistinctCuisines();
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Top N cuisines by restaurant count
     */
    @Operation(summary = "Top N cuisines by restaurant count")
    @GetMapping("/by-cuisine")
    public ResponseEntity<Map<String, Object>> getByCuisine(
            @Parameter(description = "Number of cuisines to return") @RequestParam(defaultValue = "10") int limit) {
        try {
            List<AggregationCount> data = restaurantService.getTopCuisinesByCount(limit);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Triggers a manual data sync from the NYC Open Data API.
     */
    @Operation(summary = "Trigger manual data sync", description = "Fetches fresh data from the NYC Open Data API, upserts into MongoDB, and invalidates the Redis cache. Returns the sync result with record counts.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        try {
            SyncResult result = syncService.runSync();
            auditService.log(AuditAction.SYNC_TRIGGERED, null, null,
                    result.isSuccess() ? null : Map.of("error", result.getErrorMessage()));
            Map<String, Object> response = new HashMap<>();
            response.put("status", result.isSuccess() ? "success" : "error");
            response.put("rawRecords", result.getRawRecords());
            response.put("upsertedRestaurants", result.getUpsertedRestaurants());
            response.put("startedAt", result.getStartedAt());
            response.put("completedAt", result.getCompletedAt());
            if (!result.isSuccess()) response.put("error", result.getErrorMessage());
            int httpStatus = result.isSuccess() ? 200 : 502;
            return ResponseEntity.status(httpStatus).body(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Returns the status of the last data sync.
     */
    @Operation(summary = "Last sync status", description = "Returns the result of the most recent data sync, or a never_run status if no sync has been executed.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> syncStatus() {
        Map<String, Object> response = new HashMap<>();
        if (syncService.isRunning()) {
            response.put("status", "running");
            response.put("startedAt", syncService.getRunningStartedAt());
            response.put("message", "Sync in progress");
            return ResponseEntity.ok(response);
        }
        SyncResult last = syncService.getLastResult();
        if (last == null) {
            response.put("status", "never_run");
            response.put("message", "No sync has been executed yet");
        } else {
            response.put("status", last.isSuccess() ? "success" : "error");
            response.put("rawRecords", last.getRawRecords());
            response.put("upsertedRestaurants", last.getUpsertedRestaurants());
            response.put("startedAt", last.getStartedAt());
            response.put("completedAt", last.getCompletedAt());
            if (!last.isSuccess()) response.put("error", last.getErrorMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Top N healthiest restaurants from the Redis sorted set (lowest inspection score).
     */
    @Operation(summary = "Top healthiest restaurants", description = "Returns the restaurants with the lowest inspection scores (best health) from the Redis sorted set. Updated on every sync.")
    @GetMapping("/top")
    public ResponseEntity<Map<String, Object>> getTop(
            @Parameter(description = "Number of restaurants to return") @RequestParam(defaultValue = "10") int limit) {
        try {
            List<TopRestaurantEntry> data = cacheService.getTopRestaurants(limit);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Random restaurant", description = "Returns a single restaurant picked at random from the dataset.")
    @GetMapping("/random")
    public ResponseEntity<Map<String, Object>> getRandom() {
        try {
            Restaurant data = restaurantService.getRandomRestaurant();
            Map<String, Object> response = new HashMap<>();
            if (data == null) {
                response.put("status", "error");
                response.put("message", "No restaurants in database");
                return ResponseEntity.status(404).body(response);
            }
            response.put("status", "success");
            response.put("data", RestaurantService.toView(data));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Random sample of restaurants", description = "Returns N randomly-selected restaurants via $sample aggregation. Default limit is 3.")
    @GetMapping("/sample")
    public ResponseEntity<Map<String, Object>> getSample(
            @RequestParam(defaultValue = "3") int limit) {
        try {
            List<Restaurant> data = restaurantDAO.findSampleRestaurants(limit);
            List<Map<String, Object>> views = data.stream()
                .map(RestaurantService::toView)
                .collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", views);
            response.put("count", views.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Restaurant detail", description = "Returns the full document for a single restaurant, including all grades and computed badge fields.")
    @GetMapping("/{restaurantId}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String restaurantId) {
        try {
            Restaurant data = restaurantService.getByRestaurantId(restaurantId);
            Map<String, Object> response = new HashMap<>();
            if (data == null) {
                response.put("status", "error");
                response.put("message", "Restaurant not found: " + restaurantId);
                return ResponseEntity.status(404).body(response);
            }
            response.put("status", "success");
            response.put("data", RestaurantService.toView(data));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Recently inspected restaurants", description = "Returns restaurants that had at least one inspection in the last N days.")
    @GetMapping("/recent-inspections")
    public ResponseEntity<Map<String, Object>> getRecentInspections(
            @RequestParam(defaultValue = "3650") int days,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> data = restaurantService.getRecentlyInspected(days, limit)
                    .stream().map(RestaurantService::toView).collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("days", days);
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Nearby restaurants", description = "Returns restaurants within the given radius (meters) around the provided coordinates. Requires a 2dsphere index.")
    @GetMapping("/nearby")
    public ResponseEntity<Map<String, Object>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") int radius,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> data = restaurantService.getNearbyRestaurants(lat, lng, radius, limit)
                    .stream().map(RestaurantService::toView).collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Heatmap data", description = "Returns lat/lng/weight points for the violation heatmap overlay. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/heatmap")
    public ResponseEntity<Map<String, Object>> getHeatmap(
            @RequestParam(required = false) String borough,
            @RequestParam(defaultValue = "500") int limit) {
        try {
            List<HeatmapPoint> data = restaurantService.getHeatmapData(borough, limit);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Rebuild Redis cache from MongoDB", description = "Reads all restaurants from MongoDB and repopulates the Redis leaderboard sorted set. Use when Redis is empty after a restart. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/rebuild-cache")
    public ResponseEntity<Map<String, Object>> rebuildCache(
            @RequestParam(defaultValue = "25000") int limit) {
        try {
            List<Restaurant> restaurants = restaurantService.getAllRestaurants(limit);
            cacheService.invalidateAll();
            cacheService.resetTopRestaurants();
            cacheService.addTopRestaurantsBatch(restaurants);
            cacheService.finalizeTopRestaurants();
            auditService.log(AuditAction.CACHE_REBUILT, null, null, null);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("restaurantsProcessed", restaurants.size());
            response.put("message", "Cache rebuilt successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Search restaurants by name or address", description = "Case-insensitive regex search on restaurant name and street address. Returns at most limit results (default 20).")
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchRestaurants(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Restaurant> data = analyticsDAO.searchByNameOrAddress(q, limit);
            List<Map<String, Object>> views = data.stream()
                .map(RestaurantService::toView)
                .collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", views);
            response.put("count", views.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Autocomplete suggestions from Elasticsearch")
    @GetMapping("/autocomplete")
    public ResponseEntity<Map<String, Object>> autocomplete(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limit) {
        try {
            // Split query tokens: last token that matches a known borough is treated
            // as a location filter; remaining tokens drive the name/cuisine search
            String[] tokens = q.trim().split("\\s+");
            java.util.Set<String> BOROUGHS = java.util.Set.of(
                    "manhattan", "brooklyn", "queens", "bronx", "staten", "island");
            StringBuilder nameQuery = new StringBuilder();
            StringBuilder boroQuery = new StringBuilder();
            for (String token : tokens) {
                if (BOROUGHS.contains(token.toLowerCase())) {
                    if (boroQuery.length() > 0) boroQuery.append(" ");
                    boroQuery.append(token);
                } else {
                    if (nameQuery.length() > 0) nameQuery.append(" ");
                    nameQuery.append(token);
                }
            }
            String namePart = nameQuery.length() > 0 ? nameQuery.toString() : q;
            String boroPart = boroQuery.toString();

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(ElasticsearchSyncService.INDEX)
                    .size(limit)
                    .query(query -> query
                            .bool(b -> {
                                // Name/cuisine match (ngram for prefix, fuzzy for typos)
                                b.must(m -> m
                                        .bool(inner -> inner
                                                .should(sh -> sh
                                                        .multiMatch(mm -> mm
                                                                .query(namePart)
                                                                .fields("dba^4", "cuisineDescription^2", "street^1")
                                                                .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                                                        )
                                                )
                                                .should(sh -> sh
                                                        .multiMatch(mm -> mm
                                                                .query(namePart)
                                                                .fields("dba^2", "cuisineDescription", "street")
                                                                .fuzziness("AUTO")
                                                        )
                                                )
                                                .minimumShouldMatch("1")
                                        )
                                );
                                // Borough filter — only applied when user typed a borough token
                                if (!boroPart.isEmpty()) {
                                    b.filter(f -> f
                                            .match(m -> m
                                                    .field("boro")
                                                    .query(boroPart)
                                            )
                                    );
                                }
                                return b;
                            })
                    )
            );
            SearchResponse<EsRestaurantDoc> response = esClient.search(searchRequest, EsRestaurantDoc.class);

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
                    .collect(Collectors.toList());

            Map<String, Object> body = new HashMap<>();
            body.put("status", "success");
            body.put("data", results);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Map points for all restaurants", description = "Returns lightweight projection documents (restaurantId, name, lat, lng, grade) for all restaurants that have coordinates.")
    @GetMapping("/map-points")
    public ResponseEntity<Map<String, Object>> getMapPoints() {
        try {
            List<Document> data = analyticsDAO.findMapPoints();
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    private ResponseEntity<Map<String, Object>> errorResponse(Exception e) {
        return ResponseUtil.errorResponse(e);
    }
}
