package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.repository.CaseMemberRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CaseMembershipService {

    private final CaseMemberRepository caseMemberRepository;
    private final PersonDirectoryService personDirectoryService;

    public CaseMembershipService(CaseMemberRepository caseMemberRepository, PersonDirectoryService personDirectoryService) {
        this.caseMemberRepository = caseMemberRepository;
        this.personDirectoryService = personDirectoryService;
    }

    public boolean isCaseMember(UUID caseId, UUID personId) {
        return caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, personId);
    }

    public boolean isCaseMemberByExternalSubject(UUID caseId, String externalSubject) {
        return personDirectoryService.findBySubject(externalSubject)
                .map(person -> caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, person.getId()))
                .orElse(false);
    }
}
