package com.custodycalendar.api.web;

import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.PersonIdentity;
import com.custodycalendar.api.domain.service.PersonDirectoryService;
import com.custodycalendar.api.security.AuthenticatedUserService;
import com.custodycalendar.api.security.AuthenticatedUserService.AuthenticatedUser;
import com.custodycalendar.api.web.dto.LinkIdentityRequest;
import com.custodycalendar.api.web.dto.MeResponse;
import com.custodycalendar.api.web.dto.PersonIdentityResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service endpoints for the authenticated person. Linked identities let
 * two logins (e.g. a parent and their partner) act as the same person; for
 * safety, identities can only ever be linked to or removed from yourself.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AuthenticatedUserService authenticatedUserService;
    private final PersonDirectoryService personDirectoryService;

    public MeController(
            AuthenticatedUserService authenticatedUserService,
            PersonDirectoryService personDirectoryService) {
        this.authenticatedUserService = authenticatedUserService;
        this.personDirectoryService = personDirectoryService;
    }

    @GetMapping
    public MeResponse getMe(Authentication authentication) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        Person person = personDirectoryService.requireBySubject(user.subject());
        return new MeResponse(
                person.getId(),
                person.getDisplayName(),
                user.subject(),
                toIdentityResponses(person, user.subject()));
    }

    @GetMapping("/identities")
    public List<PersonIdentityResponse> listIdentities(Authentication authentication) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        Person person = personDirectoryService.requireBySubject(user.subject());
        return toIdentityResponses(person, user.subject());
    }

    @PostMapping("/identities")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonIdentityResponse linkIdentity(
            Authentication authentication,
            @Valid @RequestBody LinkIdentityRequest request) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        Person person = personDirectoryService.requireBySubject(user.subject());
        PersonIdentity identity = personDirectoryService.linkIdentity(person.getId(), request.externalSubject(), request.label());
        return toIdentityResponse(identity, person, user.subject());
    }

    @DeleteMapping("/identities/{identityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeIdentity(Authentication authentication, @PathVariable UUID identityId) {
        AuthenticatedUser user = authenticatedUserService.require(authentication);
        Person person = personDirectoryService.requireBySubject(user.subject());
        personDirectoryService.removeIdentity(person.getId(), identityId, user.subject());
    }

    private List<PersonIdentityResponse> toIdentityResponses(Person person, String activeSubject) {
        return personDirectoryService.listIdentities(person.getId()).stream()
                .map(identity -> toIdentityResponse(identity, person, activeSubject))
                .toList();
    }

    private PersonIdentityResponse toIdentityResponse(PersonIdentity identity, Person person, String activeSubject) {
        return new PersonIdentityResponse(
                identity.getId(),
                identity.getExternalSubject(),
                identity.getLabel(),
                identity.getCreatedAt(),
                identity.getExternalSubject().equals(person.getExternalSubject()),
                identity.getExternalSubject().equals(activeSubject));
    }
}
