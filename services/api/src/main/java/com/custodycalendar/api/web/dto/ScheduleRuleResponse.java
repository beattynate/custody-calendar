package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.ScheduleRuleType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleRuleResponse(
        UUID id,
        UUID caseId,
        ScheduleRuleType type,
        LocalDate anchorDate,
        UUID parentAId,
        UUID parentBId,
        JsonNode metadata
) {
}
