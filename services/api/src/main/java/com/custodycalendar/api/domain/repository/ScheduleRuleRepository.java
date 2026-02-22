package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.ScheduleRule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRuleRepository extends JpaRepository<ScheduleRule, UUID> {
    Optional<ScheduleRule> findByCaseId(UUID caseId);
}
