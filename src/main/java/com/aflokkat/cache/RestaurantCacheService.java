package com.aflokkat.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.config.AppConfig;
import com.aflokkat.domain.Grade;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.dto.TopRestaurantEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis cache layer for expensive MongoDB aggregations and the "top restaurants" sorted set.
 *
 * Cache-aside pattern: on miss, the loader is called and the result is stored with a configurable TTL.
 * On data sync, all cache entries are invalidated and the sorted set is rebuilt.
 *
 * All Redis failures are swallowed with a warning log so the app degrades gracefully when Redis is down.
 */
@Service
public class RestaurantCacheService {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantCacheService.class);

    private static final String KEY_BY_BOROUGH            = "restaurants:by_borough";
    private static final String KEY_CUISINE_SCORES_PREFIX = "restaurants:cuisine_scores:";
    private static final String KEY_WORST_CUISINES_PREFIX = "restaurants:worst_cuisines:";
    static final String KEY_TOP                            = "restaurants:top";
    private static final String KEY_PATTERN               = "restaurants:*";

    // Reusable type tokens — TypeReference construction involves reflection, so we cache them
    private static final TypeReference<List<AggregationCount>>    TYPE_AGG_COUNT =
            new TypeReference<List<AggregationCount>>() {};
    private static final TypeReference<List<BoroughCuisineScore>> TYPE_BOROUGH_CUISINE_SCORE =
            new TypeReference<List<BoroughCuisineScore>>() {};
    private static final TypeReference<List<CuisineScore>>        TYPE_CUISINE_SCORE =
            new TypeReference<List<CuisineScore>>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public RestaurantCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttlSeconds = AppConfig.getRedisCacheTtlSeconds();
    }

    // ── Typed facade methods (keep key logic encapsulated) ────────────────────

    public List<AggregationCount> getOrLoadByBorough(Supplier<List<AggregationCount>> loader) {
        return getOrLoad(KEY_BY_BOROUGH, loader, TYPE_AGG_COUNT);
    }

    public List<BoroughCuisineScore> getOrLoadCuisineScores(String cuisine, Supplier<List<BoroughCuisineScore>> loader) {
        return getOrLoad(KEY_CUISINE_SCORES_PREFIX + cuisine, loader, TYPE_BOROUGH_CUISINE_SCORE);
    }

    public List<CuisineScore> getOrLoadWorstCuisines(String borough, int limit, Supplier<List<CuisineScore>> loader) {
        return getOrLoad(KEY_WORST_CUISINES_PREFIX + borough + ":" + limit, loader, TYPE_CUISINE_SCORE);
    }

    // ── Core cache-aside ──────────────────────────────────────────────────────

    /**
     * Cache-aside: returns the cached value for {@code key}, or calls {@code loader},
     * stores the result, and returns it. Redis failures fall through to the loader silently.
     */
    <T> T getOrLoad(String key, Supplier<T> loader, TypeReference<T> typeRef) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                logger.debug("Cache hit: {}", key);
                return objectMapper.readValue(cached, typeRef);
            }
        } catch (Exception e) {
            logger.warn("Cache read failed for key {}: {}", key, e.getMessage());
        }

        T value = loader.get();

        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), Duration.ofSeconds(ttlSeconds));
            logger.debug("Cache stored: {} (TTL {}s)", key, ttlSeconds);
        } catch (Exception e) {
            logger.warn("Cache write failed for key {}: {}", key, e.getMessage());
        }

        return value;
    }

    // ── Sorted set ────────────────────────────────────────────────────────────

    /**
     * Rebuilds the "top restaurants" sorted set from the freshly-synced restaurant list.
     * Score = latest inspection score (lower = healthier). Restaurants with no score are skipped.
     * Uses a single bulk ZADD to avoid N+1 Redis calls.
     */
    public void updateTopRestaurants(List<Restaurant> restaurants) {
        try {
            redis.delete(KEY_TOP);

            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            for (Restaurant r : restaurants) {
                if (r.getGrades() == null) continue;
                Integer score = null;
                for (Grade g : r.getGrades()) {
                    if (g.getScore() != null) { score = g.getScore(); break; }
                }
                if (score == null) continue;
                TopRestaurantEntry entry = new TopRestaurantEntry(
                        r.getRestaurantId(), r.getName(), r.getBorough(), r.getCuisine(), score);
                tuples.add(new DefaultTypedTuple<>(objectMapper.writeValueAsString(entry), score.doubleValue()));
            }

            if (!tuples.isEmpty()) {
                redis.opsForZSet().add(KEY_TOP, tuples);
            }
            logger.info("Top restaurants sorted set rebuilt: {} entries", tuples.size());
        } catch (Exception e) {
            logger.warn("Failed to update top restaurants sorted set: {}", e.getMessage());
        }
    }

    /**
     * Returns the {@code limit} restaurants with the lowest inspection scores (healthiest).
     */
    public List<TopRestaurantEntry> getTopRestaurants(int limit) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().rangeWithScores(KEY_TOP, 0, limit - 1);
            if (tuples == null || tuples.isEmpty()) return Collections.emptyList();

            List<TopRestaurantEntry> result = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                result.add(objectMapper.readValue(tuple.getValue(), TopRestaurantEntry.class));
            }
            return result;
        } catch (Exception e) {
            logger.warn("Failed to read top restaurants from Redis: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Invalidation ──────────────────────────────────────────────────────────

    /**
     * Deletes all {@code restaurants:*} keys. Called after a successful data sync.
     * Uses SCAN instead of KEYS to avoid blocking Redis on large keyspaces.
     */
    public void invalidateAll() {
        try {
            ScanOptions options = ScanOptions.scanOptions().match(KEY_PATTERN).count(100).build();
            List<String> toDelete = redis.execute((RedisCallback<List<String>>) conn -> {
                List<String> keys = new ArrayList<>();
                Cursor<byte[]> cursor = conn.scan(options);
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
                cursor.close();
                return keys;
            });
            if (toDelete != null && !toDelete.isEmpty()) {
                redis.delete(toDelete);
                logger.info("Cache invalidated: {} keys deleted", toDelete.size());
            }
        } catch (Exception e) {
            logger.warn("Cache invalidation failed: {}", e.getMessage());
        }
    }
}
