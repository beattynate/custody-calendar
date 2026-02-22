package com.custodycalendar.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChildRequest(
        @NotBlank String name
) {
}