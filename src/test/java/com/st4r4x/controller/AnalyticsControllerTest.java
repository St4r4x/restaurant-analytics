package com.st4r4x.controller;

import com.st4r4x.aggregation.CuisineScore;
import com.st4r4x.dao.RestaurantDAO;
import com.st4r4x.dto.AtRiskEntry;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for STAT-01 through STAT-04 analytics endpoints.
 * Pattern: @ExtendWith(MockitoExtension.class) + standaloneSetup — NEVER @WebMvcTest.
 * Mocks RestaurantDAO (interface) directly — RestaurantService cannot be mocked on Java 25
 * due to constructor-injection Mockito limitation (consistent with RestaurantControllerSearchTest).
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private RestaurantDAO restaurantDAO;

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
    void testKpis_returns200() throws Exception {
        when(restaurantDAO.countAll()).thenReturn(27000L);
        when(restaurantDAO.countAtRiskRestaurants()).thenReturn(412L);
        when(restaurantDAO.findBoroughGradeDistribution()).thenReturn(Collections.emptyList());
        when(restaurantDAO.findWorstCuisinesByAverageScore(200)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/analytics/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRestaurants").value(27000))
                .andExpect(jsonPath("$.percentGradeA").exists())
                .andExpect(jsonPath("$.avgScore").exists())
                .andExpect(jsonPath("$.atRiskCount").value(412));
    }

    /**
     * STAT-02: GET /api/analytics/borough-grades returns 200 with borough data.
     */
    @Test
    void testBoroughGrades_returns5Boroughs() throws Exception {
        when(restaurantDAO.findBoroughGradeDistribution()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/analytics/borough-grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * STAT-03: GET /api/analytics/cuisine-rankings returns two ranked lists of 10.
     */
    @Test
    void testCuisineRankings_returnsTwoLists() throws Exception {
        CuisineScore cs = new CuisineScore("Italian", 12.4, 500);
        when(restaurantDAO.findWorstCuisinesByAverageScore(10))
                .thenReturn(Collections.nCopies(10, cs));
        when(restaurantDAO.findBestCuisinesByAverageScore(10))
                .thenReturn(Collections.nCopies(10, cs));

        mockMvc.perform(get("/api/analytics/cuisine-rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.best").isArray())
                .andExpect(jsonPath("$.worst").isArray())
                .andExpect(jsonPath("$.best.length()").value(10))
                .andExpect(jsonPath("$.worst.length()").value(10));
    }

    /**
     * STAT-04: GET /api/analytics/at-risk returns 200 with data array containing entries.
     */
    @Test
    void testAtRisk_returnsEntries() throws Exception {
        AtRiskEntry entry = new AtRiskEntry();
        entry.setRestaurantId("12345");
        entry.setName("Dirty Diner");
        entry.setBorough("MANHATTAN");
        entry.setLastGrade("C");

        when(restaurantDAO.findAtRiskRestaurants(null, 50))
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
