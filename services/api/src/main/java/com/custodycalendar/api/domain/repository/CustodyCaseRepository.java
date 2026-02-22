package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.CustodyCase;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustodyCaseRepository extends JpaRepository<CustodyCase, UUID> {
}
