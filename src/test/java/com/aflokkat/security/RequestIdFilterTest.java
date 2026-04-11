package com.aflokkat.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
    }

    @Test
    void filter_setsXRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(response.getHeader("X-Request-ID"),
                "X-Request-ID response header must be set by RequestIdFilter");
    }

    @Test
    void filter_headerIsValidUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String header = response.getHeader("X-Request-ID");
        assertTrue(
            header.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "X-Request-ID must be a valid lowercase UUID v4 format");
    }

    @Test
    void filter_ignoresClientSuppliedRequestId() throws Exception {
        // D-01: server always generates fresh UUID — never trusts client header
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        request.addHeader("X-Request-ID", "attacker-controlled-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotEquals("attacker-controlled-value", response.getHeader("X-Request-ID"),
                "RequestIdFilter must not reuse client-supplied X-Request-ID header (log injection prevention)");
    }

    @Test
    void filter_clearsMdcAfterRequest() throws Exception {
        // D-03: MDC.remove in finally block prevents stale requestId on thread pool reuse
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get("requestId"),
                "MDC requestId must be removed after filter completes (prevents cross-request contamination)");
    }

    @Test
    void filter_generatesUniqueIdsPerRequest() throws Exception {
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilterInternal(req1, resp1, new MockFilterChain());

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/restaurants/stats");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilterInternal(req2, resp2, new MockFilterChain());

        assertNotEquals(resp1.getHeader("X-Request-ID"), resp2.getHeader("X-Request-ID"),
                "Each request must receive a distinct UUID");
    }
}
