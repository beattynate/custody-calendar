package com.custodycalendar.api.domain.solver;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduleRun(
        LocalDate startDate,
        LocalDate endDate,
        UUID parentId,
        int lengthDays
) {
}