package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.Event;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByCaseIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
            UUID caseId, LocalDate to, LocalDate from);
}
