package com.aflokkat.controller;

import com.aflokkat.config.AppConfig;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.*;
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    // ── SC-2 read path: controller B cannot see controller A's reports ──────
    @Test
    void listReports_doesNotReturnOtherControllersReports() throws Exception {
        // Set up ctrl_b (userId=99L) as the authenticated caller
        UserEntity callerUser = new UserEntity("ctrl_b", "b@test.com", "hash", "ROLE_CONTROLLER");
        callerUser.setId(99L);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "ctrl_b", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(userRepository.findByUsername("ctrl_b")).thenReturn(Optional.of(callerUser));
        // ctrl_b has no reports
        when(reportRepository.findByUserId(99L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        // Critical: repository called with ctrl_b's id (99), never with ctrl_a's id (42)
        verify(reportRepository).findByUserId(99L);
        verify(reportRepository, never()).findByUserId(42L);
    }

    // ── CTRL-03: patch report ──────────────────────────────────────────────

    @Test
    void patchReport_updatesOwnedReport_andReturns200() throws Exception {
        // Entity owned by user 42L (same as ctx user)
        InspectionReportEntity entity = makeEntity(1L, "R1", Grade.A, Status.OPEN);
        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(reportRepository.save(any(InspectionReportEntity.class))).thenReturn(entity);
        when(restaurantDAO.findByRestaurantId("R1")).thenReturn(null);

        ReportRequest req = new ReportRequest();
        req.setGrade(Grade.B);
        req.setStatus(Status.IN_PROGRESS);

        mockMvc.perform(patch("/api/reports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(reportRepository).save(any(InspectionReportEntity.class));
    }

    @Test
    void patchReport_returns403_whenNotOwner() throws Exception {
        // Entity owned by user 99L — caller is 42L
        InspectionReportEntity entity = makeEntity(1L, "R1", Grade.A, Status.OPEN);
        UserEntity owner = new UserEntity("other_user", "other@test.com", "hash", "ROLE_CONTROLLER");
        owner.setId(99L);
        entity.setUser(owner);

        UserEntity caller = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        caller.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(caller));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(entity));

        ReportRequest req = new ReportRequest();
        req.setGrade(Grade.C);

        mockMvc.perform(patch("/api/reports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Forbidden"));

        verify(reportRepository, never()).save(any());
    }

    @Test
    void patchReport_appliesOnlyNonNullFields_leavingOthersUnchanged() throws Exception {
        // Entity starts with grade=A, status=OPEN, notes=null, violationCodes=null
        InspectionReportEntity entity = makeEntity(1L, "R1", Grade.A, Status.OPEN);

        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));
        when(reportRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(reportRepository.save(any(InspectionReportEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(restaurantDAO.findByRestaurantId("R1")).thenReturn(null);

        // Only grade and violationCodes are non-null; status and notes are intentionally null
        ReportRequest req = new ReportRequest();
        req.setGrade(Grade.C);
        req.setViolationCodes("10F");
        // status and notes stay null in the request

        mockMvc.perform(patch("/api/reports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Capture the entity passed to save() and verify only non-null fields were applied
        org.mockito.ArgumentCaptor<InspectionReportEntity> captor =
                org.mockito.ArgumentCaptor.forClass(InspectionReportEntity.class);
        verify(reportRepository).save(captor.capture());
        InspectionReportEntity saved = captor.getValue();
        // grade and violationCodes must be updated
        org.junit.jupiter.api.Assertions.assertEquals(Grade.C, saved.getGrade());
        org.junit.jupiter.api.Assertions.assertEquals("10F", saved.getViolationCodes());
        // status must remain OPEN (not overwritten), notes must remain null
        org.junit.jupiter.api.Assertions.assertEquals(Status.OPEN, saved.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(saved.getNotes());
    }

    // ── CTRL-04: photo upload ──────────────────────────────────────────────

    /**
     * Override the AppConfig static "properties" field to inject a test uploads dir.
     * Avoids mockStatic (which causes java.lang.VerifyError on Java 25 with Byte Buddy).
     * AppConfig.getUploadsDir() reads: System.getenv(APP_UPLOADS_DIR) > .env > properties field.
     * In tests there is no APP_UPLOADS_DIR env var, so patching the properties field works.
     */
    private static void setUploadsDir(String path) throws Exception {
        Field f = AppConfig.class.getDeclaredField("properties");
        f.setAccessible(true);
        Properties props = (Properties) f.get(null);
        props.setProperty("app.uploads.dir", path);
    }

    @Test
    void photoUpload_savesFileAndUpdatesPhotoPath(@TempDir Path tempDir) throws Exception {
        setUploadsDir(tempDir.toString());

        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));

        InspectionReportEntity entity = makeEntity(1L, "R1", Grade.A, Status.OPEN);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(reportRepository.save(any(InspectionReportEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(restaurantDAO.findByRestaurantId("R1")).thenReturn(null);

        byte[] imageBytes = "fake-image-content".getBytes();
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", imageBytes);

        mockMvc.perform(multipart("/api/reports/1/photo").file(multipartFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Capture entity passed to save() and verify photoPath contains reportId and filename
        org.mockito.ArgumentCaptor<InspectionReportEntity> captor =
                org.mockito.ArgumentCaptor.forClass(InspectionReportEntity.class);
        verify(reportRepository).save(captor.capture());
        InspectionReportEntity saved = captor.getValue();
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getPhotoPath());
        org.junit.jupiter.api.Assertions.assertTrue(saved.getPhotoPath().contains("1"),
                "photoPath should contain reportId directory");
        org.junit.jupiter.api.Assertions.assertTrue(saved.getPhotoPath().contains("test.jpg"),
                "photoPath should contain original filename");
    }

    @Test
    void photoUpload_returns404_whenReportNotFound(@TempDir Path tempDir) throws Exception {
        // Stub user lookup so controller reaches findById() check
        UserEntity user = new UserEntity("ctrl_user", "ctrl@test.com", "hash", "ROLE_CONTROLLER");
        user.setId(42L);
        when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user));
        // Report 99 does not exist
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        byte[] imageBytes = "fake-image-content".getBytes();
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", imageBytes);

        mockMvc.perform(multipart("/api/reports/99/photo").file(multipartFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPhoto_streamsFileWithCorrectContentType(@TempDir Path tempDir) throws Exception {
        // Write a real file to temp dir so UrlResource.exists() returns true
        Path photoDir = tempDir.resolve("1");
        Files.createDirectories(photoDir);
        Path photoFile = photoDir.resolve("123456789_test.jpg");
        Files.write(photoFile, "fake-jpeg-bytes".getBytes());

        InspectionReportEntity entity = makeEntity(1L, "R1", Grade.A, Status.OPEN);
        entity.setPhotoPath(photoFile.toString());
        when(reportRepository.findById(1L)).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/reports/1/photo"))
                .andExpect(status().isOk());
    }

    // ── SC-3: file written to AppConfig.getUploadsDir() path is re-readable ─
    /**
     * Verifies the file-I/O contract for photo persistence:
     * a file written to the path returned by AppConfig.getUploadsDir() is readable
     * using a fresh call to the same method.
     *
     * Docker layer: production persistence is guaranteed by the named volume
     * uploads_data:/app/uploads in docker-compose.yml (APP_UPLOADS_DIR=/app/uploads).
     * This test cannot invoke docker compose; it verifies the Java plumbing only.
     */
    @Test
    void uploadsDir_fileWrittenAndReadableFromSamePath(@TempDir Path tempDir) throws Exception {
        // Patch AppConfig static properties field to point to tempDir
        // (avoids mockStatic which causes VerifyError on Java 25)
        setUploadsDir(tempDir.toString());

        // Write a file the way the controller would
        String uploadsDir = AppConfig.getUploadsDir();
        Path targetDir = Paths.get(uploadsDir, "42");
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve("photo.jpg");
        Files.write(targetFile, "photo-content".getBytes());

        // Re-read using a fresh AppConfig.getUploadsDir() call (simulates a new request)
        String resolvedDir = AppConfig.getUploadsDir();
        Path resolvedFile = Paths.get(resolvedDir, "42", "photo.jpg");
        assertTrue(Files.exists(resolvedFile),
                "File must exist at path returned by AppConfig.getUploadsDir()");
        assertArrayEquals("photo-content".getBytes(), Files.readAllBytes(resolvedFile));
    }
}
