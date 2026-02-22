package com.custodycalendar.api.web.dto;

import java.util.List;

public record SolveScheduleResponse(
        List<SolveOptionResponse> options
) {
}