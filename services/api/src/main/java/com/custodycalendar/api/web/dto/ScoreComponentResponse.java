package com.custodycalendar.api.web.dto;

public record ScoreComponentResponse(
        String key,
        int count,
        int weight,
        int score
) {
}
