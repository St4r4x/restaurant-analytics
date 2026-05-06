package com.st4r4x.sync;

import com.st4r4x.cache.RestaurantCacheService;
import com.st4r4x.dao.RestaurantDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CronScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CronScheduler.class);

    private static final List<String> BOROUGHS =
            Arrays.asList("Manhattan", "Brooklyn", "Queens", "Bronx", "Staten Island");

    private final RestaurantCacheService cacheService;
    private final RestaurantDAO restaurantDAO;
    private final OsmEnrichmentService osmEnrichmentService;
    private final ElasticsearchSyncService esSyncService;

    private final ConcurrentHashMap<String, JobStatus> registry = new ConcurrentHashMap<>();

    @Autowired
    public CronScheduler(RestaurantCacheService cacheService,
                         RestaurantDAO restaurantDAO,
                         OsmEnrichmentService osmEnrichmentService,
                         ElasticsearchSyncService esSyncService) {
        this.cacheService = cacheService;
        this.restaurantDAO = restaurantDAO;
        this.osmEnrichmentService = osmEnrichmentService;
        this.esSyncService = esSyncService;
    }

    /** Pre-populate Redis with common aggregations — runs 30 min after the nightly sync. */
    @Scheduled(cron = "0 30 2 * * *")
    public void warmCache() {
        Instant start = Instant.now();
        try {
            cacheService.getOrLoadByBorough(() -> restaurantDAO.findCountByBorough());
            for (String borough : BOROUGHS) {
                cacheService.getOrLoadWorstCuisines(borough, 10,
                        () -> restaurantDAO.findWorstCuisinesByAverageScoreInBorough(borough, 10));
            }
            recordJob("cache-warmup", start, true, null);
            logger.info("Cache warm-up complete");
        } catch (Exception e) {
            recordJob("cache-warmup", start, false, e.getMessage());
            logger.warn("Cache warm-up failed: {}", e.getMessage());
        }
    }

    /** Full OSM re-enrichment for all restaurants — Sundays at 03:00. */
    @Scheduled(cron = "0 0 3 * * 0")
    public void reEnrichOsm() {
        Instant start = Instant.now();
        try {
            osmEnrichmentService.enrichAll();
            recordJob("osm-reenrichment", start, true, null);
            logger.info("OSM re-enrichment triggered");
        } catch (Exception e) {
            recordJob("osm-reenrichment", start, false, e.getMessage());
            logger.warn("OSM re-enrichment failed: {}", e.getMessage());
        }
    }

    /** ES reindex — daily at 04:00, after the 02:00 data sync. */
    @Scheduled(cron = "0 0 4 * * *")
    public void reindexEs() {
        Instant start = Instant.now();
        try {
            esSyncService.reindex();
            recordJob("es-reindex", start, true, null);
            logger.info("ES reindex complete");
        } catch (Exception e) {
            recordJob("es-reindex", start, false, e.getMessage());
            logger.warn("ES reindex failed: {}", e.getMessage());
        }
    }

    public Map<String, JobStatus> getStatus() {
        return Collections.unmodifiableMap(registry);
    }

    private void recordJob(String key, Instant start, boolean success, String error) {
        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
        registry.put(key, success
                ? JobStatus.success(start, durationMs)
                : JobStatus.failure(start, durationMs, error));
    }
}
