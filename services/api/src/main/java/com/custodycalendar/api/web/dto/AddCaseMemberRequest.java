package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddCaseMemberRequest(
        @NotBlank String externalSubject,
        @NotBlank String displayName,
        @NotNull MemberRole role
) {
}
