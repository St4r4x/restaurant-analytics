package com.aflokkat.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import com.aflokkat.config.AppConfig;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting for /api/auth/** (10 req/min) and /api/restaurants/** (100 req/min).
 *
 * Two independent Bucket4j token-bucket maps — auth and restaurant paths share no state.
 * Registered as @Component — Spring Boot auto-registers in the servlet filter chain.
 * Do NOT also register via http.addFilterBefore() in SecurityConfig (would double-apply).
 *
 * Known limitation: bucket maps are unbounded. For academic scope this is acceptable.
 * In production, replace ConcurrentHashMap with Guava CacheBuilder.expireAfterAccess().
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> restaurantBuckets = new ConcurrentHashMap<>();
    private final int authMax;
    private final int authWindow;
    private final int restaurantMax;
    private final int restaurantWindow;

    /**
     * Default constructor used by Spring — reads config from AppConfig.
     */
    public RateLimitFilter() {
        this(
            AppConfig.getAuthRateLimitRequests(),
            AppConfig.getAuthRateLimitWindowMinutes(),
            AppConfig.getRestaurantRateLimitRequests(),
            AppConfig.getRestaurantRateLimitWindowMinutes()
        );
    }

    /**
     * Test constructor — allows injecting all four threshold values directly.
     */
    public RateLimitFilter(int authMax, int authWindow, int restaurantMax, int restaurantWindow) {
        this.authMax = authMax;
        this.authWindow = authWindow;
        this.restaurantMax = restaurantMax;
        this.restaurantWindow = restaurantWindow;
    }

    /**
     * Backward-compatible 2-arg constructor for tests that only test auth paths.
     * Restaurant bucket uses production defaults (100/min) when this constructor is used.
     */
    public RateLimitFilter(int authMax, int authWindow) {
        this(authMax, authWindow,
             AppConfig.getRestaurantRateLimitRequests(),
             AppConfig.getRestaurantRateLimitWindowMinutes());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Apply filter only to auth and restaurant API paths; skip everything else
        return !uri.startsWith("/api/auth/") && !uri.startsWith("/api/restaurants/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        String uri = request.getRequestURI();

        Bucket bucket;
        if (uri.startsWith("/api/auth/")) {
            bucket = authBuckets.computeIfAbsent(ip, k -> newBucket(authMax, authWindow));
        } else {
            // /api/restaurants/** path
            bucket = restaurantBuckets.computeIfAbsent(ip, k -> newBucket(restaurantMax, restaurantWindow));
        }

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Too many requests\"}");
        }
    }

    private Bucket newBucket(int max, int windowMinutes) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(max, Refill.greedy(max, Duration.ofMinutes(windowMinutes))))
                .build();
    }
}
