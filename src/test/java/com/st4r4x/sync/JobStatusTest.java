package com.st4r4x.sync;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class JobStatusTest {

    @Test
    void success_stores_all_fields() {
        Instant now = Instant.now();
        JobStatus s = JobStatus.success(now, 1234L);
        assertEquals(now, s.getLastRunAt());
        assertEquals(1234L, s.getDurationMs());
        assertTrue(s.isSuccess());
        assertNull(s.getErrorMessage());
    }

    @Test
    void failure_stores_error_message() {
        Instant now = Instant.now();
        JobStatus s = JobStatus.failure(now, 500L, "something went wrong");
        assertFalse(s.isSuccess());
        assertEquals("something went wrong", s.getErrorMessage());
    }
}
