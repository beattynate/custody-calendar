package com.custodycalendar.api.domain.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MoveGenerator {

    private final LockedEventApplier lockedEventApplier;
    private final ScheduleRunHelper scheduleRunHelper;

    public MoveGenerator(LockedEventApplier lockedEventApplier, ScheduleRunHelper scheduleRunHelper) {
        this.lockedEventApplier = lockedEventApplier;
        this.scheduleRunHelper = scheduleRunHelper;
    }

    public List<GeneratedCandidate> generateCandidates(
            ScheduleAssignmentSet lockedBaseline,
            RequestedScheduleEvent requestedEvent,
            SolverConstraints constraints) {
        if (requestedEvent == null) {
            return List.of(new GeneratedCandidate(
                    lockedBaseline,
                    List.of("Baseline only"),
                    null));
        }
        List<GeneratedCandidate> generated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        int[] shifts = new int[] {0, -1, 1, -2, 2};
        for (int startShift : shifts) {
            for (int endShift : shifts) {
                LocalDate shiftedStart = requestedEvent.startDate().plusDays(startShift);
                LocalDate shiftedEnd = requestedEvent.endDate().plusDays(endShift);
                if (shiftedEnd.isBefore(shiftedStart)) {
                    continue;
                }

                RequestedScheduleEvent variant = new RequestedScheduleEvent(
                        requestedEvent.title(),
                        shiftedStart,
                        shiftedEnd,
                        requestedEvent.parentId(),
                        requestedEvent.locked());
                ScheduleAssignmentSet candidate = lockedEventApplier.applyRequestedEvent(
                        lockedBaseline,
                        variant,
                        constraints.respectLocked());
                String signature = candidate.signature();
                if (seen.add(signature)) {
                    generated.add(new GeneratedCandidate(
                            candidate,
                            List.of("Shift vacation window by start=" + startShift + ", end=" + endShift),
                            variant));
                }

                addOneDayRunRepairs(candidate, seen, generated, variant);
            }
        }

        // Compensation style deterministic moves: give back a small block after the event.
        for (int length : new int[] {2, 3}) {
            ScheduleAssignmentSet candidate = lockedEventApplier.applyRequestedEvent(
                    lockedBaseline,
                    requestedEvent,
                    constraints.respectLocked());
            LocalDate start = requestedEvent.endDate().plusDays(1);
            int flipped = 0;
            List<LocalDate> changed = new ArrayList<>();
            for (LocalDate date = start; !date.isAfter(candidate.endDate()) && flipped < length; date = date.plusDays(1)) {
                if (!candidate.containsDate(date)) {
                    continue;
                }
                var day = candidate.get(date);
                if (!day.isLocked() && !day.assignedParentId().equals(requestedEvent.parentId())) {
                    candidate.put(date, day.withAssignedParentId(requestedEvent.parentId(), day.derivedFrom()));
                    changed.add(date);
                    flipped++;
                }
            }
            if (!changed.isEmpty()) {
                String signature = candidate.signature();
                if (seen.add(signature)) {
                    generated.add(new GeneratedCandidate(
                            candidate,
                            List.of("Compensation insertion after vacation for " + changed.size() + " days"),
                            requestedEvent));
                }
            }

            addOneDayRunRepairs(candidate, seen, generated, requestedEvent);
        }

        return generated;
    }

    private void addOneDayRunRepairs(
            ScheduleAssignmentSet candidate,
            Set<String> seen,
            List<GeneratedCandidate> generated,
            RequestedScheduleEvent requestedEvent) {
        LocalDate horizonStart = candidate.days().isEmpty() ? null : candidate.days().firstKey();
        LocalDate horizonEnd = candidate.days().isEmpty() ? null : candidate.days().lastKey();
        for (ScheduleRun run : scheduleRunHelper.deriveRuns(candidate)) {
            if (run.lengthDays() != 1) {
                continue;
            }
            LocalDate date = run.startDate();
            if ((horizonStart != null && date.equals(horizonStart))
                    || (horizonEnd != null && date.equals(horizonEnd))) {
                continue;
            }
            ScheduleAssignmentSet.DayAssignment current = candidate.get(date);
            if (current == null || current.isLocked()) {
                continue;
            }

            UUID replacement = null;
            LocalDate prev = date.minusDays(1);
            LocalDate next = date.plusDays(1);
            if (candidate.containsDate(prev)) {
                replacement = candidate.get(prev).assignedParentId();
            } else if (candidate.containsDate(next)) {
                replacement = candidate.get(next).assignedParentId();
            }

            if (replacement == null || replacement.equals(current.assignedParentId())) {
                continue;
            }

            ScheduleAssignmentSet repaired = candidate.copy();
            repaired.put(date, current.withAssignedParentId(replacement, current.derivedFrom()));
            String signature = repaired.signature();
            if (seen.add(signature)) {
                generated.add(new GeneratedCandidate(
                        repaired,
                        List.of("Repair single-day run on " + date),
                        requestedEvent));
            }
        }
    }

    public record GeneratedCandidate(
            ScheduleAssignmentSet assignments,
            List<String> patchOperations,
            RequestedScheduleEvent appliedEvent) {
    }
}
