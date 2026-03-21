package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.ScheduleProposal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleProposalRepository extends JpaRepository<ScheduleProposal, UUID> {
    List<ScheduleProposal> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
    Optional<ScheduleProposal> findByIdAndCaseId(UUID id, UUID caseId);
}
