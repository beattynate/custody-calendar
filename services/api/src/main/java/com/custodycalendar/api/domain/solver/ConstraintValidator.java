package com.custodycalendar.api.domain.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConstraintValidator {

    private final ScheduleRunHelper runHelper;

    public ConstraintValidator(ScheduleRunHelper runHelper) {
        this.runHelper = runHelper;
    }

    public ConstraintValidationResult validate(
            ScheduleAssignmentSet candidate,
            ScheduleAssignmentSet lockedBaseline,
            SolverConstraints constraints) {
        List<String> violations = new ArrayList<>();

        for (Map.Entry<LocalDate, ScheduleAssignmentSet.DayAssignment> entry : candidate.days().entrySet()) {
            if (entry.getValue() == null || entry.getValue().assignedParentId() == null) {
                violations.add("Unassigned day: " + entry.getKey());
            }
        }

        if (constraints.respectLocked()) {
            for (Map.Entry<LocalDate, ScheduleAssignmentSet.DayAssignment> entry : lockedBaseline.days().entrySet()) {
                ScheduleAssignmentSet.DayAssignment baselineDay = entry.getValue();
                if (baselineDay.isLocked()) {
                    ScheduleAssignmentSet.DayAssignment candidateDay = candidate.get(entry.getKey());
                    if (candidateDay == null
                            || !baselineDay.assignedParentId().equals(candidateDay.assignedParentId())
                            || !baselineDay.lockedSourceEventId().equals(candidateDay.lockedSourceEventId())) {
                        violations.add("Locked day changed: " + entry.getKey());
                    }
                }
            }
        }

        for (ScheduleRun run : runHelper.deriveRuns(candidate)) {
            if (run.lengthDays() < constraints.minRunDays()) {
                violations.add("Run shorter than minRunDays: " + run.startDate() + ".." + run.endDate());
            }
        }

        return new ConstraintValidationResult(violations.isEmpty(), List.copyOf(violations));
    }
}