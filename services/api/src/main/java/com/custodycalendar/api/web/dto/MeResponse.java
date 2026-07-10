package com.custodycalendar.api.web.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID personId,
        String displayName,
        String activeSubject,
        List<PersonIdentityResponse> identities) {
}
