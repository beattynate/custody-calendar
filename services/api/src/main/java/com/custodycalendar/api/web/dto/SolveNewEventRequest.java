package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.EventAppliesTo;
import com.custodycalendar.api.domain.model.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record SolveNewEventRequest(
        @NotBlank String title,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull EventType eventType,
        @NotNull EventAppliesTo appliesTo,
        @NotNull UUID parentId,
        boolean locked,
        String recurrenceRule,
        String notes
) {
}
