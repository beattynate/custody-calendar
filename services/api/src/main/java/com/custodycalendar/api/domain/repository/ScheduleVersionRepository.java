package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.ScheduleVersion;
import com.custodycalendar.api.domain.model.ScheduleVersionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleVersionRepository extends JpaRepository<ScheduleVersion, UUID> {
    Optional<ScheduleVersion> findFirstByCaseIdAndStatusOrderByCreatedAtDesc(UUID caseId, ScheduleVersionStatus status);
}
