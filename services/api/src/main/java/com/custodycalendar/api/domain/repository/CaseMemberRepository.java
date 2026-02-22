package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.CaseMember;
import com.custodycalendar.api.domain.model.CaseMemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseMemberRepository extends JpaRepository<CaseMember, CaseMemberId> {
    boolean existsByIdCaseIdAndIdPersonId(UUID caseId, UUID personId);
    List<CaseMember> findAllByIdCaseId(UUID caseId);
}
