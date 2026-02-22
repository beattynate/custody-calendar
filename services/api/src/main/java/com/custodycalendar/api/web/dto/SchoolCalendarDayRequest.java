package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.SchoolDayType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SchoolCalendarDayRequest(
        @NotNull LocalDate date,
        @NotNull SchoolDayType dayType
) {
}
