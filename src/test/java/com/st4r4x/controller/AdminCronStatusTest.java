package com.st4r4x.controller;

import com.st4r4x.sync.CronScheduler;
import com.st4r4x.sync.JobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCronStatusTest {

    @Mock CronScheduler cronScheduler;
    @InjectMocks AdminController adminController;

    @Test
    void getCronStatus_returns_registry_map() {
        Instant now = Instant.now();
        Map<String, JobStatus> registry = Map.of(
                "cache-warmup", JobStatus.success(now, 300L),
                "es-reindex",   JobStatus.failure(now, 100L, "es down")
        );
        when(cronScheduler.getStatus()).thenReturn(registry);

        ResponseEntity<Map<String, Object>> response = adminController.getCronStatus();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("success", body.get("status"));
        assertSame(registry, body.get("jobs"));
    }

    @Test
    void getCronStatus_hasPreAuthorizeAdminRole() throws NoSuchMethodException {
        Method method = AdminController.class.getMethod("getCronStatus");
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "@PreAuthorize must be present on getCronStatus()");
        assertEquals("hasRole('ADMIN')", annotation.value());
    }

    @Test
    void runCronJob_known_sync_job_returns_200() {
        when(cronScheduler.runJob("cache-warmup")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = adminController.runCronJob("cache-warmup");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("success", body.get("status"));
        assertEquals("cache-warmup", body.get("job"));
    }

    @Test
    void runCronJob_known_async_job_returns_202() {
        when(cronScheduler.runJob("es-reindex")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = adminController.runCronJob("es-reindex");

        assertEquals(202, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("accepted", body.get("status"));
        assertEquals("es-reindex", body.get("job"));
    }

    @Test
    void runCronJob_unknown_key_returns_400() {
        when(cronScheduler.runJob("unknown")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = adminController.runCronJob("unknown");

        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertTrue(body.get("message").toString().contains("unknown"));
    }

    @Test
    void runCronJob_hasPreAuthorizeAdminRole() throws NoSuchMethodException {
        Method method = AdminController.class.getMethod("runCronJob", String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "@PreAuthorize must be present on runCronJob()");
        assertEquals("hasRole('ADMIN')", annotation.value());
    }
}
