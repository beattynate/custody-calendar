package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.ScheduleProposalApproval;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleProposalApprovalRepository extends JpaRepository<ScheduleProposalApproval, UUID> {
    List<ScheduleProposalApproval> findByProposalIdOrderByPersonIdAsc(UUID proposalId);
    Optional<ScheduleProposalApproval> findByProposalIdAndPersonId(UUID proposalId, UUID personId);
}
