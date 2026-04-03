package com.aflokkat.controller;

import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.dto.AtRiskEntry;
import com.aflokkat.service.RestaurantService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * Wave 0 test scaffold for STAT-01 through STAT-04.
 * All tests are @Disabled until AnalyticsController is created in Plan 06-02.
 * Pattern: @ExtendWith(MockitoExtension.class) + standaloneSetup — NEVER @WebMvcTest.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private RestaurantService restaurantService;

    @InjectMocks
    private AnalyticsController analyticsController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(analyticsController).build();
    }

    /**
     * STAT-01: GET /api/analytics/kpis returns 200 with all four KPI fields.
     */
    @Test
    @Disabled("Wave 0 stub — enable when AnalyticsController is created in Plan 06-02")
    void testKpis_returns200() throws Exception {
        when(restaurantService.countAll()).thenReturn(27000L);

        mockMvc.perform(get("/api/analytics/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRestaurants").exists())
                .andExpect(jsonPath("$.percentGradeA").exists())
                .andExpect(jsonPath("$.avgScore").exists())
                .andExpect(jsonPath("$.atRiskCount").exists());
    }

    /**
     * STAT-02: GET /api/analytics/borough-grades returns 200 with borough data.
     */
    @Test
    @Disabled("Wave 0 stub — enable when AnalyticsController is created in Plan 06-02")
    void testBoroughGrades_returns5Boroughs() throws Exception {
        mockMvc.perform(get("/api/analytics/borough-grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * STAT-03: GET /api/analytics/cuisine-rankings returns two ranked lists of 10.
     */
    @Test
    @Disabled("Wave 0 stub — enable when AnalyticsController is created in Plan 06-02")
    void testCuisineRankings_returnsTwoLists() throws Exception {
        CuisineScore cs = new CuisineScore("Italian", 12.4, 500);
        when(restaurantService.getWorstCuisinesByAverageScore(10))
                .thenReturn(Collections.nCopies(10, cs));
        when(restaurantService.getBestCuisinesByAverageScore(10))
                .thenReturn(Collections.nCopies(10, cs));

        mockMvc.perform(get("/api/analytics/cuisine-rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.best").isArray())
                .andExpect(jsonPath("$.worst").isArray());
    }

    /**
     * STAT-04: GET /api/analytics/at-risk returns 200 with data array containing entries.
     */
    @Test
    @Disabled("Wave 0 stub — enable when AnalyticsController is created in Plan 06-02")
    void testAtRisk_returnsEntries() throws Exception {
        AtRiskEntry entry = new AtRiskEntry();
        entry.setRestaurantId("12345");
        entry.setName("Dirty Diner");
        entry.setBorough("MANHATTAN");
        entry.setLastGrade("C");

        when(restaurantService.getAtRiskRestaurants(null, 50))
                .thenReturn(Collections.singletonList(entry));

        mockMvc.perform(get("/api/analytics/at-risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].restaurantId").value("12345"))
                .andExpect(jsonPath("$.data[0].name").value("Dirty Diner"))
                .andExpect(jsonPath("$.data[0].borough").value("MANHATTAN"))
                .andExpect(jsonPath("$.data[0].lastGrade").value("C"));
    }
}
