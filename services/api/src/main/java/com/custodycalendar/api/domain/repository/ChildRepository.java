package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.Child;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, UUID> {
    List<Child> findAllByCaseIdOrderByNameAsc(UUID caseId);
}
