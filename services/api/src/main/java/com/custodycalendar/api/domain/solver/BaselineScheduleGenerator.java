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

    private static final boolean[] DEFAULT_PATTERN = new boolean[] {
            true, true, false, false, true, true, true,
            false, false, true, true, false, false, false
    };

    private final ObjectMapper objectMapper;

    public BaselineScheduleGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ScheduleAssignmentSet generate(ScheduleRule rule, LocalDate horizonStart, LocalDate horizonEnd) {
        ScheduleAssignmentSet assignments = new ScheduleAssignmentSet();
        JsonNode metadata = parseMetadata(rule);
        boolean anchorIsParentA = resolveAnchorParentA(metadata);
        boolean[] pattern = resolvePattern(metadata);

        for (LocalDate date = horizonStart; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            long daysFromAnchor = java.time.temporal.ChronoUnit.DAYS.between(rule.getAnchorDate(), date);
            int patternIndex = Math.floorMod((int) daysFromAnchor, pattern.length);
            boolean isParentA = pattern[patternIndex];
            if (!anchorIsParentA) {
                isParentA = !isParentA;
            }
            UUID assigned = isParentA ? rule.getParentAId() : rule.getParentBId();
            assignments.put(date, new ScheduleAssignmentSet.DayAssignment(assigned, null, ScheduleDaySource.BASELINE));
        }

        return assignments;
    }

    /**
     * Reads a custom pattern from metadata.pattern if present.
     * Format: string of 'A' and 'B' characters, e.g. "AABBAAABBAABBBB" for 2-2-3.
     * Falls back to the default 2-2-3 pattern.
     */
    private boolean[] resolvePattern(JsonNode metadata) {
        String patternStr = metadata.path("pattern").asText(null);
        if (patternStr == null || patternStr.isBlank()) {
            return DEFAULT_PATTERN;
        }
        patternStr = patternStr.toUpperCase();
        boolean[] pattern = new boolean[patternStr.length()];
        for (int i = 0; i < patternStr.length(); i++) {
            char c = patternStr.charAt(i);
            if (c != 'A' && c != 'B') {
                return DEFAULT_PATTERN;
            }
            pattern[i] = (c == 'A');
        }
        return pattern;
    }

    private JsonNode parseMetadata(ScheduleRule rule) {
        try {
            return objectMapper.readTree(rule.getMetadata());
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private boolean resolveAnchorParentA(JsonNode metadata) {
        String anchorParent = metadata.path("anchorParent").asText("A");
        return !"B".equalsIgnoreCase(anchorParent);
    }
}