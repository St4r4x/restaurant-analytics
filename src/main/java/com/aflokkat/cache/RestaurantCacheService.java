package com.aflokkat.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

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

    public static final String KEY_BY_BOROUGH            = "restaurants:by_borough";
    public static final String KEY_CUISINE_SCORES_PREFIX = "restaurants:cuisine_scores:";
    public static final String KEY_WORST_CUISINES_PREFIX = "restaurants:worst_cuisines:";
    public static final String KEY_TOP                   = "restaurants:top";
    private static final String KEY_PATTERN       = "restaurants:*";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public RestaurantCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttlSeconds = AppConfig.getRedisCacheTtlSeconds();
    }

    /**
     * Cache-aside: returns the cached value for {@code key}, or calls {@code loader},
     * stores the result, and returns it. Redis failures fall through to the loader silently.
     */
    public <T> T getOrLoad(String key, Supplier<T> loader, TypeReference<T> typeRef) {
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

    /**
     * Rebuilds the "top restaurants" sorted set from the freshly-synced restaurant list.
     * Score = latest inspection score (lower = healthier). Restaurants with no score are skipped.
     */
    public void updateTopRestaurants(List<Restaurant> restaurants) {
        try {
            redis.delete(KEY_TOP);
            int added = 0;
            for (Restaurant r : restaurants) {
                if (r.getGrades() == null) continue;
                Integer score = null;
                for (Grade g : r.getGrades()) {
                    if (g.getScore() != null) { score = g.getScore(); break; }
                }
                if (score == null) continue;
                TopRestaurantEntry entry = new TopRestaurantEntry(
                        r.getRestaurantId(), r.getName(), r.getBorough(), r.getCuisine(), score);
                redis.opsForZSet().add(KEY_TOP, objectMapper.writeValueAsString(entry), score.doubleValue());
                added++;
            }
            logger.info("Top restaurants sorted set rebuilt: {} entries", added);
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

    /**
     * Deletes all {@code restaurants:*} keys. Called after a successful data sync.
     */
    public void invalidateAll() {
        try {
            Set<String> keys = redis.keys(KEY_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
                logger.info("Cache invalidated: {} keys deleted", keys.size());
            }
        } catch (Exception e) {
            logger.warn("Cache invalidation failed: {}", e.getMessage());
        }
    }
}
