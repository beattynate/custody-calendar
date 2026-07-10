package com.custodycalendar.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkIdentityRequest(
        @NotBlank String externalSubject,
        String label) {
}
