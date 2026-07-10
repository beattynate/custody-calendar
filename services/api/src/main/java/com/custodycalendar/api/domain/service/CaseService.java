package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.CustodyCase;
import com.custodycalendar.api.domain.model.MemberRole;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.CaseMember;
import com.custodycalendar.api.domain.model.CaseMemberId;
import com.custodycalendar.api.domain.repository.CaseMemberRepository;
import com.custodycalendar.api.domain.repository.CustodyCaseRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CaseService {

    private final CustodyCaseRepository custodyCaseRepository;
    private final CaseMemberRepository caseMemberRepository;
    private final PersonDirectoryService personDirectoryService;

    public CaseService(
            CustodyCaseRepository custodyCaseRepository,
            CaseMemberRepository caseMemberRepository,
            PersonDirectoryService personDirectoryService) {
        this.custodyCaseRepository = custodyCaseRepository;
        this.caseMemberRepository = caseMemberRepository;
        this.personDirectoryService = personDirectoryService;
    }

    @Transactional
    public CustodyCase createCase(String name, String timezone, String creatorSubject, String creatorDisplayName) {
        validateTimezone(timezone);

        CustodyCase custodyCase = new CustodyCase();
        custodyCase.setId(UUID.randomUUID());
        custodyCase.setName(name);
        custodyCase.setTimezone(timezone);
        CustodyCase savedCase = custodyCaseRepository.save(custodyCase);

        Person savedPerson = personDirectoryService.ensurePerson(
                creatorSubject,
                (creatorDisplayName == null || creatorDisplayName.isBlank()) ? creatorSubject : creatorDisplayName);

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

    public List<CustodyCase> listCasesForSubject(String externalSubject) {
        Optional<Person> person = personDirectoryService.findBySubject(externalSubject);
        if (person.isEmpty()) {
            return List.of();
        }

        List<CaseMember> memberships = caseMemberRepository.findAllByIdPersonId(person.get().getId());
        if (memberships.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> caseIds = new LinkedHashSet<>();
        for (CaseMember membership : memberships) {
            if (membership.getId() != null && membership.getId().getCaseId() != null) {
                caseIds.add(membership.getId().getCaseId());
            }
        }
        if (caseIds.isEmpty()) {
            return List.of();
        }

        List<CustodyCase> cases = new ArrayList<>(custodyCaseRepository.findAllByIdIn(caseIds));
        cases.sort(Comparator.comparing(CustodyCase::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CustodyCase::getId));
        return cases;
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
