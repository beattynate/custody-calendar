package com.custodycalendar.api.domain.solver;

import com.custodycalendar.api.domain.model.ScheduleDaySource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

public class ScheduleAssignmentSet {

    private final NavigableMap<LocalDate, DayAssignment> days;

    public ScheduleAssignmentSet() {
        this.days = new TreeMap<>();
    }

    private ScheduleAssignmentSet(NavigableMap<LocalDate, DayAssignment> days) {
        this.days = days;
    }

    public ScheduleAssignmentSet copy() {
        return new ScheduleAssignmentSet(new TreeMap<>(days));
    }

    public void put(LocalDate date, DayAssignment assignment) {
        days.put(date, assignment);
    }

    public DayAssignment get(LocalDate date) {
        return days.get(date);
    }

    public boolean containsDate(LocalDate date) {
        return days.containsKey(date);
    }

    public NavigableMap<LocalDate, DayAssignment> days() {
        return days;
    }

    public LocalDate startDate() {
        return days.firstKey();
    }

    public LocalDate endDate() {
        return days.lastKey();
    }

    public String signature() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<LocalDate, DayAssignment> entry : days.entrySet()) {
            builder.append(entry.getKey()).append('|')
                    .append(entry.getValue().assignedParentId()).append('|')
                    .append(entry.getValue().lockedSourceEventId()).append(';');
        }
        return builder.toString();
    }

    public record DayAssignment(
            UUID assignedParentId,
            UUID lockedSourceEventId,
            ScheduleDaySource derivedFrom) {

        public DayAssignment withAssignedParentId(UUID assignedParentId, ScheduleDaySource source) {
            return new DayAssignment(assignedParentId, lockedSourceEventId, source);
        }

        public DayAssignment lockTo(UUID assignedParentId, UUID eventId) {
            return new DayAssignment(assignedParentId, eventId, ScheduleDaySource.OVERRIDE);
        }

        public boolean isLocked() {
            return lockedSourceEventId != null;
        }
    }
}