package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.Event;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.repository.EventRepository;
import com.custodycalendar.api.domain.repository.ScheduleRuleRepository;
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
import java.util.List;
import java.util.Map;
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
    private final BaselineScheduleGenerator baselineScheduleGenerator;
    private final LockedEventApplier lockedEventApplier;
    private final ConstraintValidator constraintValidator;
    private final MoveGenerator moveGenerator;
    private final Scorer scorer;

    public ScheduleSolveService(
            CaseService caseService,
            ScheduleRuleRepository scheduleRuleRepository,
            EventRepository eventRepository,
            BaselineScheduleGenerator baselineScheduleGenerator,
            LockedEventApplier lockedEventApplier,
            ConstraintValidator constraintValidator,
            MoveGenerator moveGenerator,
            Scorer scorer) {
        this.caseService = caseService;
        this.scheduleRuleRepository = scheduleRuleRepository;
        this.eventRepository = eventRepository;
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
                continue;
            }

            Scorer.ScoreResult score = scorer.score(generated.assignments(), baseline, lockedBaseline, command.weights());
            optionOrdinal++;
            validOptions.add(new SolveOptionResult(
                    String.valueOf((char) ('A' + (optionOrdinal - 1))),
                    score.total(),
                    score.breakdown(),
                    buildPatchOperations(generated.patchOperations(), lockedBaseline, generated.assignments()),
                    diffChangedDays(lockedBaseline, generated.assignments()),
                    List.of(),
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
}
