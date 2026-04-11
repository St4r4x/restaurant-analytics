package com.aflokkat.config;

import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.aflokkat.security.JwtAuthenticationFilter;
import com.aflokkat.security.JwtUtil;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
        return new JwtAuthenticationFilter(jwtUtil);
    }

    /**
     * Prevent Spring Boot from auto-registering JwtAuthenticationFilter as a standalone
     * servlet filter. It is already registered in the Spring Security filter chain via
     * addFilterBefore(). Without this, the filter runs twice per request and causes a
     * StackOverflowError in MockMvc slice tests (and double execution in production).
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                // Public: auth endpoints, read-only NYC data, Swagger
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/restaurants/**").permitAll()
                .antMatchers("/api/inspection/**").permitAll()
                .antMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/v3/api-docs/**",
                    "/webjars/**"
                ).permitAll()
                // Admin-only endpoints (MUST be before /api/reports/** wildcard)
                .antMatchers("/api/reports/stats").hasRole("ADMIN")
                // Controller-only endpoints
                .antMatchers("/api/reports/**").hasRole("CONTROLLER")
                // Any authenticated user (any role)
                .antMatchers("/api/users/**").authenticated()
                // Admin view route: protected by client-side IIFE guard in admin.html
                // (JWT lives in localStorage, not cookies → browser navigation has no Auth header)
                // Non-API view routes: open for now (Phase 3 scope)
                .anyRequest().permitAll()
            .and()
            .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) -> {
                    // API calls get 401 JSON; browser navigation gets redirected to /login
                    String path = request.getRequestURI();
                    if (path.startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized\"}");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
                })
            .and()
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
