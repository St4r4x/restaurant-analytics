package com.st4r4x.security;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Generates a server-side UUID per request, stores it in SLF4J MDC as "requestId",
 * and returns it as the X-Request-ID response header on every HTTP response.
 *
 * Registration: @Component @Order(0) — servlet container registers this filter.
 * It runs BEFORE RateLimitFilter (@Order 1) and BEFORE Spring Security FilterChainProxy.
 * All requests receive a request ID: authenticated, unauthenticated, Swagger, sync, public.
 *
 * Do NOT also add this filter to SecurityConfig via http.addFilterBefore() — that would
 * cause double execution (MDC set/cleared twice per request). Follow the same pattern as
 * RateLimitFilter: @Component only, no SecurityConfig registration.
 *
 * Client-supplied X-Request-ID request headers are intentionally ignored (D-01):
 * always generate server-side UUID to prevent log injection and ensure UUID format.
 *
 * No async endpoints in this project — shouldNotFilterAsyncDispatch() default (true) is correct.
 */
@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
