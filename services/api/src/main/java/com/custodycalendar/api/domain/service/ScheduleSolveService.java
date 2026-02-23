package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.Event;
import com.custodycalendar.api.domain.model.LedgerDayBucket;
import com.custodycalendar.api.domain.model.LedgerEntry;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.model.SchoolCalendarDay;
import com.custodycalendar.api.domain.model.SchoolDayType;
import com.custodycalendar.api.domain.repository.EventRepository;
import com.custodycalendar.api.domain.repository.LedgerEntryRepository;
import com.custodycalendar.api.domain.repository.ScheduleRuleRepository;
import com.custodycalendar.api.domain.repository.SchoolCalendarDayRepository;
import com.custodycalendar.api.domain.solver.BaselineScheduleGenerator;
import com.custodycalendar.api.domain.solver.ConstraintValidationResult;
import com.custodycalendar.api.domain.solver.ConstraintValidator;
import com.custodycalendar.api.domain.solver.LockedEventApplier;
import com.custodycalendar.api.domain.solver.MoveGenerator;
import com.custodycalendar.api.domain.solver.ScheduleAssignmentSet;
import com.custodycalendar.api.domain.solver.ScheduleSolveCommand;
import com.custodycalendar.api.domain.solver.Scorer;
import com.custodycalendar.api.domain.solver.SolveComputationResult;
import com.custodycalendar.api.domain.solver.SolveOptionResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleSolveService {

    private final CaseService caseService;
    private final ScheduleRuleRepository scheduleRuleRepository;
    private final EventRepository eventRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final SchoolCalendarDayRepository schoolCalendarDayRepository;
    private final BaselineScheduleGenerator baselineScheduleGenerator;
    private final LockedEventApplier lockedEventApplier;
    private final ConstraintValidator constraintValidator;
    private final MoveGenerator moveGenerator;
    private final Scorer scorer;

    public ScheduleSolveService(
            CaseService caseService,
            ScheduleRuleRepository scheduleRuleRepository,
            EventRepository eventRepository,
            LedgerEntryRepository ledgerEntryRepository,
            SchoolCalendarDayRepository schoolCalendarDayRepository,
            BaselineScheduleGenerator baselineScheduleGenerator,
            LockedEventApplier lockedEventApplier,
            ConstraintValidator constraintValidator,
            MoveGenerator moveGenerator,
            Scorer scorer) {
        this.caseService = caseService;
        this.scheduleRuleRepository = scheduleRuleRepository;
        this.eventRepository = eventRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.schoolCalendarDayRepository = schoolCalendarDayRepository;
        this.baselineScheduleGenerator = baselineScheduleGenerator;
        this.lockedEventApplier = lockedEventApplier;
        this.constraintValidator = constraintValidator;
        this.moveGenerator = moveGenerator;
        this.scorer = scorer;
    }

    @Transactional(readOnly = true)
    public SolveComputationResult solveDetailed(UUID caseId, ScheduleSolveCommand command) {
        caseService.requireCase(caseId);
        validateCommand(command);

        ScheduleRule rule = scheduleRuleRepository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule rule is required before solving"));

        List<Event> events = eventRepository.findByCaseIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
                caseId, command.horizonEnd(), command.horizonStart());

        ScheduleAssignmentSet baseline = baselineScheduleGenerator.generate(rule, command.horizonStart(), command.horizonEnd());
        ScheduleAssignmentSet lockedBaseline = lockedEventApplier.applyLockedEvents(baseline, events);
        Map<LocalDate, SchoolDayType> schoolDayTypes = loadSchoolDayTypes(caseId, command.horizonStart(), command.horizonEnd());
        Map<LedgerBalanceKey, Integer> currentOwedBalances = loadCurrentOwedBalances(caseId, schoolDayTypes);

        if (command.newEvent() == null) {
            Scorer.ScoreResult score = scorer.score(
                    lockedBaseline,
                    baseline,
                    lockedBaseline,
                    command.weights(),
                    totalOwedDays(currentOwedBalances),
                    Set.of());
            SolveOptionResult option = new SolveOptionResult(
                    "A",
                    score.total(),
                    score.breakdown(),
                    score.details(),
                    List.of("Baseline only"),
                    diffChangedDays(baseline, lockedBaseline),
                    List.of(),
                    toOwedBalances(currentOwedBalances),
                    lockedBaseline);
            return new SolveComputationResult(List.of(option));
        }

        List<MoveGenerator.GeneratedCandidate> generatedCandidates = moveGenerator.generateCandidates(
                lockedBaseline,
                command.newEvent(),
                command.constraints());

        List<SolveOptionResult> validOptions = new ArrayList<>();
        int optionOrdinal = 0;
        for (MoveGenerator.GeneratedCandidate generated : generatedCandidates) {
            ConstraintValidationResult validation = constraintValidator.validate(
                    generated.assignments(),
                    lockedBaseline,
                    command.constraints());
            if (!validation.valid()) {
                if (command.newEvent() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Baseline violates constraints: " + String.join("; ", validation.violations()));
                }
                continue;
            }

            List<SolveOptionResult.ChangedDay> changedDays = diffChangedDays(lockedBaseline, generated.assignments());
            List<SolveOptionResult.LedgerImpact> ledgerImpact = buildLedgerImpact(
                    changedDays,
                    schoolDayTypes,
                    command.newEvent() == null ? null : command.newEvent().parentId());
            Map<LedgerBalanceKey, Integer> projectedOwedBalances = applyLedgerImpacts(currentOwedBalances, ledgerImpact);
            Scorer.ScoreResult score = scorer.score(
                    generated.assignments(),
                    baseline,
                    lockedBaseline,
                    command.weights(),
                    totalOwedDays(projectedOwedBalances),
                    transitionExcludedDates(generated.appliedEvent()));
            optionOrdinal++;
            validOptions.add(new SolveOptionResult(
                    String.valueOf((char) ('A' + (optionOrdinal - 1))),
                    score.total(),
                    score.breakdown(),
                    score.details(),
                    buildPatchOperations(generated.patchOperations(), lockedBaseline, generated.assignments()),
                    changedDays,
                    ledgerImpact,
                    toOwedBalances(projectedOwedBalances),
                    generated.assignments()));
        }

        List<SolveOptionResult> topOptions = validOptions.stream()
                .sorted(Comparator.comparingInt(SolveOptionResult::scoreTotal)
                        .thenComparing(SolveOptionResult::optionId))
                .limit(5)
                .toList();

        if (topOptions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid schedule options satisfy constraints");
        }

        return new SolveComputationResult(topOptions);
    }

    private void validateCommand(ScheduleSolveCommand command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solve request is required");
        }
        if (command.horizonStart() == null || command.horizonEnd() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horizon start and end are required");
        }
        if (command.horizonEnd().isBefore(command.horizonStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horizon end must be on or after start");
        }
        if (command.newEvent() != null && command.newEvent().parentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newEvent.parentId is required");
        }
    }

    private List<String> buildPatchOperations(
            List<String> generatedOperations,
            ScheduleAssignmentSet baseline,
            ScheduleAssignmentSet candidate) {
        List<String> patchOperations = new ArrayList<>(generatedOperations);
        List<SolveOptionResult.ChangedDay> changed = diffChangedDays(baseline, candidate);
        if (!changed.isEmpty()) {
            LocalDate first = changed.get(0).date();
            LocalDate last = changed.get(changed.size() - 1).date();
            patchOperations.add("Changed days " + first + ".." + last + " (" + changed.size() + " total)");
        }
        return patchOperations;
    }

    private List<SolveOptionResult.ChangedDay> diffChangedDays(ScheduleAssignmentSet baseline, ScheduleAssignmentSet candidate) {
        List<SolveOptionResult.ChangedDay> changedDays = new ArrayList<>();
        for (Map.Entry<LocalDate, ScheduleAssignmentSet.DayAssignment> entry : candidate.days().entrySet()) {
            ScheduleAssignmentSet.DayAssignment baseDay = baseline.get(entry.getKey());
            ScheduleAssignmentSet.DayAssignment candidateDay = entry.getValue();
            if (baseDay != null && !baseDay.assignedParentId().equals(candidateDay.assignedParentId())) {
                changedDays.add(new SolveOptionResult.ChangedDay(
                        entry.getKey(),
                        baseDay.assignedParentId(),
                        candidateDay.assignedParentId()));
            }
        }
        return changedDays;
    }

    private Map<LocalDate, SchoolDayType> loadSchoolDayTypes(UUID caseId, LocalDate from, LocalDate to) {
        Map<LocalDate, SchoolDayType> types = new HashMap<>();
        for (SchoolCalendarDay day : schoolCalendarDayRepository.findByIdCaseIdAndIdDateBetweenOrderByIdDateAsc(caseId, from, to)) {
            types.put(day.getId().getDate(), day.getDayType());
        }
        return types;
    }

    private Map<LedgerBalanceKey, Integer> loadCurrentOwedBalances(UUID caseId, Map<LocalDate, SchoolDayType> schoolDayTypes) {
        Map<LedgerBalanceKey, Integer> balances = new HashMap<>();
        for (LedgerEntry entry : ledgerEntryRepository.findByCaseIdOrderByDateAsc(caseId)) {
            LedgerDayBucket bucket = resolveBucket(entry.getDayBucket(), schoolDayTypes.get(entry.getDate()));
            if (bucket == null) {
                continue;
            }
            addDirectedAmount(balances, entry.getFromParentId(), entry.getToParentId(), bucket, entry.getAmountDays());
        }
        return balances;
    }

    private List<SolveOptionResult.LedgerImpact> buildLedgerImpact(
            List<SolveOptionResult.ChangedDay> changedDays,
            Map<LocalDate, SchoolDayType> schoolDayTypes,
            UUID requestedParentId) {
        Map<LedgerBalanceKey, Integer> aggregated = new HashMap<>();
        for (SolveOptionResult.ChangedDay changedDay : changedDays) {
            LedgerDayBucket bucket = resolveBucket(null, schoolDayTypes.get(changedDay.date()));
            if (bucket == null) {
                continue;
            }
            // Candidate receiver owes the baseline owner a makeup day of the same day-bucket type.
            addDirectedAmount(aggregated, changedDay.toParentId(), changedDay.fromParentId(), bucket, 1);
        }

        List<SolveOptionResult.LedgerImpact> impacts = new ArrayList<>();
        aggregated.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<LedgerBalanceKey, Integer> e) -> e.getKey().dayBucket().name())
                        .thenComparing(e -> e.getKey().fromParentId().toString())
                        .thenComparing(e -> e.getKey().toParentId().toString()))
                .forEach(entry -> {
                    if (entry.getValue() <= 0) {
                        return;
                    }
                    impacts.add(new SolveOptionResult.LedgerImpact(
                            entry.getKey().fromParentId(),
                            entry.getKey().toParentId(),
                            entry.getValue(),
                            ledgerReason(entry.getKey(), requestedParentId),
                            entry.getKey().dayBucket().name()));
                });
        return impacts;
    }

    private Map<LedgerBalanceKey, Integer> applyLedgerImpacts(
            Map<LedgerBalanceKey, Integer> currentBalances,
            List<SolveOptionResult.LedgerImpact> impacts) {
        Map<LedgerBalanceKey, Integer> projected = new HashMap<>(currentBalances);
        for (SolveOptionResult.LedgerImpact impact : impacts) {
            LedgerDayBucket bucket = impact.dayBucket() == null ? null : LedgerDayBucket.valueOf(impact.dayBucket());
            if (bucket == null) {
                continue;
            }
            addDirectedAmount(projected, impact.fromParentId(), impact.toParentId(), bucket, impact.amountDays());
        }
        return projected;
    }

    private int totalOwedDays(Map<LedgerBalanceKey, Integer> balances) {
        return balances.values().stream().mapToInt(Integer::intValue).sum();
    }

    private List<SolveOptionResult.OwedBalance> toOwedBalances(Map<LedgerBalanceKey, Integer> balances) {
        return balances.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator
                        .comparing((Map.Entry<LedgerBalanceKey, Integer> e) -> e.getKey().dayBucket().name())
                        .thenComparing(e -> e.getKey().fromParentId().toString())
                        .thenComparing(e -> e.getKey().toParentId().toString()))
                .map(entry -> new SolveOptionResult.OwedBalance(
                        entry.getKey().fromParentId(),
                        entry.getKey().toParentId(),
                        entry.getValue(),
                        entry.getKey().dayBucket().name()))
                .toList();
    }

    private LedgerDayBucket resolveBucket(LedgerDayBucket explicitBucket, SchoolDayType schoolDayType) {
        if (explicitBucket != null) {
            return explicitBucket;
        }
        if (schoolDayType == SchoolDayType.HOLIDAY) {
            return null;
        }
        if (schoolDayType == SchoolDayType.SCHOOL) {
            return LedgerDayBucket.SCHOOL;
        }
        return LedgerDayBucket.NON_SCHOOL;
    }

    private void addDirectedAmount(
            Map<LedgerBalanceKey, Integer> balances,
            UUID fromParentId,
            UUID toParentId,
            LedgerDayBucket dayBucket,
            int amount) {
        if (fromParentId == null || toParentId == null || fromParentId.equals(toParentId) || dayBucket == null || amount <= 0) {
            return;
        }
        LedgerBalanceKey key = new LedgerBalanceKey(fromParentId, toParentId, dayBucket);
        LedgerBalanceKey opposite = new LedgerBalanceKey(toParentId, fromParentId, dayBucket);

        int remaining = amount;
        Integer oppositeAmount = balances.get(opposite);
        if (oppositeAmount != null && oppositeAmount > 0) {
            int offset = Math.min(oppositeAmount, remaining);
            int updatedOpposite = oppositeAmount - offset;
            if (updatedOpposite == 0) {
                balances.remove(opposite);
            } else {
                balances.put(opposite, updatedOpposite);
            }
            remaining -= offset;
        }

        if (remaining <= 0) {
            return;
        }
        balances.merge(key, remaining, Integer::sum);
        if (balances.getOrDefault(key, 0) <= 0) {
            balances.remove(key);
        }
    }

    private Set<LocalDate> transitionExcludedDates(com.custodycalendar.api.domain.solver.RequestedScheduleEvent event) {
        if (event == null || event.startDate() == null || event.endDate() == null) {
            return Set.of();
        }
        Set<LocalDate> dates = new HashSet<>();
        for (LocalDate date = event.startDate(); !date.isAfter(event.endDate()); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private String ledgerReason(LedgerBalanceKey key, UUID requestedParentId) {
        if (requestedParentId == null) {
            return "VACATION_TAKE";
        }
        if (requestedParentId.equals(key.fromParentId())) {
            return "VACATION_TAKE";
        }
        if (requestedParentId.equals(key.toParentId())) {
            return "MAKEUP_GIVE";
        }
        return "VACATION_TAKE";
    }

    private record LedgerBalanceKey(UUID fromParentId, UUID toParentId, LedgerDayBucket dayBucket) {
    }
}
