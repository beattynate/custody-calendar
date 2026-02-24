package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.CustodyCase;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustodyCaseRepository extends JpaRepository<CustodyCase, UUID> {
    List<CustodyCase> findAllByIdIn(Collection<UUID> ids);
}
