package com.aflokkat.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import com.aflokkat.domain.Grade;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.dto.TopRestaurantEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RestaurantCacheServiceTest {

    @Mock StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    @Mock ValueOperations<String, String> valueOps;
    @SuppressWarnings("unchecked")
    @Mock ZSetOperations<String, String> zSetOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestaurantCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new RestaurantCacheService(redis, objectMapper);
    }

    // ── getOrLoad ────────────────────────────────────────────────────────────

    @Test
    void getOrLoad_returnsCachedValue_onCacheHit() throws Exception {
        List<String> expected = Arrays.asList("Manhattan", "Brooklyn");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("restaurants:by_borough")).thenReturn(objectMapper.writeValueAsString(expected));

        List<String> result = cacheService.getOrLoad(
                "restaurants:by_borough",
                () -> { throw new AssertionError("loader must not be called on cache hit"); },
                new TypeReference<List<String>>() {});

        assertEquals(expected, result);
        verify(valueOps, never()).set(any(), any(), any());
    }

    @Test
    void getOrLoad_callsLoaderAndCaches_onCacheMiss() throws Exception {
        List<String> expected = Arrays.asList("Queens");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        List<String> result = cacheService.getOrLoad(
                "restaurants:by_borough",
                () -> expected,
                new TypeReference<List<String>>() {});

        assertEquals(expected, result);
        verify(valueOps).set(eq("restaurants:by_borough"), anyString(), any());
    }

    @Test
    void getOrLoad_stillReturnsLoaderResult_whenRedisWriteFails() {
        List<String> expected = Arrays.asList("Bronx");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        doThrow(new RuntimeException("Redis down")).when(valueOps).set(any(), any(), any());

        List<String> result = cacheService.getOrLoad(
                "restaurants:by_borough",
                () -> expected,
                new TypeReference<List<String>>() {});

        assertEquals(expected, result);
    }

    // ── updateTopRestaurants ─────────────────────────────────────────────────

    @Test
    void updateTopRestaurants_skipsRestaurantsWithNoScore() {
        Restaurant r = new Restaurant();
        r.setRestaurantId("123");
        r.setName("No Score Place");
        r.setGrades(Collections.singletonList(new Grade())); // grade with null score


        cacheService.updateTopRestaurants(Collections.singletonList(r));

        verify(zSetOps, never()).add(any(), any(), anyDouble());
    }

    @Test
    void updateTopRestaurants_addsRestaurantWithScore() throws Exception {
        Grade grade = new Grade();
        grade.setScore(10);

        Restaurant r = new Restaurant();
        r.setRestaurantId("456");
        r.setName("Healthy Place");
        r.setBorough("Manhattan");
        r.setCuisine("Japanese");
        r.setGrades(Collections.singletonList(grade));

        when(redis.opsForZSet()).thenReturn(zSetOps);

        cacheService.updateTopRestaurants(Collections.singletonList(r));

        verify(zSetOps).add(eq(RestaurantCacheService.KEY_TOP), anyString(), eq(10.0));
    }

    // ── getTopRestaurants ────────────────────────────────────────────────────

    @Test
    void getTopRestaurants_returnsEmptyList_whenSortedSetIsEmpty() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeWithScores(RestaurantCacheService.KEY_TOP, 0, 9)).thenReturn(Collections.emptySet());

        List<TopRestaurantEntry> result = cacheService.getTopRestaurants(10);

        assertTrue(result.isEmpty());
    }

    // ── invalidateAll ────────────────────────────────────────────────────────

    @Test
    void invalidateAll_deletesAllMatchingKeys() {
        Set<String> keys = new HashSet<>(Arrays.asList("restaurants:by_borough", "restaurants:top"));
        when(redis.keys("restaurants:*")).thenReturn(keys);

        cacheService.invalidateAll();

        verify(redis).delete(keys);
    }

    @Test
    void invalidateAll_doesNothing_whenNoKeysExist() {
        when(redis.keys("restaurants:*")).thenReturn(Collections.emptySet());

        cacheService.invalidateAll();

        verify(redis, never()).delete(anyCollection());
    }
}
