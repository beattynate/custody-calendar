package com.custodycalendar.api.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class EventConsentIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String caseId;
    private String parentAId;
    private String parentBId;

    @BeforeEach
    void setUpCase() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE audit_log, schedule_proposal_approvals, schedule_proposals, ledger_entries, schedule_days, schedule_versions, events, school_calendar_days, schedule_rules, children, case_members, person_identities, people, cases RESTART IDENTITY CASCADE");

        MvcResult caseResult = mockMvc.perform(post("/api/v1/cases")
                        .with(asA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Consent Case\",\"timezone\":\"America/Denver\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        caseId = objectMapper.readTree(caseResult.getResponse().getContentAsString()).get("id").asText();

        parentAId = objectMapper.readTree(mockMvc.perform(get("/api/v1/me").with(asA()))
                .andReturn().getResponse().getContentAsString()).get("personId").asText();

        MvcResult memberResult = mockMvc.perform(post("/api/v1/cases/{caseId}/people", caseId)
                        .with(asA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalSubject\":\"clerk|ex\",\"displayName\":\"Ex\",\"role\":\"PARENT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        parentBId = objectMapper.readTree(memberResult.getResponse().getContentAsString()).get("personId").asText();

        // Initial rule creation applies directly (onboarding).
        mockMvc.perform(put("/api/v1/cases/{caseId}/schedule-rule", caseId)
                        .with(asA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TWO_TWO_THREE","anchorDate":"2026-06-01",
                                 "parentAId":"%s","parentBId":"%s",
                                 "metadata":{"anchorParent":"A"}}
                                """.formatted(parentAId, parentBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingChange").doesNotExist());
    }

    private RequestPostProcessor asA() {
        return jwt().jwt(jwt -> jwt.subject("clerk|nate").claim("name", "Nate"));
    }

    private RequestPostProcessor asB() {
        return jwt().jwt(jwt -> jwt.subject("clerk|ex").claim("name", "Ex"));
    }

    private String lockedEventJson(String title) {
        return """
                {"title":"%s","startDate":"2026-12-24","endDate":"2026-12-26",
                 "eventType":"HOLIDAY_LOCKED","appliesTo":"KIDS_ASSIGNMENT",
                 "parentId":"%s","locked":true}
                """.formatted(title, parentAId);
    }

    @Test
    void lockedEventNeedsOtherParentApproval() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/cases/{caseId}/events", caseId)
                        .with(asA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lockedEventJson("Christmas")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_CREATE"))
                .andReturn();
        String eventId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // The creator cannot approve their own locked event.
        mockMvc.perform(post("/api/v1/cases/{caseId}/events/{eventId}/approve", caseId, eventId).with(asA()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/cases/{caseId}/events/{eventId}/approve", caseId, eventId).with(asB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("ACTIVE"));

        // Deleting the now-active locked event also needs the other parent.
        mockMvc.perform(delete("/api/v1/cases/{caseId}/events/{eventId}", caseId, eventId).with(asB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_DELETE"));
        mockMvc.perform(post("/api/v1/cases/{caseId}/events/{eventId}/approve", caseId, eventId).with(asA()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases/{caseId}/events?from=2026-12-01&to=2026-12-31", caseId).with(asA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void scheduleFallsBackToBaselineBeforeFirstAcceptedVersion() throws Exception {
        mockMvc.perform(get("/api/v1/cases/{caseId}/schedule?from=2026-06-01&to=2026-06-14", caseId).with(asA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$[0].derivedFrom").value("BASELINE"));
    }

    @Test
    void icsExportContainsCustodyRuns() throws Exception {
        MvcResult ics = mockMvc.perform(get("/api/v1/cases/{caseId}/schedule.ics?from=2026-06-01&to=2026-06-14", caseId).with(asA()))
                .andExpect(status().isOk())
                .andReturn();
        String body = ics.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("BEGIN:VCALENDAR");
        org.assertj.core.api.Assertions.assertThat(body).contains("SUMMARY:Kids with ");
        org.assertj.core.api.Assertions.assertThat(body).contains("DTSTART;VALUE=DATE:20260601");
    }

    @Test
    void icsFeedTokenGrantsPublicAccessUntilRotated() throws Exception {
        MvcResult rotated = mockMvc.perform(post("/api/v1/cases/{caseId}/ics-feed", caseId).with(asA()))
                .andExpect(status().isOk())
                .andReturn();
        String feedPath = objectMapper.readTree(rotated.getResponse().getContentAsString()).get("feedPath").asText();

        // The feed URL works without any authentication.
        MvcResult feed = mockMvc.perform(get(feedPath))
                .andExpect(status().isOk())
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(feed.getResponse().getContentAsString())
                .contains("BEGIN:VCALENDAR")
                .contains("SUMMARY:Kids with ");

        // Either parent can read the current feed path while signed in.
        mockMvc.perform(get("/api/v1/cases/{caseId}/ics-feed", caseId).with(asB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedPath").value(feedPath));

        // Rotating invalidates previously shared URLs.
        mockMvc.perform(post("/api/v1/cases/{caseId}/ics-feed", caseId).with(asB()))
                .andExpect(status().isOk());
        mockMvc.perform(get(feedPath))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonLockedEventsApplyImmediatelyAndDeleteDirectly() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/cases/{caseId}/events", caseId)
                        .with(asA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Trip","startDate":"2026-07-10","endDate":"2026-07-12",
                                 "eventType":"VACATION_WITH_KIDS","appliesTo":"KIDS_ASSIGNMENT",
                                 "parentId":"%s","locked":false}
                                """.formatted(parentAId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalStatus").value("ACTIVE"))
                .andReturn();
        String eventId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/cases/{caseId}/events/{eventId}", caseId, eventId).with(asA()))
                .andExpect(status().isNoContent());
    }

    @Test
    void ruleChangesNeedOtherParentApprovalAndAreAudited() throws Exception {
        mockMvc.perform(put("/api/v1/cases/{caseId}/schedule-rule", caseId)
                        .with(asA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TWO_TWO_THREE","anchorDate":"2026-09-07",
                                 "parentAId":"%s","parentBId":"%s",
                                 "metadata":{"anchorParent":"A"}}
                                """.formatted(parentAId, parentBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchorDate").value("2026-06-01"))
                .andExpect(jsonPath("$.pendingChange.anchorDate").value("2026-09-07"));

        // Requester cannot approve their own change; the other parent can.
        mockMvc.perform(post("/api/v1/cases/{caseId}/schedule-rule/approve", caseId).with(asA()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/cases/{caseId}/schedule-rule/approve", caseId).with(asB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchorDate").value("2026-09-07"))
                .andExpect(jsonPath("$.pendingChange").doesNotExist());

        mockMvc.perform(get("/api/v1/cases/{caseId}/audit", caseId).with(asA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("RULE_CHANGE_APPROVED"))
                .andExpect(jsonPath("$[?(@.action=='RULE_CREATED')]").exists())
                .andExpect(jsonPath("$[?(@.action=='MEMBER_ADDED')]").exists());
    }
}
