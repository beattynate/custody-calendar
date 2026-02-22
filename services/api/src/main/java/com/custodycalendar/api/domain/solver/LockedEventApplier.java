package com.custodycalendar.api.domain.solver;

import com.custodycalendar.api.domain.model.Event;
import com.custodycalendar.api.domain.model.EventAppliesTo;
import com.custodycalendar.api.domain.model.ScheduleDaySource;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LockedEventApplier {

    public ScheduleAssignmentSet applyLockedEvents(ScheduleAssignmentSet baseline, List<Event> events) {
        ScheduleAssignmentSet result = baseline.copy();
        events.stream()
                .filter(Event::isLocked)
                .filter(e -> e.getAppliesTo() == EventAppliesTo.KIDS_ASSIGNMENT)
                .filter(e -> e.getParentId() != null)
                .sorted(Comparator.comparing(Event::getStartDate).thenComparing(Event::getId))
                .forEach(event -> applyLockedEvent(result, event));
        return result;
    }

    public ScheduleAssignmentSet applyRequestedEvent(
            ScheduleAssignmentSet source,
            RequestedScheduleEvent requestedEvent,
            boolean respectLocked) {
        ScheduleAssignmentSet result = source.copy();
        if (requestedEvent.parentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested event parentId is required for KIDS assignment");
        }
        if (requestedEvent.endDate().isBefore(requestedEvent.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested event end date must be on or after start date");
        }

        for (LocalDate date = requestedEvent.startDate(); !date.isAfter(requestedEvent.endDate()); date = date.plusDays(1)) {
            if (!result.containsDate(date)) {
                continue;
            }
            ScheduleAssignmentSet.DayAssignment existing = result.get(date);
            if (existing.isLocked() && respectLocked && !existing.assignedParentId().equals(requestedEvent.parentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested event conflicts with locked schedule");
            }
            if (!existing.isLocked() || !respectLocked) {
                result.put(date, new ScheduleAssignmentSet.DayAssignment(
                        requestedEvent.parentId(),
                        existing.lockedSourceEventId(),
                        ScheduleDaySource.OVERRIDE));
            }
        }
        return result;
    }

    private void applyLockedEvent(ScheduleAssignmentSet result, Event event) {
        for (LocalDate date = event.getStartDate(); !date.isAfter(event.getEndDate()); date = date.plusDays(1)) {
            if (!result.containsDate(date)) {
                continue;
            }
            ScheduleAssignmentSet.DayAssignment existing = result.get(date);
            if (existing.isLocked() && !existing.assignedParentId().equals(event.getParentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflicting locked events overlap");
            }
            result.put(date, existing.lockTo(event.getParentId(), event.getId()));
        }
    }
}