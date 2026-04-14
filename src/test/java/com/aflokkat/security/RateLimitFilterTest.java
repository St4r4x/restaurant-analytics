package com.aflokkat.security;

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
        // Construct with a small threshold: 3 requests per minute for test speed
        // auth=3/min, restaurant=100/min (production-like defaults for existing tests)
        filter = new RateLimitFilter(3, 1);
    }

    @Test
    void filter_passes_beforeThreshold() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.1");

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilterInternal(request, response, chain);
            // Must not be 429
            assertEquals(200, response.getStatus(),
                    "Request " + (i + 1) + " should not be rate-limited");
        }
    }

    @Test
    void filter_returns429_afterThresholdExceeded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");

        // Drain the bucket (3 allowed)
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, new MockFilterChain());
        }

        // 4th request must get 429
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertEquals(429, response.getStatus(), "4th request should be rate-limited");
    }

    @Test
    void restaurantPath_returns429_afterRestaurantThreshold() throws Exception {
        // auth=10/min, restaurant=3/min (small restaurant threshold for test speed)
        RateLimitFilter restaurantFilter = new RateLimitFilter(10, 1, 3, 1);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restaurants/by-borough");
        request.setRemoteAddr("172.16.0.1");

        // Drain the restaurant bucket (3 allowed)
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            restaurantFilter.doFilterInternal(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus(),
                    "Restaurant request " + (i + 1) + " should not be rate-limited");
        }

        // 4th restaurant request must get 429
        MockHttpServletResponse response = new MockHttpServletResponse();
        restaurantFilter.doFilterInternal(request, response, new MockFilterChain());
        assertEquals(429, response.getStatus(), "4th restaurant request should be rate-limited");
    }

    @Test
    void authAndRestaurantBuckets_areIndependent() throws Exception {
        // auth=2/min, restaurant=2/min — both small to test independence
        RateLimitFilter dualFilter = new RateLimitFilter(2, 1, 2, 1);
        String ip = "192.168.99.1";

        // Drain the auth bucket (2 allowed)
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr(ip);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            dualFilter.doFilterInternal(req, resp, new MockFilterChain());
            assertEquals(200, resp.getStatus(), "Auth request " + (i + 1) + " should pass");
        }

        // Auth bucket exhausted — restaurant bucket must still be fresh
        MockHttpServletRequest restaurantReq = new MockHttpServletRequest("GET", "/api/restaurants/by-borough");
        restaurantReq.setRemoteAddr(ip);
        MockHttpServletResponse restaurantResp = new MockHttpServletResponse();
        dualFilter.doFilterInternal(restaurantReq, restaurantResp, new MockFilterChain());
        assertEquals(200, restaurantResp.getStatus(),
                "Restaurant request should pass even though auth bucket is exhausted");
    }
}
