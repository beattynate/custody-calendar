package com.custodycalendar.api.web.dto;

public record SolveConstraintsRequest(
        Integer minRunDays,
        Integer compensationWindowDays,
        Boolean respectLocked
) {
}