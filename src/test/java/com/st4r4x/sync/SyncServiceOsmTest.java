package com.st4r4x.sync;

import com.st4r4x.cache.RestaurantCacheService;
import com.st4r4x.dao.RestaurantWriteDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
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
        // Mock streamPages (returns int, accepts Consumer<List<NycApiRestaurantDto>>)
        when(apiClient.streamPages(any())).thenReturn(0);
        syncService.runSync();
        verify(osmEnrichmentService, times(1)).enrichNew();
    }
}
