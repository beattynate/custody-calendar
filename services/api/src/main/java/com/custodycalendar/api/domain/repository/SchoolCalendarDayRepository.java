package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.SchoolCalendarDay;
import com.custodycalendar.api.domain.model.SchoolCalendarDayId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolCalendarDayRepository extends JpaRepository<SchoolCalendarDay, SchoolCalendarDayId> {
    List<SchoolCalendarDay> findByIdCaseIdAndIdDateBetweenOrderByIdDateAsc(UUID caseId, LocalDate from, LocalDate to);
}
