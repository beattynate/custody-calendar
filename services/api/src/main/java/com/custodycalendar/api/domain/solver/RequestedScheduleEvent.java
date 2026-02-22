package com.custodycalendar.api.domain.solver;

import java.time.LocalDate;
import java.util.UUID;

public record RequestedScheduleEvent(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        UUID parentId,
        boolean locked
) {
}