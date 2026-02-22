package com.custodycalendar.api.web.dto;

import java.util.UUID;

public record ChildResponse(
        UUID id,
        String name
) {
}