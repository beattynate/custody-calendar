package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.PersonIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonIdentityRepository extends JpaRepository<PersonIdentity, UUID> {
    Optional<PersonIdentity> findByExternalSubject(String externalSubject);

    List<PersonIdentity> findAllByPersonIdOrderByCreatedAtAsc(UUID personId);

    long countByPersonId(UUID personId);
}
