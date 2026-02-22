package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.Person;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, UUID> {
    Optional<Person> findByExternalSubject(String externalSubject);
}
