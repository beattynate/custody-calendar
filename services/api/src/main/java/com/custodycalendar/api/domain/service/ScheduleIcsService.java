package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.ScheduleDay;
import com.custodycalendar.api.domain.repository.PersonRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders the schedule (accepted version, or baseline fallback) as an iCalendar
 * file with one all-day event per contiguous custody run, importable into
 * Google/Apple/Outlook calendars.
 */
@Service
public class ScheduleIcsService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ScheduleVersionService scheduleVersionService;
    private final PersonRepository personRepository;

    public ScheduleIcsService(ScheduleVersionService scheduleVersionService, PersonRepository personRepository) {
        this.scheduleVersionService = scheduleVersionService;
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public String buildIcs(UUID caseId, LocalDate from, LocalDate to) {
        List<ScheduleDay> days = new ArrayList<>(scheduleVersionService.getAcceptedSchedule(caseId, from, to).days());
        days.sort(Comparator.comparing(day -> day.getId().getDate()));

        Map<UUID, Person> people = personRepository.findAllById(
                        days.stream().map(ScheduleDay::getAssignedParentId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));

        String stamp = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        StringBuilder ics = new StringBuilder();
        appendLine(ics, "BEGIN:VCALENDAR");
        appendLine(ics, "VERSION:2.0");
        appendLine(ics, "PRODID:-//custody-calendar//schedule//EN");
        appendLine(ics, "CALSCALE:GREGORIAN");
        appendLine(ics, "X-WR-CALNAME:Custody Calendar");

        int index = 0;
        UUID runParent = null;
        LocalDate runStart = null;
        LocalDate previous = null;
        for (ScheduleDay day : days) {
            LocalDate date = day.getId().getDate();
            UUID parent = day.getAssignedParentId();
            boolean continues = parent.equals(runParent) && previous != null && date.equals(previous.plusDays(1));
            if (!continues) {
                if (runParent != null) {
                    appendEvent(ics, caseId, ++index, runStart, previous, personName(people, runParent), stamp);
                }
                runParent = parent;
                runStart = date;
            }
            previous = date;
        }
        if (runParent != null) {
            appendEvent(ics, caseId, ++index, runStart, previous, personName(people, runParent), stamp);
        }

        appendLine(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private void appendEvent(StringBuilder ics, UUID caseId, int index, LocalDate start, LocalDate end, String parentName, String stamp) {
        appendLine(ics, "BEGIN:VEVENT");
        appendLine(ics, "UID:" + caseId + "-" + start.format(DATE) + "-" + index + "@custody-calendar");
        appendLine(ics, "DTSTAMP:" + stamp);
        appendLine(ics, "DTSTART;VALUE=DATE:" + start.format(DATE));
        appendLine(ics, "DTEND;VALUE=DATE:" + end.plusDays(1).format(DATE));
        appendLine(ics, "SUMMARY:" + escapeText("Kids with " + parentName));
        appendLine(ics, "TRANSP:TRANSPARENT");
        appendLine(ics, "END:VEVENT");
    }

    private String personName(Map<UUID, Person> people, UUID personId) {
        Person person = people.get(personId);
        return person == null ? "parent" : person.getDisplayName();
    }

    private String escapeText(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    private void appendLine(StringBuilder ics, String line) {
        ics.append(line).append("\r\n");
    }
}
