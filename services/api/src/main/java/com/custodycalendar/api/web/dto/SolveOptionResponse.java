package com.custodycalendar.api.web.dto;

import java.util.List;
import java.util.Map;

public record SolveOptionResponse(
        String optionId,
        int scoreTotal,
        Map<String, Integer> scoreBreakdown,
        List<String> patchOperations,
        List<ChangedDayResponse> changedDays,
        List<LedgerImpactResponse> ledgerImpact
) {
}