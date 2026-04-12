package com.aflokkat.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wave 0 test scaffold for STAT-04 public page accessibility.
 * Test is @Disabled until ViewController.analytics() is added in Plan 06-03.
 */
@ExtendWith(MockitoExtension.class)
public class ViewControllerAnalyticsTest {

    @InjectMocks
    private ViewController viewController;

    /**
     * STAT-04: GET /analytics returns the "analytics" view (fully public — no auth required).
     */
    @Test
    public void testAnalyticsPage_returns200() {
        assertEquals("analytics", viewController.analytics());
    }
}
