package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.SchoolCalendarDay;
import com.custodycalendar.api.domain.model.SchoolCalendarDayId;
import com.custodycalendar.api.domain.repository.SchoolCalendarDayRepository;
import com.custodycalendar.api.web.dto.SchoolCalendarBulkRequest;
import com.custodycalendar.api.web.dto.SchoolCalendarDayRequest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
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

    @Transactional
    public List<SchoolCalendarDay> bulkGenerate(UUID caseId, SchoolCalendarBulkRequest request) {
        caseService.requireCase(caseId);
        if (request.startDate() == null || request.endDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate/endDate are required");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }
        if (request.daysOfWeek() == null || request.daysOfWeek().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "daysOfWeek is required");
        }

        List<SchoolCalendarDay> days = new ArrayList<>();
        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (!request.daysOfWeek().contains(dow)) {
                continue;
            }
            SchoolCalendarDayId id = new SchoolCalendarDayId();
            id.setCaseId(caseId);
            id.setDate(date);

            SchoolCalendarDay day = new SchoolCalendarDay();
            day.setId(id);
            day.setDayType(request.dayType());
            days.add(day);
        }

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
