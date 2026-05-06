package com.st4r4x.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void log(AuditAction action, String targetType, String targetId, Map<String, Object> detail) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String actorUsername = (auth != null) ? auth.getName() : "anonymous";
            String actorRole = resolveRole(auth);

            AuditLogEntity entry = new AuditLogEntity();
            entry.setActorUsername(actorUsername);
            entry.setActorRole(actorRole);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail != null ? objectMapper.writeValueAsString(detail) : null);
            entry.setCreatedAt(new Date());

            auditLogRepository.save(entry);
        } catch (JsonProcessingException e) {
            logger.warn("AuditService: failed to serialize detail for action {}: {}", action, e.getMessage());
        } catch (Exception e) {
            logger.warn("AuditService: failed to persist audit entry for action {}: {}", action, e.getMessage());
        }
    }

    private String resolveRole(Authentication auth) {
        if (auth == null) return "anonymous";
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities == null || authorities.isEmpty()) return "unknown";
        return authorities.iterator().next().getAuthority();
    }
}
