package com.custodycalendar.api.web;

import com.custodycalendar.api.config.SolverDefaultsProperties;
import com.custodycalendar.api.domain.model.Child;
import com.custodycalendar.api.domain.model.Event;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.service.CaseResourceService;
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
import com.custodycalendar.api.web.dto.AcceptSolveOptionRequest;
import com.custodycalendar.api.web.dto.AcceptSolveOptionResponse;
import com.custodycalendar.api.web.dto.AddCaseMemberRequest;
import com.custodycalendar.api.web.dto.ChangedDayResponse;
import com.custodycalendar.api.web.dto.CaseMemberResponse;
import com.custodycalendar.api.web.dto.ChildResponse;
import com.custodycalendar.api.web.dto.CreateChildRequest;
import com.custodycalendar.api.web.dto.CreateEventRequest;
import com.custodycalendar.api.web.dto.EventResponse;
import com.custodycalendar.api.web.dto.LedgerImpactResponse;
import com.custodycalendar.api.web.dto.OwedBalanceResponse;
import com.custodycalendar.api.web.dto.ScheduleRuleResponse;
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
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
    private final ScheduleVersionService scheduleVersionService;
    private final SchoolCalendarService schoolCalendarService;
    private final AuthenticatedUserService authenticatedUserService;
    private final SolverDefaultsProperties solverDefaultsProperties;
    private final ObjectMapper objectMapper;

    public CaseResourceController(
            CaseResourceService caseResourceService,
            ScheduleSolveService scheduleSolveService,
            ScheduleVersionService scheduleVersionService,
            SchoolCalendarService schoolCalendarService,
            AuthenticatedUserService authenticatedUserService,
            SolverDefaultsProperties solverDefaultsProperties,
            ObjectMapper objectMapper) {
        this.caseResourceService = caseResourceService;
        this.scheduleSolveService = scheduleSolveService;
        this.scheduleVersionService = scheduleVersionService;
        this.schoolCalendarService = schoolCalendarService;
        this.authenticatedUserService = authenticatedUserService;
        this.solverDefaultsProperties = solverDefaultsProperties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/people")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public CaseMemberResponse addMember(@PathVariable UUID caseId, @Valid @RequestBody AddCaseMemberRequest request) {
        var member = caseResourceService.addMember(caseId, request.externalSubject(), request.displayName(), request.role());
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
            @Valid @RequestBody UpsertScheduleRuleRequest request) {
        ScheduleRule rule = caseResourceService.upsertScheduleRule(
                caseId,
                request.type(),
                request.anchorDate(),
                request.parentAId(),
                request.parentBId(),
                request.metadata());
        return toScheduleRuleResponse(rule);
    }

    @GetMapping("/schedule-rule")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public ScheduleRuleResponse getScheduleRule(@PathVariable UUID caseId) {
        return toScheduleRuleResponse(caseResourceService.getScheduleRule(caseId));
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public EventResponse createEvent(@PathVariable UUID caseId, @Valid @RequestBody CreateEventRequest request) {
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

        return toEventResponse(caseResourceService.createEvent(caseId, event));
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

    @PostMapping("/schedule/solve/accept")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public AcceptSolveOptionResponse acceptSolveOption(
            @PathVariable UUID caseId,
            Authentication authentication,
            @Valid @RequestBody AcceptSolveOptionRequest request) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        var accepted = scheduleVersionService.acceptSolveOption(
                caseId,
                user.subject(),
                request.optionId(),
                toSolveCommand(request.solveRequest()),
                request.reason());
        return new AcceptSolveOptionResponse(
                accepted.versionId(),
                accepted.status(),
                accepted.optionId(),
                accepted.scoreTotal(),
                accepted.scoreBreakdown(),
                accepted.changedDays().stream()
                        .map(d -> new ChangedDayResponse(d.date(), d.fromParentId(), d.toParentId()))
                        .toList(),
                accepted.scheduleDayCount());
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
                metadata);
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
                event.getNotes());
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
