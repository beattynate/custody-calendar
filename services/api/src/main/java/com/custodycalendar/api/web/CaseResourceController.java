package com.custodycalendar.api.web;

import com.custodycalendar.api.config.SolverDefaultsProperties;
import com.custodycalendar.api.domain.model.Child;
import com.custodycalendar.api.domain.model.Event;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.repository.PersonRepository;
import com.custodycalendar.api.domain.service.AuditLogService;
import com.custodycalendar.api.domain.service.CaseResourceService;
import com.custodycalendar.api.domain.service.PersonDirectoryService;
import com.custodycalendar.api.domain.service.LedgerService;
import com.custodycalendar.api.domain.service.ScheduleIcsService;
import com.custodycalendar.api.domain.service.ScheduleProposalService;
import com.custodycalendar.api.domain.service.ScheduleSolveService;
import com.custodycalendar.api.domain.service.ScheduleVersionService;
import com.custodycalendar.api.domain.service.SchoolCalendarService;
import com.custodycalendar.api.domain.solver.RequestedScheduleEvent;
import com.custodycalendar.api.domain.solver.ScheduleSolveCommand;
import com.custodycalendar.api.domain.solver.SolveComputationResult;
import com.custodycalendar.api.domain.solver.SolverConstraints;
import com.custodycalendar.api.domain.solver.SolverWeights;
import com.custodycalendar.api.security.AuthenticatedUserService;
import com.custodycalendar.api.security.AuthenticatedUserService.AuthenticatedUser;
import com.custodycalendar.api.web.dto.AddCaseMemberRequest;
import com.custodycalendar.api.web.dto.AuditEntryResponse;
import com.custodycalendar.api.web.dto.ChangedDayResponse;
import com.custodycalendar.api.web.dto.CaseMemberResponse;
import com.custodycalendar.api.web.dto.ChildResponse;
import com.custodycalendar.api.web.dto.CreateChildRequest;
import com.custodycalendar.api.web.dto.CreateEventRequest;
import com.custodycalendar.api.web.dto.CreateScheduleProposalRequest;
import com.custodycalendar.api.web.dto.EventResponse;
import com.custodycalendar.api.web.dto.LedgerEntryResponse;
import com.custodycalendar.api.web.dto.LedgerImpactResponse;
import com.custodycalendar.api.web.dto.OwedBalanceResponse;
import com.custodycalendar.api.web.dto.ScheduleRuleResponse;
import com.custodycalendar.api.web.dto.ScheduleProposalApprovalResponse;
import com.custodycalendar.api.web.dto.ScheduleProposalDecisionRequest;
import com.custodycalendar.api.web.dto.ScheduleProposalResponse;
import com.custodycalendar.api.web.dto.ScheduleDayResponse;
import com.custodycalendar.api.web.dto.ScoreComponentResponse;
import com.custodycalendar.api.web.dto.SchoolCalendarDayRequest;
import com.custodycalendar.api.web.dto.SchoolCalendarDayResponse;
import com.custodycalendar.api.web.dto.SchoolCalendarBulkRequest;
import com.custodycalendar.api.web.dto.SolveOptionResponse;
import com.custodycalendar.api.web.dto.SolveScheduleRequest;
import com.custodycalendar.api.web.dto.SolveScheduleResponse;
import com.custodycalendar.api.web.dto.UpsertScheduleRuleRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@RequestMapping("/api/v1/cases/{caseId}")
public class CaseResourceController {

    private final CaseResourceService caseResourceService;
    private final ScheduleSolveService scheduleSolveService;
    private final ScheduleProposalService scheduleProposalService;
    private final ScheduleVersionService scheduleVersionService;
    private final SchoolCalendarService schoolCalendarService;
    private final LedgerService ledgerService;
    private final ScheduleIcsService scheduleIcsService;
    private final AuditLogService auditLogService;
    private final AuthenticatedUserService authenticatedUserService;
    private final PersonDirectoryService personDirectoryService;
    private final PersonRepository personRepository;
    private final SolverDefaultsProperties solverDefaultsProperties;
    private final ObjectMapper objectMapper;

    public CaseResourceController(
            CaseResourceService caseResourceService,
            ScheduleSolveService scheduleSolveService,
            ScheduleProposalService scheduleProposalService,
            ScheduleVersionService scheduleVersionService,
            SchoolCalendarService schoolCalendarService,
            LedgerService ledgerService,
            ScheduleIcsService scheduleIcsService,
            AuditLogService auditLogService,
            AuthenticatedUserService authenticatedUserService,
            PersonDirectoryService personDirectoryService,
            PersonRepository personRepository,
            SolverDefaultsProperties solverDefaultsProperties,
            ObjectMapper objectMapper) {
        this.caseResourceService = caseResourceService;
        this.scheduleSolveService = scheduleSolveService;
        this.scheduleProposalService = scheduleProposalService;
        this.scheduleVersionService = scheduleVersionService;
        this.schoolCalendarService = schoolCalendarService;
        this.ledgerService = ledgerService;
        this.scheduleIcsService = scheduleIcsService;
        this.auditLogService = auditLogService;
        this.authenticatedUserService = authenticatedUserService;
        this.personDirectoryService = personDirectoryService;
        this.personRepository = personRepository;
        this.solverDefaultsProperties = solverDefaultsProperties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/people")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public CaseMemberResponse addMember(@PathVariable UUID caseId, Authentication authentication, @Valid @RequestBody AddCaseMemberRequest request) {
        var member = caseResourceService.addMember(caseId, request.externalSubject(), request.displayName(), request.role(), requireActor(authentication).getId());
        return new CaseMemberResponse(member.personId(), member.externalSubject(), member.displayName(), member.role());
    }

    @GetMapping("/people")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<CaseMemberResponse> listMembers(@PathVariable UUID caseId) {
        return caseResourceService.listMembers(caseId).stream()
                .map(m -> new CaseMemberResponse(m.personId(), m.externalSubject(), m.displayName(), m.role()))
                .toList();
    }

    @PostMapping("/children")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ChildResponse addChild(@PathVariable UUID caseId, @Valid @RequestBody CreateChildRequest request) {
        Child child = caseResourceService.addChild(caseId, request.name());
        return new ChildResponse(child.getId(), child.getName());
    }

    @GetMapping("/children")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<ChildResponse> listChildren(@PathVariable UUID caseId) {
        return caseResourceService.listChildren(caseId).stream()
                .map(child -> new ChildResponse(child.getId(), child.getName()))
                .toList();
    }

    @PutMapping("/schedule-rule")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ScheduleRuleResponse upsertScheduleRule(
            @PathVariable UUID caseId,
            Authentication authentication,
            @Valid @RequestBody UpsertScheduleRuleRequest request) {
        var result = caseResourceService.upsertScheduleRule(
                caseId,
                request.type(),
                request.anchorDate(),
                request.parentAId(),
                request.parentBId(),
                request.metadata(),
                requireActor(authentication).getId());
        return toScheduleRuleResponse(result.rule());
    }

    @PostMapping("/schedule-rule/approve")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ScheduleRuleResponse approveScheduleRuleChange(@PathVariable UUID caseId, Authentication authentication) {
        return toScheduleRuleResponse(caseResourceService.approveScheduleRuleChange(caseId, requireActor(authentication).getId()));
    }

    @PostMapping("/schedule-rule/reject")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ScheduleRuleResponse rejectScheduleRuleChange(@PathVariable UUID caseId, Authentication authentication) {
        return toScheduleRuleResponse(caseResourceService.rejectScheduleRuleChange(caseId, requireActor(authentication).getId()));
    }

    @GetMapping("/schedule-rule")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ScheduleRuleResponse getScheduleRule(@PathVariable UUID caseId) {
        return toScheduleRuleResponse(caseResourceService.getScheduleRule(caseId));
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public EventResponse createEvent(@PathVariable UUID caseId, Authentication authentication, @Valid @RequestBody CreateEventRequest request) {
        return toEventResponse(caseResourceService.createEvent(caseId, toEvent(request), requireActor(authentication).getId()));
    }

    @PutMapping("/events/{eventId}")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public EventResponse updateEvent(
            @PathVariable UUID caseId,
            @PathVariable UUID eventId,
            Authentication authentication,
            @Valid @RequestBody CreateEventRequest request) {
        return toEventResponse(caseResourceService.updateEvent(caseId, eventId, toEvent(request), requireActor(authentication).getId()));
    }

    @DeleteMapping("/events/{eventId}")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public org.springframework.http.ResponseEntity<EventResponse> deleteEvent(
            @PathVariable UUID caseId,
            @PathVariable UUID eventId,
            Authentication authentication) {
        var result = caseResourceService.deleteEvent(caseId, eventId, requireActor(authentication).getId());
        if (result.deleted()) {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
        return org.springframework.http.ResponseEntity.ok(toEventResponse(result.event()));
    }

    @PostMapping("/events/{eventId}/approve")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public EventResponse approveEvent(@PathVariable UUID caseId, @PathVariable UUID eventId, Authentication authentication) {
        return toEventResponse(caseResourceService.approveEvent(caseId, eventId, requireActor(authentication).getId()));
    }

    @PostMapping("/events/{eventId}/reject")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public EventResponse rejectEvent(@PathVariable UUID caseId, @PathVariable UUID eventId, Authentication authentication) {
        return toEventResponse(caseResourceService.rejectEvent(caseId, eventId, requireActor(authentication).getId()));
    }

    @GetMapping("/events")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<EventResponse> listEvents(
            @PathVariable UUID caseId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return caseResourceService.listEvents(caseId, from, to).stream()
                .map(this::toEventResponse)
                .toList();
    }

    @PostMapping("/schedule/solve")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public SolveScheduleResponse solveSchedule(
            @PathVariable UUID caseId,
            @Valid @RequestBody SolveScheduleRequest request) {
        SolveComputationResult result = scheduleSolveService.solveDetailed(caseId, toSolveCommand(request));

        return new SolveScheduleResponse(result.options().stream()
                .map(option -> new SolveOptionResponse(
                        option.optionId(),
                        option.scoreTotal(),
                        option.scoreBreakdown(),
                        option.scoreDetails().entrySet().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        java.util.Map.Entry::getKey,
                                        e -> new ScoreComponentResponse(
                                                e.getValue().key(),
                                                e.getValue().count(),
                                                e.getValue().weight(),
                                                e.getValue().score()))),
                        option.patchOperations(),
                        option.changedDays().stream()
                                .map(d -> new ChangedDayResponse(d.date(), d.fromParentId(), d.toParentId()))
                                .toList(),
                        option.ledgerImpact().stream()
                                .map(l -> new LedgerImpactResponse(
                                        l.fromParentId(),
                                        l.toParentId(),
                                        l.amountDays(),
                                        l.reason(),
                                        l.dayBucket()))
                                .toList(),
                        option.owedBalances().stream()
                                .map(b -> new OwedBalanceResponse(
                                        b.fromParentId(),
                                        b.toParentId(),
                                        b.amountDays(),
                                        b.dayBucket()))
                                .toList()))
                .toList());
    }

    @GetMapping("/schedule/proposals")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<ScheduleProposalResponse> listScheduleProposals(@PathVariable UUID caseId) {
        return scheduleProposalService.listProposals(caseId).stream()
                .map(this::toScheduleProposalResponse)
                .toList();
    }

    @PostMapping("/schedule/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ScheduleProposalResponse createScheduleProposal(
            @PathVariable UUID caseId,
            Authentication authentication,
            @Valid @RequestBody CreateScheduleProposalRequest request) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        return toScheduleProposalResponse(scheduleProposalService.createProposal(
                caseId,
                user.subject(),
                toSolveCommand(request.solveRequest()),
                request.optionId(),
                request.reason()));
    }

    @PostMapping("/schedule/proposals/{proposalId}/approve")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ScheduleProposalResponse approveScheduleProposal(
            @PathVariable UUID caseId,
            @PathVariable UUID proposalId,
            Authentication authentication,
            @RequestBody(required = false) ScheduleProposalDecisionRequest request) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        return toScheduleProposalResponse(scheduleProposalService.approveProposal(
                caseId,
                proposalId,
                user.subject(),
                request == null ? null : request.comment()));
    }

    @PostMapping("/schedule/proposals/{proposalId}/reject")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ScheduleProposalResponse rejectScheduleProposal(
            @PathVariable UUID caseId,
            @PathVariable UUID proposalId,
            Authentication authentication,
            @RequestBody(required = false) ScheduleProposalDecisionRequest request) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        return toScheduleProposalResponse(scheduleProposalService.rejectProposal(
                caseId,
                proposalId,
                user.subject(),
                request == null ? null : request.comment()));
    }

    @GetMapping("/schedule")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<ScheduleDayResponse> getSchedule(
            @PathVariable UUID caseId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var result = scheduleVersionService.getAcceptedSchedule(caseId, from, to);
        return result.days().stream()
                .map(day -> new ScheduleDayResponse(
                        day.getId().getDate(),
                        day.getAssignedParentId(),
                        day.getLockedSourceEventId(),
                        day.getDerivedFrom()))
                .toList();
    }

    @GetMapping("/ledger")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<LedgerEntryResponse> listLedgerEntries(@PathVariable UUID caseId) {
        return ledgerService.listEntries(caseId).stream()
                .map(entry -> new LedgerEntryResponse(
                        entry.getId(),
                        entry.getDate(),
                        entry.getFromParentId(),
                        entry.getToParentId(),
                        entry.getAmountDays(),
                        entry.getReasonType() == null ? null : entry.getReasonType().name(),
                        entry.getDayBucket() == null ? null : entry.getDayBucket().name(),
                        entry.getEventId(),
                        entry.getVersionId(),
                        entry.getNotes()))
                .toList();
    }

    @GetMapping("/ledger/balance")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<OwedBalanceResponse> getLedgerBalance(@PathVariable UUID caseId) {
        return ledgerService.computeBalances(caseId).stream()
                .map(balance -> new OwedBalanceResponse(
                        balance.fromParentId(),
                        balance.toParentId(),
                        balance.amountDays(),
                        balance.dayBucket() == null ? null : balance.dayBucket().name()))
                .toList();
    }

    @GetMapping("/ics-feed")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public Map<String, String> getIcsFeed(@PathVariable UUID caseId) {
        return Map.of("feedPath", scheduleIcsService.getFeedPath(caseId));
    }

    @PostMapping("/ics-feed")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public Map<String, String> rotateIcsFeed(@PathVariable UUID caseId, Authentication authentication) {
        return Map.of("feedPath", scheduleIcsService.rotateFeedToken(caseId, requireActor(authentication).getId()));
    }

    @GetMapping(value = "/schedule.ics", produces = "text/calendar")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public org.springframework.http.ResponseEntity<String> exportScheduleIcs(
            @PathVariable UUID caseId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=custody-schedule.ics")
                .body(scheduleIcsService.buildIcs(caseId, from, to));
    }

    @GetMapping("/audit")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<AuditEntryResponse> listAuditEntries(@PathVariable UUID caseId) {
        var entries = auditLogService.listForCase(caseId);
        Map<UUID, Person> actors = personRepository.findAllById(
                        entries.stream()
                                .map(e -> e.getActorPersonId())
                                .filter(java.util.Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet()))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Person::getId, p -> p));
        return entries.stream()
                .map(entry -> new AuditEntryResponse(
                        entry.getId(),
                        entry.getAction(),
                        entry.getEntityType(),
                        entry.getEntityId(),
                        entry.getActorPersonId(),
                        entry.getActorPersonId() == null || actors.get(entry.getActorPersonId()) == null
                                ? null
                                : actors.get(entry.getActorPersonId()).getDisplayName(),
                        readJsonOrNull(entry.getDetails()),
                        entry.getCreatedAt()))
                .toList();
    }

    @GetMapping("/school-calendar-days")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<SchoolCalendarDayResponse> listSchoolCalendarDays(
            @PathVariable UUID caseId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return schoolCalendarService.listDays(caseId, from, to).stream()
                .map(day -> new SchoolCalendarDayResponse(
                        day.getId().getDate(),
                        day.getDayType()))
                .toList();
    }

    @PostMapping("/school-calendar-days")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<SchoolCalendarDayResponse> upsertSchoolCalendarDays(
            @PathVariable UUID caseId,
            @Valid @RequestBody List<SchoolCalendarDayRequest> request) {
        return schoolCalendarService.upsertDays(caseId, request).stream()
                .map(day -> new SchoolCalendarDayResponse(
                        day.getId().getDate(),
                        day.getDayType()))
                .toList();
    }

    @PostMapping("/school-calendar-days/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public List<SchoolCalendarDayResponse> bulkGenerateSchoolCalendarDays(
            @PathVariable UUID caseId,
            @Valid @RequestBody SchoolCalendarBulkRequest request) {
        return schoolCalendarService.bulkGenerate(caseId, request).stream()
                .map(day -> new SchoolCalendarDayResponse(
                        day.getId().getDate(),
                        day.getDayType()))
                .toList();
    }

    private Person requireActor(Authentication authentication) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        return personDirectoryService.requireBySubject(user.subject());
    }

    private Event toEvent(CreateEventRequest request) {
        Event event = new Event();
        event.setTitle(request.title());
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setEventType(request.eventType());
        event.setAppliesTo(request.appliesTo());
        event.setParentId(request.parentId());
        event.setLocked(request.locked());
        event.setRecurrenceRule(request.recurrenceRule());
        event.setNotes(request.notes());
        return event;
    }

    private JsonNode readJsonOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private ScheduleRuleResponse toScheduleRuleResponse(ScheduleRule rule) {
        JsonNode metadata;
        try {
            metadata = objectMapper.readTree(rule.getMetadata());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid schedule rule metadata");
        }

        return new ScheduleRuleResponse(
                rule.getId(),
                rule.getCaseId(),
                rule.getType(),
                rule.getAnchorDate(),
                rule.getParentAId(),
                rule.getParentBId(),
                metadata,
                readJsonOrNull(rule.getPendingChange()),
                rule.getChangeRequestedBy());
    }

    private EventResponse toEventResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getCaseId(),
                event.getTitle(),
                event.getStartDate(),
                event.getEndDate(),
                event.getEventType(),
                event.getAppliesTo(),
                event.getParentId(),
                event.isLocked(),
                event.getRecurrenceRule(),
                event.getNotes(),
                event.getApprovalStatus() == null ? null : event.getApprovalStatus().name(),
                event.getCreatedBy(),
                event.getChangeRequestedBy(),
                readJsonOrNull(event.getPendingChange()));
    }

    private ScheduleProposalResponse toScheduleProposalResponse(ScheduleProposalService.ProposalView proposal) {
        return new ScheduleProposalResponse(
                proposal.proposalId(),
                proposal.status(),
                proposal.createdBy(),
                proposal.createdByName(),
                proposal.createdAt(),
                proposal.optionId(),
                proposal.reason(),
                proposal.acceptedVersionId(),
                proposal.optionSnapshot(),
                proposal.approvals().stream()
                        .map(a -> new ScheduleProposalApprovalResponse(
                                a.personId(),
                                a.displayName(),
                                a.decision(),
                                a.decidedAt(),
                                a.comment()))
                        .toList());
    }

    private ScheduleSolveCommand toSolveCommand(SolveScheduleRequest request) {
        SolverConstraints defaultConstraints = solverDefaultsProperties.toConstraints();
        SolverWeights defaultWeights = solverDefaultsProperties.toWeights();

        RequestedScheduleEvent requestedEvent = request.newEvent() == null
                ? null
                : new RequestedScheduleEvent(
                        request.newEvent().title(),
                        request.newEvent().startDate(),
                        request.newEvent().endDate(),
                        request.newEvent().parentId(),
                        request.newEvent().locked());

        return new ScheduleSolveCommand(
                request.baseVersionId(),
                request.horizonStart(),
                request.horizonEnd(),
                requestedEvent,
                request.constraints() == null
                        ? defaultConstraints
                        : new SolverConstraints(
                                request.constraints().minRunDays() == null
                                        ? defaultConstraints.minRunDays()
                                        : request.constraints().minRunDays(),
                                request.constraints().compensationWindowDays() == null
                                        ? defaultConstraints.compensationWindowDays()
                                        : request.constraints().compensationWindowDays(),
                                request.constraints().respectLocked() == null
                                        ? defaultConstraints.respectLocked()
                                        : request.constraints().respectLocked()),
                request.weights() == null
                        ? defaultWeights
                        : new SolverWeights(
                                valueOrDefault(request.weights().transitionPenalty(), defaultWeights.transitionPenalty()),
                                valueOrDefault(request.weights().schoolNightTransitionPenalty(), defaultWeights.schoolNightTransitionPenalty()),
                                valueOrDefault(request.weights().parityDriftPenalty(), defaultWeights.parityDriftPenalty()),
                                valueOrDefault(request.weights().lockedProximityPenalty(), defaultWeights.lockedProximityPenalty()),
                                valueOrDefault(request.weights().owedImbalancePenalty(), defaultWeights.owedImbalancePenalty()),
                                valueOrDefault(request.weights().runDaysOverThreePenalty(), defaultWeights.runDaysOverThreePenalty())));
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
