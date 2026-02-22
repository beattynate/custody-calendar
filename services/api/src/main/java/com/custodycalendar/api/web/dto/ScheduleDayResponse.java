package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.ScheduleDaySource;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleDayResponse(
        LocalDate date,
        UUID assignedParentId,
        UUID lockedSourceEventId,
        ScheduleDaySource derivedFrom
) {
}
