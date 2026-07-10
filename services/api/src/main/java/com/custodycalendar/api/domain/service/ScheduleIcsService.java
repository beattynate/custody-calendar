package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.CustodyCase;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.ScheduleDay;
import com.custodycalendar.api.domain.repository.CustodyCaseRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Renders the schedule (accepted version, or baseline fallback) as an iCalendar
 * file with one all-day event per contiguous custody run, importable into
 * Google/Apple/Outlook calendars.
 */
@Service
public class ScheduleIcsService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ScheduleVersionService scheduleVersionService;
    private final PersonRepository personRepository;
    private final CustodyCaseRepository custodyCaseRepository;
    private final AuditLogService auditLogService;

    public ScheduleIcsService(
            ScheduleVersionService scheduleVersionService,
            PersonRepository personRepository,
            CustodyCaseRepository custodyCaseRepository,
            AuditLogService auditLogService) {
        this.scheduleVersionService = scheduleVersionService;
        this.personRepository = personRepository;
        this.custodyCaseRepository = custodyCaseRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * The feed token is a capability: anyone holding the URL can read the
     * schedule (calendar apps cannot send JWT headers). Rotating replaces the
     * token and invalidates previously shared feed URLs.
     */
    @Transactional
    public String rotateFeedToken(UUID caseId, UUID actorPersonId) {
        CustodyCase custodyCase = custodyCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        custodyCase.setIcsFeedToken(HexFormat.of().formatHex(bytes));
        custodyCaseRepository.save(custodyCase);
        auditLogService.record(caseId, actorPersonId, "ICS_FEED_ROTATED", "CASE", caseId, null);
        return feedPath(caseId, custodyCase.getIcsFeedToken());
    }

    @Transactional(readOnly = true)
    public String getFeedPath(UUID caseId) {
        CustodyCase custodyCase = custodyCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        if (custodyCase.getIcsFeedToken() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No feed token yet");
        }
        return feedPath(caseId, custodyCase.getIcsFeedToken());
    }

    /** Unauthenticated feed access; a rolling window keeps subscriptions fresh. */
    @Transactional(readOnly = true)
    public String buildFeedIcs(UUID caseId, String token) {
        CustodyCase custodyCase = custodyCaseRepository.findById(caseId)
                .filter(found -> found.getIcsFeedToken() != null
                        && java.security.MessageDigest.isEqual(
                                found.getIcsFeedToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                token == null ? new byte[0] : token.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feed"));
        LocalDate today = LocalDate.now();
        return buildIcs(custodyCase.getId(), today.minusDays(30), today.plusDays(365));
    }

    private String feedPath(UUID caseId, String token) {
        return "/public/ics/" + caseId + "/" + token + ".ics";
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
