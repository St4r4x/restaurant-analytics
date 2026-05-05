package com.st4r4x.controller;

import com.st4r4x.sync.OsmEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for POST /api/admin/osm-enrich — triggers full OSM re-enrichment, ADMIN only.
 * Pattern: @ExtendWith(MockitoExtension.class) + standaloneSetup — NEVER @WebMvcTest.
 * @WebMvcTest is prohibited in this project (Java 25 + Byte Buddy crash, see SecurityConfigTest).
 */
@ExtendWith(MockitoExtension.class)
class AdminOsmEnrichTest {

    @Mock
    private OsmEnrichmentService osmEnrichmentService;

    @Mock
    private com.st4r4x.repository.ReportRepository reportRepository;

    @InjectMocks
    private AdminController adminController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    /**
     * POST /api/admin/osm-enrich delegates to osmEnrichmentService.enrichAll()
     * and returns HTTP 202 Accepted.
     * standaloneSetup bypasses Spring Security; we verify the method-level
     * @PreAuthorize annotation separately (see postOsmEnrich_hasPreAuthorizeAdminRole).
     */
    @Test
    void postOsmEnrich_triggersEnrichAll_returns202() throws Exception {
        mockMvc.perform(post("/api/admin/osm-enrich"))
                .andExpect(status().isAccepted());
        verify(osmEnrichmentService, times(1)).enrichAll();
    }

    /**
     * Verifies that the triggerOsmEnrich method is annotated with
     * @PreAuthorize("hasRole('ADMIN')"), enforcing the ADMIN-only contract.
     * standaloneSetup does not evaluate security annotations, so this test
     * inspects the annotation directly to guarantee the guard is present.
     */
    @Test
    void postOsmEnrich_hasPreAuthorizeAdminRole() throws NoSuchMethodException {
        Method method = AdminController.class.getMethod("triggerOsmEnrich");
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "@PreAuthorize must be present on triggerOsmEnrich()");
        assert annotation.value().equals("hasRole('ADMIN')")
                : "Expected hasRole('ADMIN') but got: " + annotation.value();
    }
}
