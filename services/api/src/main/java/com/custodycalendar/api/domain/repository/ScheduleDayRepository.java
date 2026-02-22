package com.custodycalendar.api.domain.repository;

import com.custodycalendar.api.domain.model.ScheduleDay;
import com.custodycalendar.api.domain.model.ScheduleDayId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleDayRepository extends JpaRepository<ScheduleDay, ScheduleDayId> {
    List<ScheduleDay> findByIdVersionIdAndIdDateBetweenOrderByIdDateAsc(UUID versionId, LocalDate from, LocalDate to);
}
