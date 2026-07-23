package com.st4r4x.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.entity.BookmarkEntity;
import com.st4r4x.entity.InspectionReportEntity;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.AuditLogRepository;
import com.st4r4x.repository.BookmarkRepository;
import com.st4r4x.repository.ReportRepository;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.service.AuditService;

@ExtendWith(MockitoExtension.class)
public class UserControllerDeleteMeTest {

    @InjectMocks
    private UserController userController;

    @Mock private UserRepository userRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditService auditService;

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
    public void deleteAccount_withNoBookmarksOrReports_deletesUserAndReturns200() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(bookmarkRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(reportRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(auditLogRepository.findByActorUsername("testuser")).thenReturn(Collections.emptyList());

        ResponseEntity<?> response = userController.deleteAccount();

        assertEquals(200, response.getStatusCode().value());
        verify(userRepository).delete(user);
    }

    @Test
    public void deleteAccount_logsAuditEventBeforeDeletingUser() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(bookmarkRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(reportRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(auditLogRepository.findByActorUsername("testuser")).thenReturn(Collections.emptyList());

        userController.deleteAccount();

        verify(auditService).log(eq(AuditAction.USER_DELETED), eq("User"), eq("testuser"), isNull());
    }

    @Test
    public void deleteAccount_deletesAllBookmarksAndReports() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        BookmarkEntity bookmark = new BookmarkEntity(user, "R001");
        InspectionReportEntity report = new InspectionReportEntity();
        report.setId(42L);
        List<BookmarkEntity> bookmarks = List.of(bookmark);
        List<InspectionReportEntity> reports = List.of(report);

        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(bookmarkRepository.findByUserId(1L)).thenReturn(bookmarks);
        when(reportRepository.findByUserId(1L)).thenReturn(reports);
        when(auditLogRepository.findByActorUsername("testuser")).thenReturn(Collections.emptyList());

        userController.deleteAccount();

        verify(bookmarkRepository).deleteAll(bookmarks);
        verify(reportRepository).deleteAll(reports);
    }

    @Test
    public void deleteAccount_anonymizesMatchingAuditLogRows() {
        UserEntity user = new UserEntity("testuser", "test@example.com", "hash", "ROLE_CUSTOMER");
        user.setId(1L);
        AuditLogEntity priorEntry = new AuditLogEntity();
        priorEntry.setActorUsername("testuser");
        List<AuditLogEntity> priorEntries = List.of(priorEntry);

        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(bookmarkRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(reportRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(auditLogRepository.findByActorUsername("testuser")).thenReturn(priorEntries);

        userController.deleteAccount();

        ArgumentCaptor<List<AuditLogEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditLogRepository).saveAll(captor.capture());
        assertEquals("[deleted-user]", captor.getValue().get(0).getActorUsername());
        verify(auditLogRepository, never()).deleteAll(any());
        verify(auditLogRepository, never()).delete(any());
    }
}
