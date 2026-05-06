package com.st4r4x.sync;

import com.st4r4x.cache.RestaurantCacheService;
import com.st4r4x.dao.RestaurantDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CronSchedulerTest {

    @Mock RestaurantCacheService cacheService;
    @Mock RestaurantDAO restaurantDAO;
    @Mock OsmEnrichmentService osmEnrichmentService;
    @Mock ElasticsearchSyncService esSyncService;

    @InjectMocks CronScheduler cronScheduler;

    @Test
    void warmCache_records_success_status() {
        when(cacheService.getOrLoadByBorough(any())).thenReturn(Collections.emptyList());
        when(cacheService.getOrLoadWorstCuisines(any(), anyInt(), any())).thenReturn(Collections.emptyList());

        cronScheduler.warmCache();

        Map<String, JobStatus> status = cronScheduler.getStatus();
        assertTrue(status.containsKey("cache-warmup"));
        assertTrue(status.get("cache-warmup").isSuccess());
    }

    @Test
    void warmCache_records_failure_status_on_exception() {
        when(cacheService.getOrLoadByBorough(any())).thenThrow(new RuntimeException("redis down"));

        cronScheduler.warmCache();

        JobStatus status = cronScheduler.getStatus().get("cache-warmup");
        assertFalse(status.isSuccess());
        assertEquals("redis down", status.getErrorMessage());
    }

    @Test
    void reEnrichOsm_calls_enrichAll() {
        cronScheduler.reEnrichOsm();

        verify(osmEnrichmentService).enrichAll();
        assertTrue(cronScheduler.getStatus().get("osm-reenrichment").isSuccess());
    }

    @Test
    void reindexEs_records_success_on_clean_run() throws Exception {
        doNothing().when(esSyncService).reindex();

        cronScheduler.reindexEs();

        assertTrue(cronScheduler.getStatus().get("es-reindex").isSuccess());
    }

    @Test
    void reindexEs_records_failure_on_exception() throws Exception {
        doThrow(new RuntimeException("es down")).when(esSyncService).reindex();

        cronScheduler.reindexEs();

        JobStatus status = cronScheduler.getStatus().get("es-reindex");
        assertFalse(status.isSuccess());
        assertEquals("es down", status.getErrorMessage());
    }

    @Test
    void getStatus_returns_unmodifiable_snapshot() {
        Map<String, JobStatus> status = cronScheduler.getStatus();
        assertNotNull(status);
        assertTrue(status.isEmpty());
    }
}
