package com.custodycalendar.api.domain.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import com.custodycalendar.api.config.SchoolNightProperties;
import com.custodycalendar.api.domain.model.SchoolDayType;
import org.springframework.stereotype.Component;

@Component
public class Scorer {

    private final SchoolNightProperties schoolNightProperties;

    public Scorer(SchoolNightProperties schoolNightProperties) {
        this.schoolNightProperties = schoolNightProperties;
    }

    public ScoreResult score(
            ScheduleAssignmentSet candidate,
            ScheduleAssignmentSet baseline,
            ScheduleAssignmentSet lockedBaseline,
            SolverWeights weights) {
        return score(candidate, baseline, lockedBaseline, weights, 0, Collections.emptySet());
    }

    public ScoreResult score(
            ScheduleAssignmentSet candidate,
            ScheduleAssignmentSet baseline,
            ScheduleAssignmentSet lockedBaseline,
            SolverWeights weights,
            int owedImbalanceDays,
            Set<LocalDate> transitionExcludedDates) {
        return score(candidate, baseline, lockedBaseline, weights, owedImbalanceDays, transitionExcludedDates, Collections.emptyMap());
    }

    public ScoreResult score(
            ScheduleAssignmentSet candidate,
            ScheduleAssignmentSet baseline,
            ScheduleAssignmentSet lockedBaseline,
            SolverWeights weights,
            int owedImbalanceDays,
            Set<LocalDate> transitionExcludedDates,
            Map<LocalDate, SchoolDayType> schoolDayTypes) {
        int transitions = 0;
        int schoolNightTransitions = 0;
        int parityDrift = 0;
        int lockedProximityChanges = 0;
        int runDaysOverThree = 0;

        UUID previous = null;
        int currentRunLength = 0;

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

            if (previous == null || previous.equals(candidateParent)) {
                currentRunLength++;
            } else {
                runDaysOverThree += Math.max(0, currentRunLength - 3);
                currentRunLength = 1;
            }

            if (previous != null
                    && !previous.equals(candidateParent)
                    && (transitionExcludedDates == null || !transitionExcludedDates.contains(date))) {
                transitions++;
                if (isSchoolNightTransition(date, schoolDayTypes)) {
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
        }
        runDaysOverThree += Math.max(0, currentRunLength - 3);

        Map<String, Integer> breakdown = new HashMap<>();
        Map<String, SolveOptionResult.ScoreComponent> details = new HashMap<>();
        putComponent(details, breakdown, "transitions", transitions, weights.transitionPenalty());
        putComponent(details, breakdown, "schoolNightTransitions", schoolNightTransitions, weights.schoolNightTransitionPenalty());
        putComponent(details, breakdown, "parityDrift", parityDrift, weights.parityDriftPenalty());
        putComponent(details, breakdown, "lockedProximity", lockedProximityChanges, weights.lockedProximityPenalty());
        putComponent(details, breakdown, "owedImbalance", owedImbalanceDays, weights.owedImbalancePenalty());
        putComponent(details, breakdown, "runDaysOverThree", runDaysOverThree, weights.runDaysOverThreePenalty());

        int total = breakdown.values().stream().mapToInt(Integer::intValue).sum();
        return new ScoreResult(total, Map.copyOf(breakdown), Map.copyOf(details));
    }

    /**
     * Prefers the case's own school calendar (a transition landing on a
     * SCHOOL day counts); the configured school-year window is only a
     * fallback for dates the calendar does not cover.
     */
    private boolean isSchoolNightTransition(LocalDate nextDate, Map<LocalDate, SchoolDayType> schoolDayTypes) {
        SchoolDayType dayType = schoolDayTypes == null ? null : schoolDayTypes.get(nextDate);
        if (dayType != null) {
            return dayType == SchoolDayType.SCHOOL;
        }
        return schoolNightProperties.isSchoolNight(nextDate);
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

    private void putComponent(
            Map<String, SolveOptionResult.ScoreComponent> details,
            Map<String, Integer> breakdown,
            String key,
            int count,
            int weight) {
        int score = count * weight;
        breakdown.put(key, score);
        details.put(key, new SolveOptionResult.ScoreComponent(key, count, weight, score));
    }

    public record ScoreResult(
            int total,
            Map<String, Integer> breakdown,
            Map<String, SolveOptionResult.ScoreComponent> details) {
    }
}
