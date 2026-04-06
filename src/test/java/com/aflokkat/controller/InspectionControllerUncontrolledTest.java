package com.aflokkat.controller;

import com.aflokkat.dao.RestaurantDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for DISC-02 uncontrolled restaurant endpoints.
 * Pattern: @ExtendWith(MockitoExtension.class) + standaloneSetup — NEVER @WebMvcTest.
 * Mocks RestaurantDAO (interface) directly — RestaurantService cannot be mocked on Java 25
 * due to constructor-injection Mockito limitation (consistent with AnalyticsControllerTest).
 */
@ExtendWith(MockitoExtension.class)
public class InspectionControllerUncontrolledTest {

    @Mock
    private RestaurantDAO restaurantDAO;

    @InjectMocks
    private InspectionController inspectionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inspectionController).build();
    }

    /**
     * DISC-02: GET /api/inspection/uncontrolled returns 200 with status "success" and data array.
     */
    @Test
    public void testUncontrolled_returns200() throws Exception {
        when(restaurantDAO.findUncontrolled(null, 500)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/inspection/uncontrolled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * DISC-02: GET /api/inspection/uncontrolled/export.csv returns 200 with Content-Type text/csv.
     */
    @Test
    public void testExportCsv_returnsTextCsv() throws Exception {
        when(restaurantDAO.findUncontrolled(null, 5000)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/inspection/uncontrolled/export.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")));
    }
}
