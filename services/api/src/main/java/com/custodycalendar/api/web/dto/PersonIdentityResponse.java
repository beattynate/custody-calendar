package com.custodycalendar.api.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PersonIdentityResponse(
        UUID id,
        String externalSubject,
        String label,
        OffsetDateTime createdAt,
        boolean primary,
        boolean current) {
}
