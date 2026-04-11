package com.st4r4x.config;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.servlet.Filter;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security rules test using standalone MockMvc backed by a minimal Spring Security context.
 *
 * Avoids @WebMvcTest to work around a Spring Boot 2.6 + Java 25 JVM crash caused by
 * Mockito's dynamic byte-buddy-agent attachment in the @WebMvcTest test execution lifecycle.
 *
 * The AnnotationConfigWebApplicationContext is bootstrapped with SecurityAutoConfiguration
 * (which provides HttpSecurity) + SecurityConfig (which defines the filter chain rules).
 * MockMvc uses springSecurity() to apply the resulting FilterChainProxy.
 *
 * Test names match the plan spec exactly:
 *   - reports_returns401_whenUnauthenticated
 *   - reports_returns403_forCustomerJwt
 *   - reports_allowsAccess_forControllerJwt
 */
public class SecurityConfigTest {

    private MockMvc mockMvc;

    /**
     * Minimal stub controller so MockMvc has a real endpoint to route to.
     * Returns 200 if security passes.
     */
    @RestController
    public static class StubReportsController {
        @GetMapping("/api/reports/test")
        public String test() {
            return "ok";
        }

        @GetMapping("/api/reports/stats")
        public String stats() {
            return "ok";
        }

        @GetMapping("/dashboard")
        public String dashboard() {
            return "ok";
        }

        @GetMapping("/admin")
        public String admin() {
            return "ok";
        }
    }

    @Before
    public void setUp() throws Exception {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        // SecurityAutoConfiguration registers HttpSecurity and the SpringSecurityFilterChain infrastructure.
        // SecurityConfig provides our custom SecurityFilterChain (antMatchers, accessDeniedHandler, etc.).
        // SecurityConfig must be registered BEFORE SecurityAutoConfiguration so that
        // SpringBootWebSecurityConfiguration's @ConditionalOnDefaultWebSecurity sees our
        // SecurityFilterChain bean and skips creating its own default chain.
        context.register(
                SecurityConfig.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        );
        context.refresh();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubReportsController())
                .apply(springSecurity((Filter) context.getBean("springSecurityFilterChain")))
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
        // Simulate a valid CUSTOMER token already parsed by the JWT filter
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        mockMvc.perform(get("/api/reports/test")
                        .with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void reports_allowsAccess_forControllerJwt() throws Exception {
        // Simulate a valid CONTROLLER token
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "ctrl_user", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER")));
        mockMvc.perform(get("/api/reports/test")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }

    // Phase 7 decision: /dashboard uses client-side IIFE auth guard only (not server-side antMatcher).
    // The server allows any request to /dashboard via anyRequest().permitAll() — the IIFE in
    // dashboard.html redirects non-CONTROLLER users to / on the client side.

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

    // /admin is NOT protected server-side: JWT lives in localStorage, browsers never send
    // Authorization headers on page navigation. Security is enforced by the client-side
    // IIFE guard in admin.html. anyRequest().permitAll() applies, so all callers get 200.
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

    // /api/reports/stats is ADMIN-only (antMatcher before /api/reports/** wildcard)
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
}
