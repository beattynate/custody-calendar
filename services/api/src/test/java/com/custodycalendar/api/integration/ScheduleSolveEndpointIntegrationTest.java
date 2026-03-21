package com.custodycalendar.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.custodycalendar.api.domain.model.CaseMember;
import com.custodycalendar.api.domain.model.CaseMemberId;
import com.custodycalendar.api.domain.model.CustodyCase;
import com.custodycalendar.api.domain.model.MemberRole;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.model.ScheduleRuleType;
import com.custodycalendar.api.domain.repository.CaseMemberRepository;
import com.custodycalendar.api.domain.repository.CustodyCaseRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import com.custodycalendar.api.domain.repository.ScheduleVersionRepository;
import com.custodycalendar.api.domain.repository.ScheduleRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ScheduleSolveEndpointIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CustodyCaseRepository custodyCaseRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private CaseMemberRepository caseMemberRepository;
    @Autowired private ScheduleRuleRepository scheduleRuleRepository;
    @Autowired private ScheduleVersionRepository scheduleVersionRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE schedule_proposal_approvals, schedule_proposals, ledger_entries, schedule_days, schedule_versions, events, school_calendar_days, schedule_rules, children, case_members, people, cases RESTART IDENTITY CASCADE");
    }

    @Test
    void solveEndpointRequiresAuthentication() throws Exception {
        Fixture fixture = seedFixture();

        mockMvc.perform(post("/api/v1/cases/{caseId}/schedule/solve", fixture.caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solveRequestJson(fixture)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void solveEndpointReturnsJsonContractForAuthorizedMember() throws Exception {
        Fixture fixture = seedFixture();

        mockMvc.perform(post("/api/v1/cases/{caseId}/schedule/solve", fixture.caseId)
                        .with(jwt().jwt(jwt -> jwt.subject("auth0|parent-a").claim("name", "Parent A")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solveRequestJson(fixture)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.options[0].optionId").isString())
                .andExpect(jsonPath("$.options[0].scoreTotal").isNumber())
                .andExpect(jsonPath("$.options[0].scoreBreakdown.transitions").exists())
                .andExpect(jsonPath("$.options[0].scoreBreakdown.parityDrift").exists())
                .andExpect(jsonPath("$.options[0].changedDays").isArray())
                .andExpect(jsonPath("$.options[0].patchOperations").isArray())
                .andExpect(jsonPath("$.options[0].ledgerImpact").isArray());
    }

    @Test
    void solveEndpointAllowsBaselineOnlyRequests() throws Exception {
        Fixture fixture = seedFixture();

        String request = """
                {
                  "horizonStart": "2026-06-01",
                  "horizonEnd": "2026-06-30"
                }
                """;

        mockMvc.perform(post("/api/v1/cases/{caseId}/schedule/solve", fixture.caseId)
                        .with(jwt().jwt(jwt -> jwt.subject("auth0|parent-a").claim("name", "Parent A")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.options[0].optionId").isString());
    }

    @Test
    void proposalApprovalByBothParentsPersistsScheduleVersion() throws Exception {
        Fixture fixture = seedFixture();
        String solveRequest = solveRequestJson(fixture);

        // 1. Solve to get an option ID
        MvcResult solveResult = mockMvc.perform(post("/api/v1/cases/{caseId}/schedule/solve", fixture.caseId)
                        .with(jwt().jwt(jwt -> jwt.subject("auth0|parent-a").claim("name", "Parent A")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solveRequest))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode solveJson = objectMapper.readTree(solveResult.getResponse().getContentAsString());
        String optionId = solveJson.path("options").get(0).path("optionId").asText();

        // 2. Parent A creates a proposal (auto-approves for proposer)
        String proposalRequest = """
                {
                  "optionId": "%s",
                  "reason": "Proposal from endpoint test",
                  "solveRequest": %s
                }
                """.formatted(optionId, solveRequest);

        MvcResult proposalResult = mockMvc.perform(post("/api/v1/cases/{caseId}/schedule/proposals", fixture.caseId)
                        .with(jwt().jwt(jwt -> jwt.subject("auth0|parent-a").claim("name", "Parent A")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposalRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposalId").isString())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();

        JsonNode proposalJson = objectMapper.readTree(proposalResult.getResponse().getContentAsString());
        String proposalId = proposalJson.get("proposalId").asText();

        // 3. Parent B approves — triggers auto-accept with materialized schedule
        MvcResult approveResult = mockMvc.perform(post("/api/v1/cases/{caseId}/schedule/proposals/{proposalId}/approve",
                        fixture.caseId, proposalId)
                        .with(jwt().jwt(jwt -> jwt.subject("auth0|parent-b").claim("name", "Parent B")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.acceptedVersionId").isString())
                .andReturn();

        JsonNode approveJson = objectMapper.readTree(approveResult.getResponse().getContentAsString());
        UUID versionId = UUID.fromString(approveJson.get("acceptedVersionId").asText());

        assertThat(scheduleVersionRepository.findById(versionId)).isPresent();
        Integer persistedDayCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM schedule_days WHERE version_id = ?",
                Integer.class,
                versionId);
        assertThat(persistedDayCount).isGreaterThan(0);

        String preferences = jdbcTemplate.queryForObject(
                "SELECT preferences::text FROM schedule_versions WHERE id = ?",
                String.class,
                versionId);
        assertThat(preferences).contains("scoreBreakdown");
        assertThat(preferences).contains("changedDays");
    }

    private String solveRequestJson(Fixture fixture) {
        return """
                {
                  "horizonStart": "2026-06-01",
                  "horizonEnd": "2026-06-30",
                  "newEvent": {
                    "title": "Summer Vacation",
                    "startDate": "2026-06-10",
                    "endDate": "2026-06-14",
                    "eventType": "VACATION_WITH_KIDS",
                    "appliesTo": "KIDS_ASSIGNMENT",
                    "parentId": "%s",
                    "locked": false
                  }
                }
                """.formatted(fixture.parentBId);
    }

    private Fixture seedFixture() {
        CustodyCase custodyCase = new CustodyCase();
        custodyCase.setId(UUID.randomUUID());
        custodyCase.setName("Endpoint Test Case");
        custodyCase.setTimezone("America/Denver");
        custodyCaseRepository.save(custodyCase);

        Person parentA = new Person();
        parentA.setId(UUID.randomUUID());
        parentA.setExternalSubject("auth0|parent-a");
        parentA.setDisplayName("Parent A");
        personRepository.save(parentA);

        Person parentB = new Person();
        parentB.setId(UUID.randomUUID());
        parentB.setExternalSubject("auth0|parent-b");
        parentB.setDisplayName("Parent B");
        personRepository.save(parentB);

        caseMemberRepository.save(member(custodyCase.getId(), parentA.getId(), MemberRole.ADMIN));
        caseMemberRepository.save(member(custodyCase.getId(), parentB.getId(), MemberRole.PARENT));

        ScheduleRule rule = new ScheduleRule();
        rule.setId(UUID.randomUUID());
        rule.setCaseId(custodyCase.getId());
        rule.setType(ScheduleRuleType.TWO_TWO_THREE);
        rule.setAnchorDate(java.time.LocalDate.of(2026, 6, 1));
        rule.setParentAId(parentA.getId());
        rule.setParentBId(parentB.getId());
        rule.setMetadata("{\"anchorParent\":\"A\"}");
        scheduleRuleRepository.save(rule);

        return new Fixture(custodyCase.getId(), parentA.getId(), parentB.getId());
    }

    private CaseMember member(UUID caseId, UUID personId, MemberRole role) {
        CaseMemberId id = new CaseMemberId();
        id.setCaseId(caseId);
        id.setPersonId(personId);
        CaseMember member = new CaseMember();
        member.setId(id);
        member.setRole(role);
        return member;
    }

    private record Fixture(UUID caseId, UUID parentAId, UUID parentBId) {
    }
}
