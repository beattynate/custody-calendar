package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.AuditLogEntry;
import com.custodycalendar.api.domain.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Immutable case-scoped change history. Entries are written from the same
 * transaction as the change they describe, so the log and the data cannot
 * disagree.
 */
@Service
public class AuditLogService {

    private static final int MAX_ENTRIES = 300;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID caseId, UUID actorPersonId, String action, String entityType, UUID entityId, Map<String, Object> details) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setId(UUID.randomUUID());
        entry.setCaseId(caseId);
        entry.setActorPersonId(actorPersonId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(writeDetails(details));
        entry.setCreatedAt(OffsetDateTime.now());
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> listForCase(UUID caseId) {
        return auditLogRepository.findByCaseIdOrderByCreatedAtDesc(caseId, Pageable.ofSize(MAX_ENTRIES));
    }

    private String writeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
