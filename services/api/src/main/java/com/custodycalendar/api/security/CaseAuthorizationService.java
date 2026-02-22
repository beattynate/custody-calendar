package com.custodycalendar.api.security;

import com.custodycalendar.api.domain.service.CaseMembershipService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("caseAccess")
public class CaseAuthorizationService {

    private final CaseMembershipService caseMembershipService;

    public CaseAuthorizationService(CaseMembershipService caseMembershipService) {
        this.caseMembershipService = caseMembershipService;
    }

    public boolean canAccess(Authentication authentication, UUID caseId) {
        Optional<String> externalSubject = resolveExternalSubject(authentication);
        return externalSubject.isPresent()
                && caseMembershipService.isCaseMemberByExternalSubject(caseId, externalSubject.get());
    }

    private Optional<String> resolveExternalSubject(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(subject);
    }
}
