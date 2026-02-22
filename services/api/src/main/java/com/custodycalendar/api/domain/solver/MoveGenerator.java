package com.custodycalendar.api.domain.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MoveGenerator {

    private final LockedEventApplier lockedEventApplier;

    public MoveGenerator(LockedEventApplier lockedEventApplier) {
        this.lockedEventApplier = lockedEventApplier;
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
        }

        return generated;
    }

    public record GeneratedCandidate(
            ScheduleAssignmentSet assignments,
            List<String> patchOperations,
            RequestedScheduleEvent appliedEvent) {
    }
}
