package com.custodycalendar.api.web;

import com.custodycalendar.api.domain.model.CustodyCase;
import com.custodycalendar.api.domain.service.CaseService;
import com.custodycalendar.api.security.AuthenticatedUserService;
import com.custodycalendar.api.web.dto.CaseResponse;
import com.custodycalendar.api.web.dto.CreateCaseRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;
    private final AuthenticatedUserService authenticatedUserService;

    public CaseController(CaseService caseService, AuthenticatedUserService authenticatedUserService) {
        this.caseService = caseService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse createCase(@Valid @RequestBody CreateCaseRequest request, Authentication authentication) {
        var user = authenticatedUserService.require(authentication);
        CustodyCase created = caseService.createCase(request.name(), request.timezone(), user.subject(), user.displayName());
        return new CaseResponse(created.getId(), created.getName(), created.getTimezone());
    }

    @GetMapping("/{caseId}")
    @PreAuthorize("@caseAccess.canAccess(authentication, #caseId)")
    public CaseResponse getCase(@PathVariable UUID caseId) {
        CustodyCase custodyCase = caseService.getCase(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));

        return new CaseResponse(custodyCase.getId(), custodyCase.getName(), custodyCase.getTimezone());
    }
}
