package com.st4r4x.controller;

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

    private static Authentication withRole(String role) {
        return new UsernamePasswordAuthenticationToken(
            "user", null, Collections.singletonList(new SimpleGrantedAuthority(role)));
    }

    // "/" is the Search page for everyone: it used to send signed-in users to their dashboard,
    // so the navbar's Search link opened the bookmarks dashboard instead of the search
    @Test
    public void index_returnsLanding_forAnonymous() {
        assertEquals("landing", viewController.index());
    }

    @Test
    public void dashboard_returnsInspectorDashboard_forController() {
        assertEquals("dashboard", viewController.dashboard(withRole("ROLE_CONTROLLER")));
    }

    @Test
    public void dashboard_returnsCustomerDashboard_forCustomer() {
        assertEquals("index", viewController.dashboard(withRole("ROLE_CUSTOMER")));
    }

    @Test
    public void dashboard_returnsCustomerDashboard_forAdmin() {
        assertEquals("index", viewController.dashboard(withRole("ROLE_ADMIN")));
    }

    @Test
    public void dashboard_letsThePageCheckTheRole_whenAccessTokenIsMissing() {
        // an expired access token reaches the server as anonymous: dashboard.html refreshes the
        // session and resolves the role client-side
        assertEquals("dashboard", viewController.dashboard(null));
    }
}
