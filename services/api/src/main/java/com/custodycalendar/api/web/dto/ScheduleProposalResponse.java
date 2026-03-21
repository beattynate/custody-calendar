package com.custodycalendar.api.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ScheduleProposalResponse(
        UUID proposalId,
        String status,
        UUID createdBy,
        String createdByName,
        OffsetDateTime createdAt,
        String optionId,
        String reason,
        UUID acceptedVersionId,
        JsonNode optionSnapshot,
        List<ScheduleProposalApprovalResponse> approvals
) {
}
