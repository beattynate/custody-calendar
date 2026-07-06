package com.custodycalendar.api.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class PersonIdentityEndpointIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE schedule_proposal_approvals, schedule_proposals, ledger_entries, schedule_days, schedule_versions, events, school_calendar_days, schedule_rules, children, case_members, person_identities, people, cases RESTART IDENTITY CASCADE");
    }

    private RequestPostProcessor asSubject(String subject, String name) {
        return jwt().jwt(jwt -> jwt.subject(subject).claim("name", name));
    }

    private String createCase(String subject, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cases")
                        .with(asSubject(subject, name))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Identity Test Case\",\"timezone\":\"America/Denver\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void linkedIdentityActsAsSamePersonAcrossCases() throws Exception {
        String caseId = createCase("clerk|nate", "Nate");

        // Creating a case auto-provisions the person with a primary identity.
        MvcResult meResult = mockMvc.perform(get("/api/v1/me").with(asSubject("clerk|nate", "Nate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identities[0].externalSubject").value("clerk|nate"))
                .andExpect(jsonPath("$.identities[0].primary").value(true))
                .andReturn();
        String natePersonId = objectMapper.readTree(meResult.getResponse().getContentAsString())
                .get("personId").asText();

        // Partner's login is unknown before linking.
        mockMvc.perform(get("/api/v1/cases").with(asSubject("clerk|partner", "Partner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Nate links the partner's login to himself.
        mockMvc.perform(post("/api/v1/me/identities")
                        .with(asSubject("clerk|nate", "Nate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalSubject\":\"clerk|partner\",\"label\":\"Partner\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalSubject").value("clerk|partner"))
                .andExpect(jsonPath("$.primary").value(false));

        // Partner now resolves to the same person and sees the same cases.
        mockMvc.perform(get("/api/v1/me").with(asSubject("clerk|partner", "Partner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personId").value(natePersonId));
        mockMvc.perform(get("/api/v1/cases").with(asSubject("clerk|partner", "Partner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(caseId));
        mockMvc.perform(get("/api/v1/cases/{caseId}", caseId).with(asSubject("clerk|partner", "Partner")))
                .andExpect(status().isOk());
    }

    @Test
    void subjectAlreadyLinkedToAnotherPersonIsRejected() throws Exception {
        String caseId = createCase("clerk|nate", "Nate");

        mockMvc.perform(post("/api/v1/cases/{caseId}/people", caseId)
                        .with(asSubject("clerk|nate", "Nate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalSubject\":\"clerk|ex\",\"displayName\":\"Ex\",\"role\":\"PARENT\"}"))
                .andExpect(status().isCreated());

        // The ex's login cannot be claimed as one of Nate's identities.
        mockMvc.perform(post("/api/v1/me/identities")
                        .with(asSubject("clerk|nate", "Nate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalSubject\":\"clerk|ex\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void primaryAndCurrentIdentitiesCannotBeRemovedButLinkedOnesCan() throws Exception {
        createCase("clerk|nate", "Nate");

        MvcResult linkResult = mockMvc.perform(post("/api/v1/me/identities")
                        .with(asSubject("clerk|nate", "Nate"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalSubject\":\"clerk|partner\",\"label\":\"Partner\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode linked = objectMapper.readTree(linkResult.getResponse().getContentAsString());

        MvcResult meResult = mockMvc.perform(get("/api/v1/me").with(asSubject("clerk|nate", "Nate")))
                .andExpect(status().isOk())
                .andReturn();
        String primaryIdentityId = objectMapper.readTree(meResult.getResponse().getContentAsString())
                .get("identities").get(0).get("id").asText();

        mockMvc.perform(delete("/api/v1/me/identities/{id}", primaryIdentityId)
                        .with(asSubject("clerk|nate", "Nate")))
                .andExpect(status().isBadRequest());

        // The partner cannot unlink the login they are currently signed in with.
        mockMvc.perform(delete("/api/v1/me/identities/{id}", linked.get("id").asText())
                        .with(asSubject("clerk|partner", "Partner")))
                .andExpect(status().isBadRequest());

        // Nate can unlink the partner login; afterwards it no longer resolves.
        mockMvc.perform(delete("/api/v1/me/identities/{id}", linked.get("id").asText())
                        .with(asSubject("clerk|nate", "Nate")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/cases").with(asSubject("clerk|partner", "Partner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
