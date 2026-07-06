package com.custodycalendar.api.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        LocalDate date,
        UUID fromParentId,
        UUID toParentId,
        Integer amountDays,
        String reasonType,
        String dayBucket,
        UUID eventId,
        UUID versionId,
        String notes) {
}
