package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.LedgerEntry;
import com.custodycalendar.api.domain.model.LedgerDayBucket;
import com.custodycalendar.api.domain.model.LedgerReasonType;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.ScheduleDay;
import com.custodycalendar.api.domain.model.ScheduleDayId;
import com.custodycalendar.api.domain.model.ScheduleVersion;
import com.custodycalendar.api.domain.model.ScheduleVersionStatus;
import com.custodycalendar.api.domain.repository.LedgerEntryRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import com.custodycalendar.api.domain.repository.ScheduleDayRepository;
import com.custodycalendar.api.domain.repository.ScheduleVersionRepository;
import com.custodycalendar.api.domain.solver.ScheduleAssignmentSet;
import com.custodycalendar.api.domain.solver.ScheduleSolveCommand;
import com.custodycalendar.api.domain.solver.SolveComputationResult;
import com.custodycalendar.api.domain.solver.SolveOptionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleVersionService {

    private final PersonRepository personRepository;
    private final ScheduleVersionRepository scheduleVersionRepository;
    private final ScheduleDayRepository scheduleDayRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ScheduleSolveService scheduleSolveService;
    private final ObjectMapper objectMapper;

    public ScheduleVersionService(
            PersonRepository personRepository,
            ScheduleVersionRepository scheduleVersionRepository,
            ScheduleDayRepository scheduleDayRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ScheduleSolveService scheduleSolveService,
            ObjectMapper objectMapper) {
        this.personRepository = personRepository;
        this.scheduleVersionRepository = scheduleVersionRepository;
        this.scheduleDayRepository = scheduleDayRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.scheduleSolveService = scheduleSolveService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AcceptedSolveVersionResult acceptSolveOption(
            UUID caseId,
            String externalSubject,
            String optionId,
            ScheduleSolveCommand command,
            String reason) {
        Person actor = personRepository.findByExternalSubject(externalSubject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authenticated person record not found"));

        SolveComputationResult solveResult = scheduleSolveService.solveDetailed(caseId, command);
        SolveOptionResult selected = solveResult.options().stream()
                .filter(option -> option.optionId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected optionId not found in solver result"));

        ScheduleVersion version = new ScheduleVersion();
        UUID versionId = UUID.randomUUID();
        version.setId(versionId);
        version.setCaseId(caseId);
        version.setStatus(ScheduleVersionStatus.ACCEPTED);
        version.setCreatedBy(actor.getId());
        version.setCreatedAt(OffsetDateTime.now());
        version.setBasedOnVersionId(command.baseVersionId());
        version.setReason((reason == null || reason.isBlank())
                ? "Accepted solver option " + selected.optionId()
                : reason.trim());
        version.setPreferences(serializePreferences(selected, command));
        scheduleVersionRepository.save(version);

        List<ScheduleDay> days = materializeDays(versionId, selected.assignments());
        scheduleDayRepository.saveAll(days);

        List<LedgerEntry> ledgerEntries = materializeLedgerEntries(caseId, versionId, selected.ledgerImpact());
        if (!ledgerEntries.isEmpty()) {
            ledgerEntryRepository.saveAll(ledgerEntries);
        }

        return new AcceptedSolveVersionResult(
                versionId,
                version.getStatus().name(),
                selected.optionId(),
                selected.scoreTotal(),
                selected.scoreBreakdown(),
                selected.changedDays(),
                days.size());
    }

    @Transactional(readOnly = true)
    public ScheduleRangeResult getAcceptedSchedule(UUID caseId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from/to query params are required");
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
        }

        ScheduleVersion accepted = scheduleVersionRepository
                .findFirstByCaseIdAndStatusOrderByCreatedAtDesc(caseId, ScheduleVersionStatus.ACCEPTED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No accepted schedule found"));

        List<ScheduleDay> days = scheduleDayRepository
                .findByIdVersionIdAndIdDateBetweenOrderByIdDateAsc(accepted.getId(), from, to);

        return new ScheduleRangeResult(accepted.getId(), accepted.getStatus().name(), days);
    }

    private String serializePreferences(SolveOptionResult selected, ScheduleSolveCommand command) {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("solver", "v1");
        preferences.put("optionId", selected.optionId());
        preferences.put("scoreTotal", selected.scoreTotal());
        preferences.put("scoreBreakdown", selected.scoreBreakdown());
        preferences.put("scoreDetails", selected.scoreDetails());
        preferences.put("changedDays", selected.changedDays());
        preferences.put("owedBalances", selected.owedBalances());
        preferences.put("patchOperations", selected.patchOperations());
        preferences.put("constraints", Map.of(
                "minRunDays", command.constraints().minRunDays(),
                "compensationWindowDays", command.constraints().compensationWindowDays(),
                "respectLocked", command.constraints().respectLocked()));
        preferences.put("weights", Map.of(
                "transitionPenalty", command.weights().transitionPenalty(),
                "schoolNightTransitionPenalty", command.weights().schoolNightTransitionPenalty(),
                "parityDriftPenalty", command.weights().parityDriftPenalty(),
                "lockedProximityPenalty", command.weights().lockedProximityPenalty(),
                "owedImbalancePenalty", command.weights().owedImbalancePenalty(),
                "runDaysOverThreePenalty", command.weights().runDaysOverThreePenalty()));
        preferences.put("horizon", Map.of(
                "start", command.horizonStart().toString(),
                "end", command.horizonEnd().toString()));
        if (command.newEvent() != null) {
            preferences.put("newEvent", Map.of(
                    "title", command.newEvent().title(),
                    "startDate", command.newEvent().startDate().toString(),
                    "endDate", command.newEvent().endDate().toString(),
                    "parentId", command.newEvent().parentId().toString(),
                    "locked", command.newEvent().locked()));
        } else {
            preferences.put("newEvent", null);
        }

        try {
            return objectMapper.writeValueAsString(preferences);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize schedule version preferences");
        }
    }

    private List<ScheduleDay> materializeDays(UUID versionId, ScheduleAssignmentSet assignments) {
        List<ScheduleDay> days = new ArrayList<>();
        assignments.days().forEach((date, assignment) -> {
            ScheduleDayId id = new ScheduleDayId();
            id.setVersionId(versionId);
            id.setDate(date);

            ScheduleDay day = new ScheduleDay();
            day.setId(id);
            day.setAssignedParentId(assignment.assignedParentId());
            day.setLockedSourceEventId(assignment.lockedSourceEventId());
            day.setDerivedFrom(assignment.derivedFrom());
            days.add(day);
        });
        return days;
    }

    private List<LedgerEntry> materializeLedgerEntries(
            UUID caseId,
            UUID versionId,
            List<SolveOptionResult.LedgerImpact> ledgerImpact) {
        List<LedgerEntry> entries = new ArrayList<>();
        for (SolveOptionResult.LedgerImpact impact : ledgerImpact) {
            LedgerEntry entry = new LedgerEntry();
            entry.setId(UUID.randomUUID());
            entry.setCaseId(caseId);
            entry.setDate(LocalDate.now());
            entry.setFromParentId(impact.fromParentId());
            entry.setToParentId(impact.toParentId());
            entry.setAmountDays(impact.amountDays());
            entry.setReasonType(toLedgerReasonType(impact.reason()));
            if (impact.dayBucket() != null && !impact.dayBucket().isBlank()) {
                entry.setDayBucket(LedgerDayBucket.valueOf(impact.dayBucket()));
            }
            entry.setVersionId(versionId);
            entry.setNotes(impact.reason());
            entries.add(entry);
        }
        return entries;
    }

    private LedgerReasonType toLedgerReasonType(String reason) {
        if (reason == null) {
            return LedgerReasonType.VACATION_TAKE;
        }
        String normalized = reason.trim().toUpperCase();
        try {
            return LedgerReasonType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            if (normalized.contains("HOLIDAY")) {
                return LedgerReasonType.HOLIDAY_RULE;
            }
            if (normalized.contains("MAKEUP")) {
                return LedgerReasonType.MAKEUP_GIVE;
            }
            return LedgerReasonType.VACATION_TAKE;
        }
    }

    public record AcceptedSolveVersionResult(
            UUID versionId,
            String status,
            String optionId,
            int scoreTotal,
            Map<String, Integer> scoreBreakdown,
            List<SolveOptionResult.ChangedDay> changedDays,
            int scheduleDayCount) {
    }

    public record ScheduleRangeResult(
            UUID versionId,
            String status,
            List<ScheduleDay> days) {
    }
}
