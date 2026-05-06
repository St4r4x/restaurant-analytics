package com.st4r4x.controller;

import com.st4r4x.sync.CronScheduler;
import com.st4r4x.sync.JobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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
}
