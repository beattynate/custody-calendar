package com.custodycalendar.api.web.dto;

import java.util.UUID;

public record OwedBalanceResponse(
        UUID fromParent,
        UUID toParent,
        int amountDays,
        String dayBucket
) {
}
