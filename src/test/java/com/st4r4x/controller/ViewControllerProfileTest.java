package com.st4r4x.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ViewControllerProfileTest {

    @InjectMocks
    private ViewController viewController;

    @Test
    public void profile_returnsProfileView() {
        assertEquals("profile", viewController.profile());
    }
}
