package com.st4r4x.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import com.st4r4x.config.AppConfig;

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
 * Per-IP rate limiting for /api/auth/** endpoints using Bucket4j token-bucket.
 *
 * Registered as a @Component — Spring Boot auto-registers it in the servlet filter chain.
 * Do NOT also register via http.addFilterBefore() in SecurityConfig (would double-apply).
 *
 * Known limitation: buckets Map is unbounded. For academic scope this is acceptable.
 * In production, replace ConcurrentHashMap with Guava CacheBuilder.expireAfterAccess().
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final int windowMinutes;

    /**
     * Default constructor used by Spring — reads config from AppConfig.
     */
    public RateLimitFilter() {
        this(AppConfig.getAuthRateLimitRequests(), AppConfig.getAuthRateLimitWindowMinutes());
    }

    /**
     * Test constructor — allows injecting threshold values directly without AppConfig.
     */
    public RateLimitFilter(int maxRequests, int windowMinutes) {
        this.maxRequests = maxRequests;
        this.windowMinutes = windowMinutes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply to auth endpoints
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                      .addLimit(Bandwidth.classic(
                              maxRequests,
                              Refill.greedy(maxRequests, Duration.ofMinutes(windowMinutes))))
                      .build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Too many requests\"}");
        }
    }
}
