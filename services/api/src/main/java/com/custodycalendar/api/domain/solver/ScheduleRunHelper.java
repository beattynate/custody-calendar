package com.custodycalendar.api.domain.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ScheduleRunHelper {

    public List<ScheduleRun> deriveRuns(ScheduleAssignmentSet assignments) {
        List<ScheduleRun> runs = new ArrayList<>();
        LocalDate runStart = null;
        LocalDate previousDate = null;
        UUID currentParent = null;
        int length = 0;

        for (Map.Entry<LocalDate, ScheduleAssignmentSet.DayAssignment> entry : assignments.days().entrySet()) {
            LocalDate date = entry.getKey();
            UUID parent = entry.getValue().assignedParentId();
            if (currentParent == null || !currentParent.equals(parent)) {
                if (currentParent != null) {
                    runs.add(new ScheduleRun(runStart, previousDate, currentParent, length));
                }
                currentParent = parent;
                runStart = date;
                length = 1;
            } else {
                length++;
            }
            previousDate = date;
        }

        if (currentParent != null) {
            runs.add(new ScheduleRun(runStart, previousDate, currentParent, length));
        }

        return runs;
    }
}
