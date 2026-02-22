package com.custodycalendar.api.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record SolveScheduleRequest(
        UUID baseVersionId,
        @NotNull LocalDate horizonStart,
        @NotNull LocalDate horizonEnd,
        @Valid SolveNewEventRequest newEvent,
        @Valid SolveConstraintsRequest constraints,
        @Valid SolveWeightsRequest weights
) {
}
