package com.aflokkat.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wave 0 test scaffold for DISC-02 public page accessibility.
 * Tests that ViewController.uncontrolled() returns the "uncontrolled" view name.
 */
@ExtendWith(MockitoExtension.class)
public class ViewControllerUncontrolledTest {

    @InjectMocks
    private ViewController viewController;

    /**
     * DISC-02: GET /uncontrolled returns the "uncontrolled" view (fully public — no auth required).
     */
    @Test
    public void testUncontrolledPage_returnsView() {
        assertEquals("uncontrolled", viewController.uncontrolled());
    }
}
