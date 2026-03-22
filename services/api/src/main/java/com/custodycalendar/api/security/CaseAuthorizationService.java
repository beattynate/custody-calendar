package com.custodycalendar.api.security;

import com.custodycalendar.api.domain.service.CaseMembershipService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component("caseAccess")
public class CaseAuthorizationService {

    private static final String OVERRIDE_HEADER = "X-Subject-Override";

    private final CaseMembershipService caseMembershipService;

    @Value("${app.security.allow-subject-override:false}")
    private boolean allowSubjectOverride;

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

        if (allowSubjectOverride) {
            String override = getOverrideHeader();
            if (override != null) {
                return Optional.of(override);
            }
        }

        return Optional.of(subject);
    }

    private String getOverrideHeader() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String value = request.getHeader(OVERRIDE_HEADER);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
