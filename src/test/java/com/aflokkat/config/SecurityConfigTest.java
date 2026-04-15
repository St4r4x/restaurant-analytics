package com.aflokkat.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Collections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security rules test using standalone MockMvc backed by a minimal context.
 *
 * Uses webAppContextSetup() + springSecurity() instead of the old
 * AnnotationConfigWebApplicationContext + SecurityAutoConfiguration approach,
 * which no longer works in Spring Boot 4.0 (SecurityAutoConfiguration moved/removed).
 *
 * TestWebConfig provides @EnableWebMvc + @EnableWebSecurity infrastructure.
 * SecurityConfig supplies the SecurityFilterChain rules under test.
 * JWT system properties are set in @BeforeAll so JwtUtil initializes correctly.
 */
public class SecurityConfigTest {

    private MockMvc mockMvc;

    @RestController
    public static class StubReportsController {
        @GetMapping("/api/reports/test")
        public String test() { return "ok"; }

        @GetMapping("/api/reports/stats")
        public String stats() { return "ok"; }

        @GetMapping("/dashboard")
        public String dashboard() { return "ok"; }

        @GetMapping("/admin")
        public String admin() { return "ok"; }

        @GetMapping("/api/restaurants/by-borough")
        public String byBorough() { return "ok"; }

        @GetMapping("/api/restaurants/health")
        public String health() { return "ok"; }
    }

    /**
     * Provides @EnableWebMvc (DispatcherServlet infrastructure) and
     * @EnableWebSecurity (HttpSecurity / WebSecurityConfiguration beans)
     * without requiring Spring Boot's autoconfigure module.
     */
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestWebConfig {
        @Bean
        public StubReportsController stubReportsController() {
            return new StubReportsController();
        }
    }

    @BeforeAll
    static void setJwtSystemProperties() {
        // JwtUtil reads these via AppConfig.getProperty() which checks System.getProperty() first.
        // Must be set before the Spring context is created (context.refresh() instantiates JwtUtil).
        System.setProperty("jwt.secret", "a_very_long_32_bytes_minimum_secret_with_extra_chars_123456");
        System.setProperty("jwt.access.expiration.ms", "900000");
        System.setProperty("jwt.refresh.expiration.ms", "604800000");
    }

    @BeforeEach
    void setUp() throws Exception {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(TestWebConfig.class, SecurityConfig.class);
        context.refresh();

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    public void reports_returns401_whenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/reports/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void reports_returns403_forCustomerJwt() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        mockMvc.perform(get("/api/reports/test")
                        .with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void reports_allowsAccess_forControllerJwt() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "ctrl_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
        mockMvc.perform(get("/api/reports/test")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }

    // Phase 7 decision: /dashboard uses client-side IIFE auth guard only (not server-side requestMatcher).
    @Test
    public void dashboard_isAccessible_whenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    public void dashboard_isAccessible_forCustomer() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        mockMvc.perform(get("/dashboard")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }

    @Test
    public void dashboard_returns200_forController() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "ctrl_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
        mockMvc.perform(get("/dashboard")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }

    @Test
    public void admin_returns200_whenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk());
    }

    @Test
    public void admin_returns200_forController() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "ctrl_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
        mockMvc.perform(get("/admin")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }

    @Test
    public void admin_returns200_forAdmin() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mockMvc.perform(get("/admin")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }

    @Test
    public void reportStats_returns403_forController() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "ctrl_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
        mockMvc.perform(get("/api/reports/stats")
                        .with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void reportStats_returns200_forAdmin() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mockMvc.perform(get("/api/reports/stats")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }

    // --- Phase 16 security hardening tests ---

    @Test
    public void cors_unlistedOrigin_returns403() throws Exception {
        mockMvc.perform(
                options("/api/restaurants/by-borough")
                    .header("Origin", "http://evil.example.com")
                    .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void responseHeaders_containXContentTypeOptions() throws Exception {
        mockMvc.perform(get("/api/restaurants/health"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    public void responseHeaders_containXFrameOptions() throws Exception {
        mockMvc.perform(get("/api/restaurants/health"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
