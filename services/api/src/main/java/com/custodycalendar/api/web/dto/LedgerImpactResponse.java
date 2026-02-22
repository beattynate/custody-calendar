package com.custodycalendar.api.web.dto;

import java.util.UUID;

public record LedgerImpactResponse(
        UUID fromParent,
        UUID toParent,
        int amountDays,
        String reason
) {
}