package com.custodycalendar.api.domain.solver;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SolveOptionResult(
        String optionId,
        int scoreTotal,
        Map<String, Integer> scoreBreakdown,
        List<String> patchOperations,
        List<ChangedDay> changedDays,
        List<LedgerImpact> ledgerImpact,
        ScheduleAssignmentSet assignments
) {
    public record ChangedDay(LocalDate date, UUID fromParentId, UUID toParentId) {
    }

    public record LedgerImpact(UUID fromParentId, UUID toParentId, int amountDays, String reason) {
    }
}