package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.CaseMember;
import com.custodycalendar.api.domain.model.CaseMemberId;
import com.custodycalendar.api.domain.model.Child;
import com.custodycalendar.api.domain.model.Event;
import com.custodycalendar.api.domain.model.MemberRole;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.model.ScheduleRuleType;
import com.custodycalendar.api.domain.repository.CaseMemberRepository;
import com.custodycalendar.api.domain.repository.ChildRepository;
import com.custodycalendar.api.domain.repository.EventRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import com.custodycalendar.api.domain.repository.ScheduleRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaseResourceService {

    private final CaseService caseService;
    private final PersonRepository personRepository;
    private final PersonDirectoryService personDirectoryService;
    private final CaseMemberRepository caseMemberRepository;
    private final ChildRepository childRepository;
    private final ScheduleRuleRepository scheduleRuleRepository;
    private final EventRepository eventRepository;

    public CaseResourceService(
            CaseService caseService,
            PersonRepository personRepository,
            PersonDirectoryService personDirectoryService,
            CaseMemberRepository caseMemberRepository,
            ChildRepository childRepository,
            ScheduleRuleRepository scheduleRuleRepository,
            EventRepository eventRepository) {
        this.caseService = caseService;
        this.personRepository = personRepository;
        this.personDirectoryService = personDirectoryService;
        this.caseMemberRepository = caseMemberRepository;
        this.childRepository = childRepository;
        this.scheduleRuleRepository = scheduleRuleRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<CaseMemberView> listMembers(UUID caseId) {
        caseService.requireCase(caseId);
        List<CaseMember> members = caseMemberRepository.findAllByIdCaseId(caseId);
        Map<UUID, Person> peopleById = personRepository.findAllById(
                        members.stream().map(m -> m.getId().getPersonId()).toList())
                .stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));

        List<CaseMemberView> views = new ArrayList<>();
        for (CaseMember member : members) {
            Person person = peopleById.get(member.getId().getPersonId());
            if (person != null) {
                views.add(new CaseMemberView(person.getId(), person.getExternalSubject(), person.getDisplayName(), member.getRole()));
            }
        }
        views.sort(Comparator.comparing(CaseMemberView::displayName, String.CASE_INSENSITIVE_ORDER));
        return views;
    }

    @Transactional
    public CaseMemberView addMember(UUID caseId, String externalSubject, String displayName, MemberRole role) {
        caseService.requireCase(caseId);

        Person person = personDirectoryService.ensurePerson(externalSubject, displayName);

        CaseMemberId memberId = new CaseMemberId();
        memberId.setCaseId(caseId);
        memberId.setPersonId(person.getId());

        CaseMember member = caseMemberRepository.findById(memberId).orElseGet(() -> {
            CaseMember created = new CaseMember();
            created.setId(memberId);
            return created;
        });
        member.setRole(role);
        caseMemberRepository.save(member);

        return new CaseMemberView(person.getId(), person.getExternalSubject(), person.getDisplayName(), member.getRole());
    }

    @Transactional(readOnly = true)
    public List<Child> listChildren(UUID caseId) {
        caseService.requireCase(caseId);
        return childRepository.findAllByCaseIdOrderByNameAsc(caseId);
    }

    @Transactional
    public Child addChild(UUID caseId, String name) {
        caseService.requireCase(caseId);
        Child child = new Child();
        child.setId(UUID.randomUUID());
        child.setCaseId(caseId);
        child.setName(name);
        return childRepository.save(child);
    }

    @Transactional(readOnly = true)
    public ScheduleRule getScheduleRule(UUID caseId) {
        caseService.requireCase(caseId);
        return scheduleRuleRepository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule rule not found"));
    }

    @Transactional
    public ScheduleRule upsertScheduleRule(
            UUID caseId,
            ScheduleRuleType type,
            LocalDate anchorDate,
            UUID parentAId,
            UUID parentBId,
            JsonNode metadata) {
        caseService.requireCase(caseId);
        if (parentAId.equals(parentBId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent A and Parent B must be different");
        }
        if (!caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, parentAId)
                || !caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, parentBId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule rule parents must be case members");
        }

        ScheduleRule rule = scheduleRuleRepository.findByCaseId(caseId).orElseGet(() -> {
            ScheduleRule created = new ScheduleRule();
            created.setId(UUID.randomUUID());
            created.setCaseId(caseId);
            return created;
        });
        rule.setType(type);
        rule.setAnchorDate(anchorDate);
        rule.setParentAId(parentAId);
        rule.setParentBId(parentBId);
        rule.setMetadata(metadata == null ? "{}" : metadata.toString());
        return scheduleRuleRepository.save(rule);
    }

    @Transactional
    public Event createEvent(UUID caseId, Event event) {
        caseService.requireCase(caseId);
        validateDateRange(event.getStartDate(), event.getEndDate(), "Event");
        if (event.getParentId() != null && !caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, event.getParentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event parent must be a case member");
        }

        event.setId(UUID.randomUUID());
        event.setCaseId(caseId);
        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<Event> listEvents(UUID caseId, LocalDate from, LocalDate to) {
        caseService.requireCase(caseId);
        validateDateRange(from, to, "Event query");
        return eventRepository.findByCaseIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(caseId, to, from);
    }

    private void validateDateRange(LocalDate start, LocalDate end, String label) {
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " date range is required");
        }
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " end date must be on or after start date");
        }
    }

    public record CaseMemberView(UUID personId, String externalSubject, String displayName, MemberRole role) {
    }
}
