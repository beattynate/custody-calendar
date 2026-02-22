package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.SchoolDayType;
import java.time.LocalDate;

public record SchoolCalendarDayResponse(
        LocalDate date,
        SchoolDayType dayType
) {
}
