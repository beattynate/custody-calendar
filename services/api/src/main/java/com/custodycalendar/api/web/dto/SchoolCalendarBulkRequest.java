package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.SchoolDayType;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record SchoolCalendarBulkRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull SchoolDayType dayType,
        @NotNull List<DayOfWeek> daysOfWeek
) {
}
