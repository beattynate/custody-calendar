package com.custodycalendar.api.domain.solver;

import com.custodycalendar.api.domain.model.ScheduleDaySource;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BaselineScheduleGenerator {

    private static final boolean[] BASE_PATTERN_A_START = new boolean[] {
            true, true, false, false, true, true, true,
            false, false, true, true, false, false, false
    };

    private final ObjectMapper objectMapper;

    public BaselineScheduleGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ScheduleAssignmentSet generate(ScheduleRule rule, LocalDate horizonStart, LocalDate horizonEnd) {
        ScheduleAssignmentSet assignments = new ScheduleAssignmentSet();
        boolean anchorIsParentA = resolveAnchorParentA(rule);

        for (LocalDate date = horizonStart; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            long daysFromAnchor = java.time.temporal.ChronoUnit.DAYS.between(rule.getAnchorDate(), date);
            int patternIndex = Math.floorMod((int) daysFromAnchor, BASE_PATTERN_A_START.length);
            boolean isParentA = BASE_PATTERN_A_START[patternIndex];
            if (!anchorIsParentA) {
                isParentA = !isParentA;
            }
            UUID assigned = isParentA ? rule.getParentAId() : rule.getParentBId();
            assignments.put(date, new ScheduleAssignmentSet.DayAssignment(assigned, null, ScheduleDaySource.BASELINE));
        }

        return assignments;
    }

    private boolean resolveAnchorParentA(ScheduleRule rule) {
        try {
            JsonNode metadata = objectMapper.readTree(rule.getMetadata());
            String anchorParent = metadata.path("anchorParent").asText("A");
            return !"B".equalsIgnoreCase(anchorParent);
        } catch (Exception ex) {
            return true;
        }
    }
}