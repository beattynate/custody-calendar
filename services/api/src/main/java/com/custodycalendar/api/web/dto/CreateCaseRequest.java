package com.custodycalendar.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCaseRequest(
        @NotBlank String name,
        @NotBlank String timezone
) {
}
