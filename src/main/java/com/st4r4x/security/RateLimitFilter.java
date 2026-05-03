package com.st4r4x.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import com.st4r4x.config.AppConfig;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting using Bucket4j token-bucket algorithm.
 *
 * Two tiers:
 *   - /api/auth/**          : strict (default 10 req/min) — brute-force protection
 *   - /api/restaurants/search, /api/restaurants/map-points : relaxed (default 100 req/min) — scraping protection
 *
 * Registered as a @Component — Spring Boot auto-registers it in the servlet filter chain.
 * Do NOT also register via http.addFilterBefore() in SecurityConfig (would double-apply).
 *
 * Known limitation: bucket Maps are unbounded. For production at scale, replace
 * ConcurrentHashMap with Guava CacheBuilder.expireAfterAccess().
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> authBuckets       = new ConcurrentHashMap<>();
    private final Map<String, Bucket> searchBuckets     = new ConcurrentHashMap<>();
    private final int authMaxRequests;
    private final int authWindowMinutes;
    private final int searchMaxRequests;
    private final int searchWindowMinutes;

    public RateLimitFilter() {
        this(
            AppConfig.getAuthRateLimitRequests(),
            AppConfig.getAuthRateLimitWindowMinutes(),
            AppConfig.getRestaurantRateLimitRequests(),
            AppConfig.getRestaurantRateLimitWindowMinutes()
        );
    }

    public RateLimitFilter(int authMaxRequests, int authWindowMinutes,
                           int searchMaxRequests, int searchWindowMinutes) {
        this.authMaxRequests    = authMaxRequests;
        this.authWindowMinutes  = authWindowMinutes;
        this.searchMaxRequests  = searchMaxRequests;
        this.searchWindowMinutes = searchWindowMinutes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/auth/")
            && !uri.equals("/api/restaurants/search")
            && !uri.equals("/api/restaurants/map-points");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        String ip  = request.getRemoteAddr();

        Bucket bucket;
        if (uri.startsWith("/api/auth/")) {
            bucket = authBuckets.computeIfAbsent(ip, k -> newBucket(authMaxRequests, authWindowMinutes));
        } else {
            bucket = searchBuckets.computeIfAbsent(ip, k -> newBucket(searchMaxRequests, searchWindowMinutes));
        }

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Too many requests\"}");
        }
    }

    private static Bucket newBucket(int maxRequests, int windowMinutes) {
        return Bucket.builder()
                     .addLimit(Bandwidth.classic(
                             maxRequests,
                             Refill.greedy(maxRequests, Duration.ofMinutes(windowMinutes))))
                     .build();
    }
}
