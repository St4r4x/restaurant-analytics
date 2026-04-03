package com.aflokkat.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ViewControllerDashboardTest {

    @InjectMocks
    private ViewController viewController;

    @Test
    public void index_redirectsToDashboard_forController() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "ctrl_user", null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
        assertEquals("redirect:/dashboard", viewController.index(auth));
    }

    @Test
    public void index_returnsIndex_forAnonymous() {
        assertEquals("index", viewController.index(null));
    }

    @Test
    public void index_returnsIndex_forCustomer() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "customer_user", null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        assertEquals("index", viewController.index(auth));
    }
}
