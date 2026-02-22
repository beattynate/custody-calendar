package com.custodycalendar.api.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AcceptSolveOptionResponse(
        UUID versionId,
        String status,
        String optionId,
        int scoreTotal,
        Map<String, Integer> scoreBreakdown,
        List<ChangedDayResponse> changedDays,
        int scheduleDayCount
) {
}
