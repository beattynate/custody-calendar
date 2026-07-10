package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.PersonIdentity;
import com.custodycalendar.api.domain.repository.PersonIdentityRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves external identity-provider subjects to Person records.
 *
 * A person can have multiple linked identities (e.g. a parent and their
 * partner each signing in with their own credentials but acting as the same
 * parent in every case). All subject lookups must go through this service
 * rather than Person.externalSubject, which only stores the primary subject.
 */
@Service
public class PersonDirectoryService {

    private final PersonRepository personRepository;
    private final PersonIdentityRepository personIdentityRepository;

    public PersonDirectoryService(
            PersonRepository personRepository,
            PersonIdentityRepository personIdentityRepository) {
        this.personRepository = personRepository;
        this.personIdentityRepository = personIdentityRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Person> findBySubject(String externalSubject) {
        if (externalSubject == null || externalSubject.isBlank()) {
            return Optional.empty();
        }
        String subject = externalSubject.trim();
        // Fall back to the primary subject column for person rows created
        // outside ensurePerson (e.g. seed scripts); write paths self-heal the
        // missing identity row via ensurePerson.
        return personIdentityRepository.findByExternalSubject(subject)
                .flatMap(identity -> personRepository.findById(identity.getPersonId()))
                .or(() -> personRepository.findByExternalSubject(subject));
    }

    @Transactional(readOnly = true)
    public Person requireBySubject(String externalSubject) {
        return findBySubject(externalSubject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authenticated person record not found"));
    }

    /**
     * Finds the person for a subject, creating the person and its primary
     * identity when the subject is unknown. Updates the display name when a
     * non-blank one is provided.
     */
    @Transactional
    public Person ensurePerson(String externalSubject, String displayName) {
        String subject = externalSubject.trim();
        Person person = findBySubject(subject).orElseGet(() -> {
            Person created = new Person();
            created.setId(UUID.randomUUID());
            created.setExternalSubject(subject);
            created.setDisplayName(subject);
            return created;
        });
        if (displayName != null && !displayName.isBlank()) {
            person.setDisplayName(displayName.trim());
        }
        person = personRepository.save(person);

        if (personIdentityRepository.findByExternalSubject(subject).isEmpty()) {
            personIdentityRepository.save(newIdentity(person.getId(), subject, "Primary"));
        }
        return person;
    }

    @Transactional(readOnly = true)
    public List<PersonIdentity> listIdentities(UUID personId) {
        return personIdentityRepository.findAllByPersonIdOrderByCreatedAtAsc(personId);
    }

    /**
     * Links an additional sign-in subject to the given person. The caller is
     * responsible for ensuring the person is the authenticated actor; a
     * subject already linked to a different person is rejected.
     */
    @Transactional
    public PersonIdentity linkIdentity(UUID personId, String externalSubject, String label) {
        if (externalSubject == null || externalSubject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "externalSubject is required");
        }
        String subject = externalSubject.trim();

        Optional<PersonIdentity> existing = personIdentityRepository.findByExternalSubject(subject);
        if (existing.isPresent()) {
            if (existing.get().getPersonId().equals(personId)) {
                return existing.get();
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This login is already associated with another person");
        }

        return personIdentityRepository.save(newIdentity(personId, subject, trimToNull(label)));
    }

    /**
     * Unlinks an identity from the given person. The primary identity (the
     * person's original subject) and the identity currently used to
     * authenticate cannot be removed.
     */
    @Transactional
    public void removeIdentity(UUID personId, UUID identityId, String currentSubject) {
        PersonIdentity identity = personIdentityRepository.findById(identityId)
                .filter(found -> found.getPersonId().equals(personId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found"));

        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found"));
        if (identity.getExternalSubject().equals(person.getExternalSubject())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The primary login cannot be removed");
        }
        if (currentSubject != null && identity.getExternalSubject().equals(currentSubject.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The login you are currently signed in with cannot be removed");
        }

        personIdentityRepository.delete(identity);
    }

    private PersonIdentity newIdentity(UUID personId, String subject, String label) {
        PersonIdentity identity = new PersonIdentity();
        identity.setId(UUID.randomUUID());
        identity.setPersonId(personId);
        identity.setExternalSubject(subject);
        identity.setLabel(label);
        identity.setCreatedAt(OffsetDateTime.now());
        return identity;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
