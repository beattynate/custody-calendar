package com.custodycalendar.api.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        UUID actorPersonId,
        String actorName,
        JsonNode details,
        OffsetDateTime createdAt) {
}
