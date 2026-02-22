package com.custodycalendar.api.web.dto;

import java.util.UUID;

public record CaseResponse(
        UUID id,
        String name,
        String timezone
) {
}
