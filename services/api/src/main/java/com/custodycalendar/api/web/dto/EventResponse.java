package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.EventAppliesTo;
import com.custodycalendar.api.domain.model.EventType;
import java.time.LocalDate;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID caseId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        EventType eventType,
        EventAppliesTo appliesTo,
        UUID parentId,
        boolean locked,
        String recurrenceRule,
        String notes
) {
}
