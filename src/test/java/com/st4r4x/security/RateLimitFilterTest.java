package com.st4r4x.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        // auth: 3 req/min, search: 5 req/min — small values for test speed
        filter = new RateLimitFilter(3, 1, 5, 1);
    }

    @Test
    void auth_passes_beforeThreshold() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.1");

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus(), "Request " + (i + 1) + " should not be rate-limited");
        }
    }

    @Test
    void auth_returns429_afterThresholdExceeded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");

        for (int i = 0; i < 3; i++) {
            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertEquals(429, response.getStatus(), "4th auth request should be rate-limited");
    }

    @Test
    void search_passes_beforeThreshold() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/search");
        request.setRemoteAddr("192.168.2.1");

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus(), "Search request " + (i + 1) + " should not be rate-limited");
        }
    }

    @Test
    void search_returns429_afterThresholdExceeded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/search");
        request.setRemoteAddr("10.0.0.2");

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertEquals(429, response.getStatus(), "6th search request should be rate-limited");
    }

    @Test
    void auth_and_search_buckets_are_independent() throws Exception {
        // Drain auth bucket for one IP
        MockHttpServletRequest authReq = new MockHttpServletRequest("POST", "/api/auth/login");
        authReq.setRemoteAddr("10.0.0.3");
        for (int i = 0; i < 3; i++) {
            filter.doFilterInternal(authReq, new MockHttpServletResponse(), new MockFilterChain());
        }

        // Same IP should still pass on search (separate bucket)
        MockHttpServletRequest searchReq = new MockHttpServletRequest("GET", "/api/restaurants/search");
        searchReq.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(searchReq, response, new MockFilterChain());
        assertEquals(200, response.getStatus(), "Search bucket should be independent from auth bucket");
    }
}
