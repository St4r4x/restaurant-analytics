package com.st4r4x.controller;

import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.BookmarkRepository;
import com.st4r4x.repository.ReportRepository;
import com.st4r4x.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserControllerMeTest {

    @InjectMocks
    private UserController userController;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private ReportRepository reportRepository;

    @BeforeEach
    public void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("testuser", null, Collections.emptyList())
        );
    }

    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void getProfile_includesBookmarkCount() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(bookmarkRepository.countByUserId(1L)).thenReturn(5L);

        ResponseEntity<Map<String, Object>> response = userController.getProfile();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertNotNull(data);
        assertEquals(5L, data.get("bookmarkCount"));
    }

    @Test
    public void getProfile_reportCountIsNull_forCustomer() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(bookmarkRepository.countByUserId(1L)).thenReturn(2L);

        ResponseEntity<Map<String, Object>> response = userController.getProfile();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertNotNull(data);
        assertNull(data.get("reportCount"));
    }

    @Test
    public void getProfile_includesReportCount_forController() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CONTROLLER");
        user.setId(2L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(bookmarkRepository.countByUserId(2L)).thenReturn(2L);
        when(reportRepository.countByUserId(2L)).thenReturn(7L);

        ResponseEntity<Map<String, Object>> response = userController.getProfile();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertNotNull(data);
        assertEquals(7L, data.get("reportCount"));
    }
}
