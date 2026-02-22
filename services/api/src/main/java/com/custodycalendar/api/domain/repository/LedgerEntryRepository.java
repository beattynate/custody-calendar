package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.LedgerEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
}
