package com.custodycalendar.api.web.dto;

import com.custodycalendar.api.domain.model.MemberRole;
import java.util.UUID;

public record CaseMemberResponse(
        UUID personId,
        String externalSubject,
        String displayName,
        MemberRole role
) {
}
