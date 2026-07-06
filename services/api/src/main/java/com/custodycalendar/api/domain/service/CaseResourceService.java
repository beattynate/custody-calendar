package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.CaseMember;
import com.custodycalendar.api.domain.model.CaseMemberId;
import com.custodycalendar.api.domain.model.Child;
import com.custodycalendar.api.domain.model.Event;
import com.custodycalendar.api.domain.model.EventApprovalStatus;
import com.custodycalendar.api.domain.model.MemberRole;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.model.ScheduleRuleType;
import com.custodycalendar.api.domain.repository.CaseMemberRepository;
import com.custodycalendar.api.domain.repository.ChildRepository;
import com.custodycalendar.api.domain.repository.EventRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import com.custodycalendar.api.domain.repository.ScheduleRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public CaseResourceService(
            CaseService caseService,
            PersonRepository personRepository,
            PersonDirectoryService personDirectoryService,
            CaseMemberRepository caseMemberRepository,
            ChildRepository childRepository,
            ScheduleRuleRepository scheduleRuleRepository,
            EventRepository eventRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.caseService = caseService;
        this.personRepository = personRepository;
        this.personDirectoryService = personDirectoryService;
        this.caseMemberRepository = caseMemberRepository;
        this.childRepository = childRepository;
        this.scheduleRuleRepository = scheduleRuleRepository;
        this.eventRepository = eventRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
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
    public CaseMemberView addMember(UUID caseId, String externalSubject, String displayName, MemberRole role, UUID actorPersonId) {
        caseService.requireCase(caseId);

        Person person = personDirectoryService.ensurePerson(externalSubject, displayName);

        CaseMemberId memberId = new CaseMemberId();
        memberId.setCaseId(caseId);
        memberId.setPersonId(person.getId());

        boolean isNew = caseMemberRepository.findById(memberId).isEmpty();
        CaseMember member = caseMemberRepository.findById(memberId).orElseGet(() -> {
            CaseMember created = new CaseMember();
            created.setId(memberId);
            return created;
        });
        member.setRole(role);
        caseMemberRepository.save(member);

        if (isNew) {
            auditLogService.record(caseId, actorPersonId, "MEMBER_ADDED", "CASE_MEMBER", person.getId(),
                    Map.of("displayName", person.getDisplayName(), "role", role.name()));
        }

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

    /**
     * Initial rule creation applies directly (the other parent usually has
     * not signed in yet during onboarding). Once a rule exists, changes are
     * parked as a pending change that the other schedule-rule parent must
     * approve before they take effect.
     */
    @Transactional
    public RuleChangeResult upsertScheduleRule(
            UUID caseId,
            ScheduleRuleType type,
            LocalDate anchorDate,
            UUID parentAId,
            UUID parentBId,
            JsonNode metadata,
            UUID actorPersonId) {
        caseService.requireCase(caseId);
        validateRuleParents(caseId, parentAId, parentBId);

        ScheduleRule existing = scheduleRuleRepository.findByCaseId(caseId).orElse(null);
        if (existing == null) {
            ScheduleRule rule = new ScheduleRule();
            rule.setId(UUID.randomUUID());
            rule.setCaseId(caseId);
            rule.setType(type);
            rule.setAnchorDate(anchorDate);
            rule.setParentAId(parentAId);
            rule.setParentBId(parentBId);
            rule.setMetadata(metadata == null ? "{}" : metadata.toString());
            rule = scheduleRuleRepository.save(rule);
            auditLogService.record(caseId, actorPersonId, "RULE_CREATED", "SCHEDULE_RULE", rule.getId(),
                    Map.of("anchorDate", anchorDate.toString()));
            return new RuleChangeResult(rule, false);
        }

        if (existing.getPendingChange() != null && !actorPersonId.equals(existing.getChangeRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A rule change is already pending; approve or reject it first");
        }
        requireRuleParent(existing, actorPersonId);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("type", type.name());
        change.put("anchorDate", anchorDate.toString());
        change.put("parentAId", parentAId.toString());
        change.put("parentBId", parentBId.toString());
        change.put("metadata", metadata == null ? objectMapper.createObjectNode() : metadata);
        existing.setPendingChange(writeJson(change));
        existing.setChangeRequestedBy(actorPersonId);
        existing = scheduleRuleRepository.save(existing);
        auditLogService.record(caseId, actorPersonId, "RULE_CHANGE_REQUESTED", "SCHEDULE_RULE", existing.getId(),
                Map.of("anchorDate", anchorDate.toString()));
        return new RuleChangeResult(existing, true);
    }

    @Transactional
    public ScheduleRule approveScheduleRuleChange(UUID caseId, UUID actorPersonId) {
        ScheduleRule rule = requirePendingRuleChange(caseId);
        requireRuleParent(rule, actorPersonId);
        if (actorPersonId.equals(rule.getChangeRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The requesting parent cannot approve their own rule change");
        }

        JsonNode change = readJson(rule.getPendingChange());
        rule.setType(ScheduleRuleType.valueOf(change.get("type").asText()));
        rule.setAnchorDate(LocalDate.parse(change.get("anchorDate").asText()));
        rule.setParentAId(UUID.fromString(change.get("parentAId").asText()));
        rule.setParentBId(UUID.fromString(change.get("parentBId").asText()));
        rule.setMetadata(change.get("metadata") == null ? "{}" : change.get("metadata").toString());
        rule.setPendingChange(null);
        rule.setChangeRequestedBy(null);
        rule = scheduleRuleRepository.save(rule);
        auditLogService.record(caseId, actorPersonId, "RULE_CHANGE_APPROVED", "SCHEDULE_RULE", rule.getId(), null);
        return rule;
    }

    @Transactional
    public ScheduleRule rejectScheduleRuleChange(UUID caseId, UUID actorPersonId) {
        ScheduleRule rule = requirePendingRuleChange(caseId);
        requireRuleParent(rule, actorPersonId);

        rule.setPendingChange(null);
        rule.setChangeRequestedBy(null);
        rule = scheduleRuleRepository.save(rule);
        auditLogService.record(caseId, actorPersonId, "RULE_CHANGE_REJECTED", "SCHEDULE_RULE", rule.getId(), null);
        return rule;
    }

    /**
     * Locked events are hard schedule overrides, so creating one requires the
     * other schedule-rule parent's approval (when a rule exists). Non-locked
     * events only influence solver proposals, which already require both
     * parents, so they apply immediately.
     */
    @Transactional
    public Event createEvent(UUID caseId, Event event, UUID actorPersonId) {
        caseService.requireCase(caseId);
        validateDateRange(event.getStartDate(), event.getEndDate(), "Event");
        if (event.getParentId() != null && !caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, event.getParentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event parent must be a case member");
        }

        event.setId(UUID.randomUUID());
        event.setCaseId(caseId);
        event.setCreatedBy(actorPersonId);
        boolean needsConsent = event.isLocked() && consentRequired(caseId);
        event.setApprovalStatus(needsConsent ? EventApprovalStatus.PENDING_CREATE : EventApprovalStatus.ACTIVE);
        Event saved = eventRepository.save(event);

        auditLogService.record(caseId, actorPersonId,
                needsConsent ? "EVENT_CREATE_REQUESTED" : "EVENT_CREATED",
                "EVENT", saved.getId(),
                eventDetails(saved));
        return saved;
    }

    @Transactional
    public Event updateEvent(UUID caseId, UUID eventId, Event requested, UUID actorPersonId) {
        Event event = requireEvent(caseId, eventId);
        validateDateRange(requested.getStartDate(), requested.getEndDate(), "Event");
        if (requested.getParentId() != null && !caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, requested.getParentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event parent must be a case member");
        }

        boolean lockedInvolved = event.isLocked() || requested.isLocked();
        if (event.getApprovalStatus() == EventApprovalStatus.PENDING_CREATE) {
            if (!actorPersonId.equals(event.getCreatedBy())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This event is awaiting approval; approve or reject it instead");
            }
            applyEventFields(event, requested);
            Event saved = eventRepository.save(event);
            auditLogService.record(caseId, actorPersonId, "EVENT_CREATE_REQUESTED", "EVENT", saved.getId(), eventDetails(saved));
            return saved;
        }
        if (event.getApprovalStatus() != EventApprovalStatus.ACTIVE
                && !actorPersonId.equals(event.getChangeRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A change to this event is already pending; approve or reject it first");
        }

        if (!lockedInvolved || !consentRequired(caseId)) {
            applyEventFields(event, requested);
            event.setApprovalStatus(EventApprovalStatus.ACTIVE);
            event.setPendingChange(null);
            event.setChangeRequestedBy(null);
            Event saved = eventRepository.save(event);
            auditLogService.record(caseId, actorPersonId, "EVENT_UPDATED", "EVENT", saved.getId(), eventDetails(saved));
            return saved;
        }

        event.setPendingChange(writeJson(eventDetails(requested)));
        event.setChangeRequestedBy(actorPersonId);
        event.setApprovalStatus(EventApprovalStatus.PENDING_UPDATE);
        Event saved = eventRepository.save(event);
        auditLogService.record(caseId, actorPersonId, "EVENT_CHANGE_REQUESTED", "EVENT", saved.getId(), eventDetails(requested));
        return saved;
    }

    @Transactional
    public DeleteEventResult deleteEvent(UUID caseId, UUID eventId, UUID actorPersonId) {
        Event event = requireEvent(caseId, eventId);

        if (event.getApprovalStatus() == EventApprovalStatus.PENDING_CREATE) {
            if (!actorPersonId.equals(event.getCreatedBy())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This event is awaiting approval; approve or reject it instead");
            }
            eventRepository.delete(event);
            auditLogService.record(caseId, actorPersonId, "EVENT_DELETED", "EVENT", event.getId(),
                    eventDetails(event));
            return new DeleteEventResult(null, true);
        }
        if (event.getApprovalStatus() != EventApprovalStatus.ACTIVE
                && !actorPersonId.equals(event.getChangeRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A change to this event is already pending; approve or reject it first");
        }

        if (!event.isLocked() || !consentRequired(caseId)) {
            eventRepository.delete(event);
            auditLogService.record(caseId, actorPersonId, "EVENT_DELETED", "EVENT", event.getId(), eventDetails(event));
            return new DeleteEventResult(null, true);
        }

        event.setPendingChange(null);
        event.setChangeRequestedBy(actorPersonId);
        event.setApprovalStatus(EventApprovalStatus.PENDING_DELETE);
        Event saved = eventRepository.save(event);
        auditLogService.record(caseId, actorPersonId, "EVENT_DELETE_REQUESTED", "EVENT", saved.getId(), eventDetails(saved));
        return new DeleteEventResult(saved, false);
    }

    @Transactional
    public Event approveEvent(UUID caseId, UUID eventId, UUID actorPersonId) {
        Event event = requireEvent(caseId, eventId);
        requireEventApprover(caseId, event, actorPersonId);

        switch (event.getApprovalStatus()) {
            case PENDING_CREATE -> {
                event.setApprovalStatus(EventApprovalStatus.ACTIVE);
            }
            case PENDING_UPDATE -> {
                JsonNode change = readJson(event.getPendingChange());
                Event requested = eventFromDetails(change);
                applyEventFields(event, requested);
                event.setApprovalStatus(EventApprovalStatus.ACTIVE);
                event.setPendingChange(null);
                event.setChangeRequestedBy(null);
            }
            case PENDING_DELETE -> {
                eventRepository.delete(event);
                auditLogService.record(caseId, actorPersonId, "EVENT_APPROVED", "EVENT", event.getId(),
                        Map.of("outcome", "deleted", "title", event.getTitle()));
                return event;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event has no pending change to approve");
        }
        event.setDecidedBy(actorPersonId);
        event.setDecidedAt(OffsetDateTime.now());
        Event saved = eventRepository.save(event);
        auditLogService.record(caseId, actorPersonId, "EVENT_APPROVED", "EVENT", saved.getId(), eventDetails(saved));
        return saved;
    }

    @Transactional
    public Event rejectEvent(UUID caseId, UUID eventId, UUID actorPersonId) {
        Event event = requireEvent(caseId, eventId);
        requireEventDecider(caseId, event, actorPersonId);

        switch (event.getApprovalStatus()) {
            case PENDING_CREATE -> {
                eventRepository.delete(event);
                auditLogService.record(caseId, actorPersonId, "EVENT_REJECTED", "EVENT", event.getId(),
                        Map.of("outcome", "creation rejected", "title", event.getTitle()));
                return event;
            }
            case PENDING_UPDATE, PENDING_DELETE -> {
                event.setApprovalStatus(EventApprovalStatus.ACTIVE);
                event.setPendingChange(null);
                event.setChangeRequestedBy(null);
                event.setDecidedBy(actorPersonId);
                event.setDecidedAt(OffsetDateTime.now());
                Event saved = eventRepository.save(event);
                auditLogService.record(caseId, actorPersonId, "EVENT_REJECTED", "EVENT", saved.getId(), eventDetails(saved));
                return saved;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event has no pending change to reject");
        }
    }

    @Transactional(readOnly = true)
    public List<Event> listEvents(UUID caseId, LocalDate from, LocalDate to) {
        caseService.requireCase(caseId);
        validateDateRange(from, to, "Event query");
        return eventRepository.findByCaseIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(caseId, to, from);
    }

    private Event requireEvent(UUID caseId, UUID eventId) {
        caseService.requireCase(caseId);
        return eventRepository.findById(eventId)
                .filter(event -> caseId.equals(event.getCaseId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    /** Consent applies once a two-parent schedule rule exists for the case. */
    private boolean consentRequired(UUID caseId) {
        return scheduleRuleRepository.findByCaseId(caseId).isPresent();
    }

    private void requireEventApprover(UUID caseId, Event event, UUID actorPersonId) {
        requireEventDecider(caseId, event, actorPersonId);
        UUID requester = event.getApprovalStatus() == EventApprovalStatus.PENDING_CREATE
                ? event.getCreatedBy()
                : event.getChangeRequestedBy();
        if (actorPersonId.equals(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The requesting parent cannot approve their own change");
        }
    }

    /** Rejection is allowed for any rule parent, including the requester (withdraw). */
    private void requireEventDecider(UUID caseId, Event event, UUID actorPersonId) {
        ScheduleRule rule = scheduleRuleRepository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule rule is required"));
        requireRuleParent(rule, actorPersonId);
    }

    private void requireRuleParent(ScheduleRule rule, UUID personId) {
        if (!rule.getParentAId().equals(personId) && !rule.getParentBId().equals(personId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only schedule rule parents can do this");
        }
    }

    private ScheduleRule requirePendingRuleChange(UUID caseId) {
        caseService.requireCase(caseId);
        ScheduleRule rule = scheduleRuleRepository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule rule not found"));
        if (rule.getPendingChange() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending rule change");
        }
        return rule;
    }

    private void validateRuleParents(UUID caseId, UUID parentAId, UUID parentBId) {
        if (parentAId.equals(parentBId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent A and Parent B must be different");
        }
        if (!caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, parentAId)
                || !caseMemberRepository.existsByIdCaseIdAndIdPersonId(caseId, parentBId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule rule parents must be case members");
        }
    }

    private void applyEventFields(Event target, Event source) {
        target.setTitle(source.getTitle());
        target.setStartDate(source.getStartDate());
        target.setEndDate(source.getEndDate());
        target.setEventType(source.getEventType());
        target.setAppliesTo(source.getAppliesTo());
        target.setParentId(source.getParentId());
        target.setLocked(source.isLocked());
        target.setRecurrenceRule(source.getRecurrenceRule());
        target.setNotes(source.getNotes());
    }

    private Map<String, Object> eventDetails(Event event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title", event.getTitle());
        details.put("startDate", event.getStartDate() == null ? null : event.getStartDate().toString());
        details.put("endDate", event.getEndDate() == null ? null : event.getEndDate().toString());
        details.put("eventType", event.getEventType() == null ? null : event.getEventType().name());
        details.put("appliesTo", event.getAppliesTo() == null ? null : event.getAppliesTo().name());
        details.put("parentId", event.getParentId() == null ? null : event.getParentId().toString());
        details.put("locked", event.isLocked());
        details.put("recurrenceRule", event.getRecurrenceRule());
        details.put("notes", event.getNotes());
        return details;
    }

    private Event eventFromDetails(JsonNode details) {
        Event event = new Event();
        event.setTitle(details.path("title").asText(null));
        event.setStartDate(LocalDate.parse(details.path("startDate").asText()));
        event.setEndDate(LocalDate.parse(details.path("endDate").asText()));
        event.setEventType(com.custodycalendar.api.domain.model.EventType.valueOf(details.path("eventType").asText()));
        event.setAppliesTo(com.custodycalendar.api.domain.model.EventAppliesTo.valueOf(details.path("appliesTo").asText()));
        event.setParentId(details.hasNonNull("parentId") ? UUID.fromString(details.get("parentId").asText()) : null);
        event.setLocked(details.path("locked").asBoolean(false));
        event.setRecurrenceRule(details.hasNonNull("recurrenceRule") ? details.get("recurrenceRule").asText() : null);
        event.setNotes(details.hasNonNull("notes") ? details.get("notes").asText() : null);
        return event;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize pending change");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read pending change");
        }
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

    public record RuleChangeResult(ScheduleRule rule, boolean pendingApproval) {
    }

    public record DeleteEventResult(Event event, boolean deleted) {
    }
}
