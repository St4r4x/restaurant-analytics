package com.aflokkat.controller;

import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.dto.ReportRequest;
import com.aflokkat.entity.Grade;
import com.aflokkat.entity.InspectionReportEntity;
import com.aflokkat.entity.Status;
import com.aflokkat.entity.UserEntity;
import com.aflokkat.repository.ReportRepository;
import com.aflokkat.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock private ReportRepository reportRepository;
    @Mock private RestaurantDAO restaurantDAO;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ReportController reportController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reportController).build();
        // Set up a default CONTROLLER security context for tests that need an authenticated user
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "ctrl_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // helper: build a persisted entity
    private InspectionReportEntity makeEntity(Long id, String restaurantId, Grade grade, Status status) {
        InspectionReportEntity e = new InspectionReportEntity();
        e.setId(id);
        e.setRestaurantId(restaurantId);
        e.setGrade(grade);
        e.setStatus(status);
        e.setCreatedAt(new Date());
        e.setUpdatedAt(new Date());
        UserEntity user = new UserEntity();
        user.setId(42L);
        user.setUsername("ctrl_user");
        e.setUser(user);
        return e;
    }

    // ── CTRL-01: create report ─────────────────────────────────────────────

    @Test
    void createReport_returns201WithEnrichedData_onValidRequest() throws Exception {
        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));

        InspectionReportEntity saved = makeEntity(1L, "R1", Grade.A, Status.OPEN);
        when(reportRepository.save(any(InspectionReportEntity.class))).thenReturn(saved);

        Restaurant restaurant = new Restaurant("Joe's", "Italian", "Manhattan");
        when(restaurantDAO.findByRestaurantId("R1")).thenReturn(restaurant);

        ReportRequest req = new ReportRequest();
        req.setRestaurantId("R1");
        req.setGrade(Grade.A);
        req.setStatus(Status.OPEN);

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.restaurantName").value("Joe's"))
                .andExpect(jsonPath("$.data.borough").value("Manhattan"))
                .andExpect(jsonPath("$.data.grade").value("A"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void createReport_returns400_whenRestaurantIdMissing() throws Exception {
        ReportRequest req = new ReportRequest();
        req.setGrade(Grade.A);
        // restaurantId intentionally null

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("restaurantId is required"));
    }

    @Test
    void createReport_enrichesWithNullFields_whenRestaurantNotInMongo() throws Exception {
        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));

        InspectionReportEntity saved = makeEntity(2L, "UNKNOWN", Grade.B, Status.OPEN);
        when(reportRepository.save(any(InspectionReportEntity.class))).thenReturn(saved);
        when(restaurantDAO.findByRestaurantId("UNKNOWN")).thenReturn(null);

        ReportRequest req = new ReportRequest();
        req.setRestaurantId("UNKNOWN");
        req.setGrade(Grade.B);

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.restaurantName").value(nullValue()));
    }

    // ── CTRL-02: list reports ──────────────────────────────────────────────

    @Test
    void listReports_returnsOnlyCallerReports_notOtherControllers() throws Exception {
        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));

        InspectionReportEntity e1 = makeEntity(1L, "R1", Grade.A, Status.OPEN);
        InspectionReportEntity e2 = makeEntity(2L, "R2", Grade.B, Status.RESOLVED);
        when(reportRepository.findByUserId(42L)).thenReturn(Arrays.asList(e1, e2));
        when(restaurantDAO.findByRestaurantId(anyString())).thenReturn(null);

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.data", hasSize(2)));

        verify(reportRepository).findByUserId(42L);
        verify(reportRepository, never()).findByUserIdAndStatus(any(), any());
    }

    @Test
    void listReports_filtersByStatus_whenStatusParamPresent() throws Exception {
        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));

        InspectionReportEntity e1 = makeEntity(1L, "R1", Grade.A, Status.OPEN);
        when(reportRepository.findByUserIdAndStatus(42L, Status.OPEN)).thenReturn(Collections.singletonList(e1));
        when(restaurantDAO.findByRestaurantId(anyString())).thenReturn(null);

        mockMvc.perform(get("/api/reports").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        verify(reportRepository).findByUserIdAndStatus(42L, Status.OPEN);
        verify(reportRepository, never()).findByUserId(any());
    }

    @Test
    void listReports_returnsAll_whenStatusParamAbsent() throws Exception {
        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));

        when(reportRepository.findByUserId(42L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        verify(reportRepository).findByUserId(42L);
        verify(reportRepository, never()).findByUserIdAndStatus(any(), any());
    }

    // ── CTRL-03: patch report ──────────────────────────────────────────────
    @Test void patchReport_updatesOwnedReport_andReturns200() { assumeTrue(false, "not implemented"); }
    @Test void patchReport_returns403_whenNotOwner() { assumeTrue(false, "not implemented"); }
    @Test void patchReport_appliesOnlyNonNullFields_leavingOthersUnchanged() { assumeTrue(false, "not implemented"); }

    // ── CTRL-04: photo upload ──────────────────────────────────────────────
    @Test void photoUpload_savesFileAndUpdatesPhotoPath() { assumeTrue(false, "not implemented"); }
    @Test void photoUpload_returns404_whenReportNotFound() { assumeTrue(false, "not implemented"); }
    @Test void getPhoto_streamsFileWithCorrectContentType() { assumeTrue(false, "not implemented"); }
}
