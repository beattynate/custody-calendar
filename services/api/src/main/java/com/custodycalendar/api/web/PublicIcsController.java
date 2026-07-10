package com.custodycalendar.api.web;

import com.custodycalendar.api.domain.service.ScheduleIcsService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated iCalendar feed for calendar-app subscriptions, protected by
 * a per-case capability token in the URL (calendar apps cannot send JWT
 * headers). Rotating the token from the app invalidates old URLs.
 */
@RestController
public class PublicIcsController {

    private final ScheduleIcsService scheduleIcsService;

    public PublicIcsController(ScheduleIcsService scheduleIcsService) {
        this.scheduleIcsService = scheduleIcsService;
    }

    @GetMapping(value = "/public/ics/{caseId}/{token}.ics", produces = "text/calendar")
    public ResponseEntity<String> feed(@PathVariable UUID caseId, @PathVariable String token) {
        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=900")
                .body(scheduleIcsService.buildFeedIcs(caseId, token));
    }
}
