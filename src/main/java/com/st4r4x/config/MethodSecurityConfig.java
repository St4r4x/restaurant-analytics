package com.st4r4x.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables Spring Security method-level security annotations (@PreAuthorize, @PostAuthorize, etc.).
 * Kept in a separate @Configuration class so that @WebMvcTest slice tests can import only
 * SecurityConfig without triggering the AOP proxying that @EnableMethodSecurity introduces,
 * which causes StackOverflowError in MockMvc environments.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
