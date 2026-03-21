package com.custodycalendar.api.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ScheduleProposalApprovalResponse(
        UUID personId,
        String displayName,
        String decision,
        OffsetDateTime decidedAt,
        String comment
) {
}
