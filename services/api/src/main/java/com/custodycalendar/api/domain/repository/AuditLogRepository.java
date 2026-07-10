package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.AuditLogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {
    List<AuditLogEntry> findByCaseIdOrderByCreatedAtDesc(UUID caseId, Pageable pageable);
}
