package com.custodycalendar.api.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateScheduleProposalRequest(
        @NotBlank String optionId,
        @NotNull @Valid SolveScheduleRequest solveRequest,
        String reason
) {
}
