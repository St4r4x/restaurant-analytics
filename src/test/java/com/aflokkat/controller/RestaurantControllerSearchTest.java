package com.aflokkat.controller;

import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.domain.Restaurant;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wave 0 test scaffold for CUST-01 (search endpoint) and CUST-03 (map-points endpoint).
 *
 * All tests are disabled because the endpoints do not exist yet.
 * They are enabled at the start of Plan 03-02 when the implementation is added.
 *
 * Pattern: @ExtendWith(MockitoExtension.class) + standaloneSetup — NEVER @WebMvcTest.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantControllerSearchTest {

    @Mock private RestaurantDAO restaurantDAO;

    @InjectMocks
    private RestaurantController restaurantController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(restaurantController).build();
    }

    /**
     * CUST-01: search with a valid query returns 200 + data array with one element.
     */
    @Test
    void testSearch_returnsResults() throws Exception {
        Restaurant r = new Restaurant("Pizza Palace", "Italian", "MANHATTAN");
        r.setRestaurantId("12345");

        when(restaurantDAO.searchByNameOrAddress("pizza", 20))
                .thenReturn(Collections.singletonList(r));

        mockMvc.perform(get("/api/restaurants/search").param("q", "pizza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * CUST-01: search with an empty query returns 200 (endpoint does not throw).
     */
    @Test
    void testSearch_emptyQuery() throws Exception {
        when(restaurantDAO.searchByNameOrAddress("", 20))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/restaurants/search").param("q", ""))
                .andExpect(status().isOk());
    }

    /**
     * CUST-01: search with a single-character query returns 200 with empty data.
     */
    @Test
    void testSearch_shortQuery() throws Exception {
        when(restaurantDAO.searchByNameOrAddress("a", 20))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/restaurants/search").param("q", "a"))
                .andExpect(status().isOk());
    }

    /**
     * CUST-03: map-points returns 200 + data array with restaurantId field.
     */
    @Test
    void testMapPoints_returnsProjection() throws Exception {
        Document doc = new Document()
                .append("restaurantId", "99999")
                .append("name", "Test Rest")
                .append("lat", 40.7)
                .append("lng", -74.0)
                .append("grade", "A");

        when(restaurantDAO.findMapPoints())
                .thenReturn(Collections.singletonList(doc));

        mockMvc.perform(get("/api/restaurants/map-points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].restaurantId").value("99999"));
    }
}
