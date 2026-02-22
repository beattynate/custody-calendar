package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.ScheduleRuleType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record UpsertScheduleRuleRequest(
        @NotNull ScheduleRuleType type,
        @NotNull LocalDate anchorDate,
        @NotNull UUID parentAId,
        @NotNull UUID parentBId,
        @NotNull JsonNode metadata
) {
}