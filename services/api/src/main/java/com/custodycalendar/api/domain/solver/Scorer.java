package com.custodycalendar.api.domain.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class Scorer {

    private static final LocalDate SCHOOL_START = LocalDate.of(2025, 8, 4);
    private static final LocalDate SCHOOL_END = LocalDate.of(2026, 5, 25);

    public ScoreResult score(
            ScheduleAssignmentSet candidate,
            ScheduleAssignmentSet baseline,
            ScheduleAssignmentSet lockedBaseline,
            SolverWeights weights) {
        int transitions = 0;
        int schoolNightTransitions = 0;
        int parityDrift = 0;
        int lockedProximityChanges = 0;
        int owedImbalanceDays;

        UUID previous = null;
        LocalDate previousDate = null;
        int candidateParentADays = 0;
        int baselineParentADays = 0;
        UUID baselineParentA = baseline.days().firstEntry().getValue().assignedParentId();

        Set<LocalDate> lockedDays = new HashSet<>();
        for (var entry : lockedBaseline.days().entrySet()) {
            if (entry.getValue().isLocked()) {
                lockedDays.add(entry.getKey());
            }
        }

        for (var entry : candidate.days().entrySet()) {
            LocalDate date = entry.getKey();
            UUID candidateParent = entry.getValue().assignedParentId();
            UUID baselineParent = baseline.get(date).assignedParentId();
            if (candidateParent.equals(baselineParentA)) {
                candidateParentADays++;
            }
            if (baselineParent.equals(baselineParentA)) {
                baselineParentADays++;
            }

            if (previous != null && !previous.equals(candidateParent)) {
                transitions++;
                if (isSchoolNightTransition(date)) {
                    schoolNightTransitions++;
                }
            }

            if (isWeekendAnchor(date) && !candidateParent.equals(baselineParent)) {
                parityDrift++;
            }

            if (!candidateParent.equals(baselineParent) && nearLockedDay(date, lockedDays)) {
                lockedProximityChanges++;
            }

            previous = candidateParent;
            previousDate = date;
        }

        owedImbalanceDays = Math.abs(candidateParentADays - baselineParentADays);

        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("transitions", transitions * weights.transitionPenalty());
        breakdown.put("schoolNightTransitions", schoolNightTransitions * weights.schoolNightTransitionPenalty());
        breakdown.put("parityDrift", parityDrift * weights.parityDriftPenalty());
        breakdown.put("lockedProximity", lockedProximityChanges * weights.lockedProximityPenalty());
        breakdown.put("owedImbalance", owedImbalanceDays * weights.owedImbalancePenalty());

        int total = breakdown.values().stream().mapToInt(Integer::intValue).sum();
        return new ScoreResult(total, Map.copyOf(breakdown));
    }

    private boolean isSchoolNightTransition(LocalDate nextDate) {
        if (nextDate.isBefore(SCHOOL_START) || nextDate.isAfter(SCHOOL_END)) {
            return false;
        }
        DayOfWeek next = nextDate.getDayOfWeek();
        return next == DayOfWeek.MONDAY
                || next == DayOfWeek.TUESDAY
                || next == DayOfWeek.WEDNESDAY
                || next == DayOfWeek.THURSDAY;
    }

    private boolean isWeekendAnchor(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.FRIDAY;
    }

    private boolean nearLockedDay(LocalDate date, Set<LocalDate> lockedDays) {
        for (int offset = -2; offset <= 2; offset++) {
            if (lockedDays.contains(date.plusDays(offset))) {
                return true;
            }
        }
        return false;
    }

    public record ScoreResult(int total, Map<String, Integer> breakdown) {
    }
}
