package com.st4r4x.controller;

import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.entity.LetterGrade;
import com.st4r4x.entity.Status;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.AuditLogRepository;
import com.st4r4x.repository.ReportRepository;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.service.AuditService;
import com.st4r4x.sync.CronScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for ADM-03: GET /api/reports/stats — aggregate counts, ADMIN only.
 * Pattern: @ExtendWith(MockitoExtension.class) + standaloneSetup — NEVER @WebMvcTest.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private CronScheduler cronScheduler;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private AdminController adminController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    @Test
    void listUsers_returns200_withUserList() throws Exception {
        UserEntity u1 = new UserEntity("alice", "alice@test.com", "hash", "ROLE_CUSTOMER");
        UserEntity u2 = new UserEntity("bob", "bob@test.com", "hash", "ROLE_CONTROLLER");
        when(userRepository.findAll()).thenReturn(Arrays.asList(u1, u2));

        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("alice"))
            .andExpect(jsonPath("$[0].role").value("ROLE_CUSTOMER"))
            .andExpect(jsonPath("$[1].username").value("bob"))
            .andExpect(jsonPath("$[1].role").value("ROLE_CONTROLLER"));
    }

    @Test
    void listUsers_doesNotReturnPasswordHash() throws Exception {
        UserEntity u = new UserEntity("alice", "alice@test.com", "secret-hash", "ROLE_CUSTOMER");
        when(userRepository.findAll()).thenReturn(Collections.singletonList(u));

        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void setUserRole_validRole_returns200() throws Exception {
        UserEntity u = new UserEntity("alice", "alice@test.com", "hash", "ROLE_CUSTOMER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(UserEntity.class))).thenReturn(u);

        mockMvc.perform(post("/api/admin/users/1/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ROLE_CONTROLLER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.role").value("ROLE_CONTROLLER"));
    }

    @Test
    void setUserRole_invalidRole_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ROLE_SUPERUSER\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void setUserRole_unknownUser_returns404() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/users/99/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ROLE_CONTROLLER\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("error"));
    }

    /**
     * ADM-03: GET /api/reports/stats returns 200 with byStatus and byGrade maps.
     */
    @Test
    void getStats_returns200_withByStatusAndByGrade() throws Exception {
        // Simulate GROUP BY results: Object[] { enumValue, Long count }
        when(reportRepository.countGroupByStatus()).thenReturn(Arrays.asList(
            new Object[]{Status.OPEN, 4L},
            new Object[]{Status.IN_PROGRESS, 2L},
            new Object[]{Status.RESOLVED, 11L}
        ));
        when(reportRepository.countGroupByGrade()).thenReturn(Arrays.asList(
            new Object[]{LetterGrade.A, 8L},
            new Object[]{LetterGrade.B, 5L},
            new Object[]{LetterGrade.C, 3L},
            new Object[]{LetterGrade.F, 1L}
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
            new Object[]{LetterGrade.A, 5L}
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

    @Test
    void getAuditLog_returns200_withPagedEntries() throws Exception {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setActorUsername("admin");
        entry.setActorRole("ROLE_ADMIN");
        entry.setAction(AuditAction.USER_ROLE_CHANGED);
        entry.setTargetType("User");
        entry.setTargetId("5");
        entry.setDetail("{\"oldRole\":\"ROLE_CUSTOMER\",\"newRole\":\"ROLE_CONTROLLER\"}");
        entry.setCreatedAt(new java.util.Date());

        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/audit?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].actorUsername").value("admin"))
            .andExpect(jsonPath("$.content[0].action").value("USER_ROLE_CHANGED"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getAuditLog_emptyPage_returns200() throws Exception {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any()))
            .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/admin/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(0));
    }
}
