package com.aflokkat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aflokkat.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validation tests for AuthController @RequestBody DTOs.
 * Uses standaloneSetup — avoids @WebMvcTest (StackOverflowError on Java 25).
 * Tests are RED until Plan 03 adds @Valid + spring-boot-starter-validation + GlobalExceptionHandler.
 */
class AuthControllerValidationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthService authService = mock(AuthService.class);
        AuthController authController = new AuthController();
        // Inject mock via reflection (field is @Autowired)
        try {
            java.lang.reflect.Field f = AuthController.class.getDeclaredField("authService");
            f.setAccessible(true);
            f.set(authController, authService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_emptyPassword_returns400() throws Exception {
        String body = "{\"username\":\"user\",\"password\":\"\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void login_emptyUsername_returns400() throws Exception {
        String body = "{\"username\":\"\",\"password\":\"pass\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void register_emptyEmail_returns400() throws Exception {
        String body = "{\"username\":\"u\",\"email\":\"\",\"password\":\"p\",\"signupCode\":\"\"}";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        String body = "{\"username\":\"u\",\"email\":\"not-an-email\",\"password\":\"p\",\"signupCode\":\"\"}";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_emptyToken_returns400() throws Exception {
        String body = "{\"refreshToken\":\"\"}";
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }
}
