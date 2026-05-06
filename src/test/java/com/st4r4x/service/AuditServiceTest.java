package com.st4r4x.service;

import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditService auditService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(String username, String role) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        when(auth.getAuthorities()).thenReturn(
            (Collection) List.of(new SimpleGrantedAuthority(role)));
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    void log_persistsEntryWithActorFromSecurityContext() {
        setupSecurityContext("admin-user", "ROLE_ADMIN");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(AuditAction.USER_ROLE_CHANGED, "User", "7",
            Map.of("oldRole", "ROLE_CUSTOMER", "newRole", "ROLE_CONTROLLER"));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntity saved = captor.getValue();

        assertThat(saved.getActorUsername()).isEqualTo("admin-user");
        assertThat(saved.getActorRole()).isEqualTo("ROLE_ADMIN");
        assertThat(saved.getAction()).isEqualTo(AuditAction.USER_ROLE_CHANGED);
        assertThat(saved.getTargetType()).isEqualTo("User");
        assertThat(saved.getTargetId()).isEqualTo("7");
        assertThat(saved.getDetail()).contains("ROLE_CONTROLLER");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void log_handlesNullDetailGracefully() {
        setupSecurityContext("admin-user", "ROLE_ADMIN");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(AuditAction.CACHE_REBUILT, null, null, null);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }
}
