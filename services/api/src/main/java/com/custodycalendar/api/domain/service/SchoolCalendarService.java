package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.SchoolCalendarDay;
import com.custodycalendar.api.domain.model.SchoolCalendarDayId;
import com.custodycalendar.api.domain.repository.SchoolCalendarDayRepository;
import com.custodycalendar.api.web.dto.SchoolCalendarDayRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SchoolCalendarService {

    private final CaseService caseService;
    private final SchoolCalendarDayRepository schoolCalendarDayRepository;

    public SchoolCalendarService(
            CaseService caseService,
            SchoolCalendarDayRepository schoolCalendarDayRepository) {
        this.caseService = caseService;
        this.schoolCalendarDayRepository = schoolCalendarDayRepository;
    }

    @Transactional
    public List<SchoolCalendarDay> upsertDays(UUID caseId, List<SchoolCalendarDayRequest> requests) {
        caseService.requireCase(caseId);
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one school day is required");
        }
        List<SchoolCalendarDay> days = requests.stream()
                .map(request -> {
                    SchoolCalendarDayId id = new SchoolCalendarDayId();
                    id.setCaseId(caseId);
                    id.setDate(request.date());

                    SchoolCalendarDay day = new SchoolCalendarDay();
                    day.setId(id);
                    day.setDayType(request.dayType());
                    return day;
                })
                .toList();
        return schoolCalendarDayRepository.saveAll(days);
    }

    @Transactional(readOnly = true)
    public List<SchoolCalendarDay> listDays(UUID caseId, LocalDate from, LocalDate to) {
        caseService.requireCase(caseId);
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from/to query params are required");
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
        }
        return schoolCalendarDayRepository.findByIdCaseIdAndIdDateBetweenOrderByIdDateAsc(caseId, from, to);
    }
}
