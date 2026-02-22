package com.custodycalendar.api.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ChangedDayResponse(
        LocalDate date,
        UUID fromParent,
        UUID toParent
) {
}