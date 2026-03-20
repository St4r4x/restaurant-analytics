package com.aflokkat.sync;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.domain.Restaurant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private NycOpenDataClient apiClient;

    @Mock
    private RestaurantDAO restaurantDAO;

    @InjectMocks
    private SyncService syncService;

    // ── mapToRestaurants ──────────────────────────────────────────────────────

    @Test
    void mapToRestaurants_groupsRowsByCamis() {
        // Two rows for same restaurant but different inspection dates → 2 grades
        NycApiRestaurantDto r1a = row("111", "Pizza Place", "MANHATTAN", "A", "10", "2024-01-01T00:00:00.000");
        NycApiRestaurantDto r1b = row("111", "Pizza Place", "MANHATTAN", "B", "25", "2023-06-15T00:00:00.000");
        NycApiRestaurantDto r2  = row("222", "Sushi Bar",   "BROOKLYN",  "A", "8",  "2024-01-01T00:00:00.000");

        List<Restaurant> result = syncService.mapToRestaurants(Arrays.asList(r1a, r1b, r2));

        assertEquals(2, result.size());
        Restaurant pizza = result.stream().filter(r -> "111".equals(r.getRestaurantId())).findFirst().orElse(null);
        assertNotNull(pizza);
        assertEquals(2, pizza.getGrades().size());
    }

    @Test
    void mapToRestaurants_deduplicatesGradesByDate() {
        // Two rows for same restaurant and same date → only 1 grade (same inspection, multiple violations)
        NycApiRestaurantDto r1a = row("333", "Dup Restaurant", "MANHATTAN", "B", "20", "2024-01-01T00:00:00.000");
        NycApiRestaurantDto r1b = row("333", "Dup Restaurant", "MANHATTAN", "B", "20", "2024-01-01T00:00:00.000");

        List<Restaurant> result = syncService.mapToRestaurants(Arrays.asList(r1a, r1b));

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getGrades().size());
    }

    @Test
    void mapToRestaurants_skipsRowsWithNullCamis() {
        NycApiRestaurantDto bad = new NycApiRestaurantDto(); // camis is null
        NycApiRestaurantDto good = row("333", "Good Place", "QUEENS", "A", "5");

        List<Restaurant> result = syncService.mapToRestaurants(Arrays.asList(bad, good));

        assertEquals(1, result.size());
        assertEquals("333", result.get(0).getRestaurantId());
    }

    @Test
    void mapToRestaurants_normalizesBoroughName() {
        NycApiRestaurantDto dto = row("444", "Diner", "STATEN ISLAND", "A", "7");
        List<Restaurant> result = syncService.mapToRestaurants(Collections.singletonList(dto));

        assertEquals("Staten Island", result.get(0).getBorough());
    }

    @Test
    void mapToRestaurants_parsesCoordinates() {
        NycApiRestaurantDto dto = row("555", "Cafe", "BRONX", "A", "12");
        dto.setLatitude("40.7128");
        dto.setLongitude("-74.0060");

        List<Restaurant> result = syncService.mapToRestaurants(Collections.singletonList(dto));

        assertNotNull(result.get(0).getAddress().getCoord());
        assertEquals(2, result.get(0).getAddress().getCoord().size());
        assertEquals(-74.0060, result.get(0).getAddress().getCoord().get(0), 0.0001);
        assertEquals(40.7128,  result.get(0).getAddress().getCoord().get(1), 0.0001);
    }

    @Test
    void mapToRestaurants_ignoresInvalidCoordinates() {
        NycApiRestaurantDto dto = row("666", "Bar", "BROOKLYN", "B", "20");
        dto.setLatitude("not-a-number");
        dto.setLongitude("-74.0");

        List<Restaurant> result = syncService.mapToRestaurants(Collections.singletonList(dto));

        assertNull(result.get(0).getAddress().getCoord());
    }

    // ── runSync ───────────────────────────────────────────────────────────────

    @Test
    void runSync_returnsSuccessResult_whenApiAndUpsertSucceed() {
        NycApiRestaurantDto dto = row("777", "New Place", "MANHATTAN", "A", "5");
        when(apiClient.fetchAll()).thenReturn(Collections.singletonList(dto));
        when(restaurantDAO.upsertRestaurants(anyList())).thenReturn(1);

        SyncResult result = syncService.runSync();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getRawRecords());
        assertEquals(1, result.getUpsertedRestaurants());
        assertNull(result.getErrorMessage());
    }

    @Test
    void runSync_returnsFailureResult_whenApiFails() {
        when(apiClient.fetchAll()).thenThrow(new RuntimeException("API down"));

        SyncResult result = syncService.runSync();

        assertFalse(result.isSuccess());
        assertEquals("API down", result.getErrorMessage());
        verify(restaurantDAO, never()).upsertRestaurants(anyList());
    }

    @Test
    void runSync_storesLastResult() {
        when(apiClient.fetchAll()).thenReturn(Collections.emptyList());
        when(restaurantDAO.upsertRestaurants(anyList())).thenReturn(0);

        assertNull(syncService.getLastResult());
        syncService.runSync();
        assertNotNull(syncService.getLastResult());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private NycApiRestaurantDto row(String camis, String name, String boro, String grade, String score) {
        return row(camis, name, boro, grade, score, "2024-01-01T00:00:00.000");
    }

    private NycApiRestaurantDto row(String camis, String name, String boro, String grade, String score, String date) {
        NycApiRestaurantDto dto = new NycApiRestaurantDto();
        dto.setCamis(camis);
        dto.setDba(name);
        dto.setBoro(boro);
        dto.setGrade(grade);
        dto.setScore(score);
        dto.setInspectionDate(date);
        dto.setCuisineDescription("American");
        return dto;
    }
}
