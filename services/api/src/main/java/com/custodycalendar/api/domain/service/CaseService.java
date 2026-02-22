package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.CustodyCase;
import com.custodycalendar.api.domain.model.MemberRole;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.CaseMember;
import com.custodycalendar.api.domain.model.CaseMemberId;
import com.custodycalendar.api.domain.repository.CaseMemberRepository;
import com.custodycalendar.api.domain.repository.CustodyCaseRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CaseService {

    private final CustodyCaseRepository custodyCaseRepository;
    private final PersonRepository personRepository;
    private final CaseMemberRepository caseMemberRepository;

    public CaseService(
            CustodyCaseRepository custodyCaseRepository,
            PersonRepository personRepository,
            CaseMemberRepository caseMemberRepository) {
        this.custodyCaseRepository = custodyCaseRepository;
        this.personRepository = personRepository;
        this.caseMemberRepository = caseMemberRepository;
    }

    @Transactional
    public CustodyCase createCase(String name, String timezone, String creatorSubject, String creatorDisplayName) {
        validateTimezone(timezone);

        CustodyCase custodyCase = new CustodyCase();
        custodyCase.setId(UUID.randomUUID());
        custodyCase.setName(name);
        custodyCase.setTimezone(timezone);
        CustodyCase savedCase = custodyCaseRepository.save(custodyCase);

        Person person = personRepository.findByExternalSubject(creatorSubject)
                .orElseGet(() -> {
                    Person created = new Person();
                    created.setId(UUID.randomUUID());
                    created.setExternalSubject(creatorSubject);
                    return created;
                });
        person.setDisplayName((creatorDisplayName == null || creatorDisplayName.isBlank()) ? creatorSubject : creatorDisplayName);
        Person savedPerson = personRepository.save(person);

        CaseMemberId memberId = new CaseMemberId();
        memberId.setCaseId(savedCase.getId());
        memberId.setPersonId(savedPerson.getId());
        if (!caseMemberRepository.existsById(memberId)) {
            CaseMember member = new CaseMember();
            member.setId(memberId);
            member.setRole(MemberRole.ADMIN);
            caseMemberRepository.save(member);
        }

        return savedCase;
    }

    public Optional<CustodyCase> getCase(UUID caseId) {
        return custodyCaseRepository.findById(caseId);
    }

    public CustodyCase requireCase(UUID caseId) {
        return getCase(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timezone");
        }
    }
}
