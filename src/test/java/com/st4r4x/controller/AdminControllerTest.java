package com.st4r4x.controller;

import com.st4r4x.entity.InspectionGrade;
import com.st4r4x.entity.Status;
import com.st4r4x.repository.ReportRepository;
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
 * Tests for ADM-03: GET /api/reports/stats — aggregate counts, ADMIN only.
 * Pattern: @ExtendWith(MockitoExtension.class) + standaloneSetup — NEVER @WebMvcTest.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private AdminController adminController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    /**
     * ADM-03: GET /api/reports/stats returns 200 with byStatus and byGrade maps.
     */
    @Test
    void getStats_returns200_withByStatusAndByInspectionGrade() throws Exception {
        // Simulate GROUP BY results: Object[] { enumValue, Long count }
        when(reportRepository.countGroupByStatus()).thenReturn(Arrays.asList(
            new Object[]{Status.OPEN, 4L},
            new Object[]{Status.IN_PROGRESS, 2L},
            new Object[]{Status.RESOLVED, 11L}
        ));
        when(reportRepository.countGroupByGrade()).thenReturn(Arrays.asList(
            new Object[]{InspectionGrade.A, 8L},
            new Object[]{InspectionGrade.B, 5L},
            new Object[]{InspectionGrade.C, 3L},
            new Object[]{InspectionGrade.F, 1L}
        ));

        mockMvc.perform(get("/api/reports/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.byStatus.OPEN").value(4))
            .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(2))
            .andExpect(jsonPath("$.byStatus.RESOLVED").value(11))
            .andExpect(jsonPath("$.byGrade.A").value(8))
            .andExpect(jsonPath("$.byGrade.B").value(5))
            .andExpect(jsonPath("$.byGrade.C").value(3))
            .andExpect(jsonPath("$.byGrade.F").value(1));
    }

    /**
     * ADM-03: Missing enum values in GROUP BY result default to 0.
     * If no reports have grade F, the query returns no row for F;
     * the response must still include "F": 0.
     */
    @Test
    void getStats_returns0_forMissingEnumValues() throws Exception {
        when(reportRepository.countGroupByStatus()).thenReturn(Collections.singletonList(
            new Object[]{Status.OPEN, 3L}
        ));
        when(reportRepository.countGroupByGrade()).thenReturn(Collections.singletonList(
            new Object[]{InspectionGrade.A, 5L}
        ));

        mockMvc.perform(get("/api/reports/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.byStatus.OPEN").value(3))
            .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(0))
            .andExpect(jsonPath("$.byStatus.RESOLVED").value(0))
            .andExpect(jsonPath("$.byGrade.A").value(5))
            .andExpect(jsonPath("$.byGrade.F").value(0));
    }

    /**
     * ADM-03: Response must NOT contain individual report data.
     * No "id", "restaurantId", "notes", or "userId" fields in the response.
     */
    @Test
    void getStats_doesNotLeakIndividualReportData() throws Exception {
        when(reportRepository.countGroupByStatus()).thenReturn(Collections.emptyList());
        when(reportRepository.countGroupByGrade()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/reports/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.restaurantId").doesNotExist())
            .andExpect(jsonPath("$.notes").doesNotExist())
            .andExpect(jsonPath("$.userId").doesNotExist());
    }
}
