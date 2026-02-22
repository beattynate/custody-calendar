package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.repository.CaseMemberRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CaseMembershipService {

    private final CaseMemberRepository caseMemberRepository;
    private final PersonRepository personRepository;

    public CaseMembershipService(CaseMemberRepository caseMemberRepository, PersonRepository personRepository) {
        this.caseMemberRepository = caseMemberRepository;
        this.personRepository = personRepository;
    }

    public boolean isCaseMember(UUID caseId, UUID personId) {
        return caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, personId);
    }

    public boolean isCaseMemberByExternalSubject(UUID caseId, String externalSubject) {
        return personRepository.findByExternalSubject(externalSubject)
                .map(person -> caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, person.getId()))
                .orElse(false);
    }
}
