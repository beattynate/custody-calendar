package com.custodycalendar.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.custodycalendar.api.domain.model.CaseMember;
import com.custodycalendar.api.domain.model.CaseMemberId;
import com.custodycalendar.api.domain.model.CustodyCase;
import com.custodycalendar.api.domain.model.Event;
import com.custodycalendar.api.domain.model.EventAppliesTo;
import com.custodycalendar.api.domain.model.EventType;
import com.custodycalendar.api.domain.model.MemberRole;
import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.model.ScheduleRuleType;
import com.custodycalendar.api.domain.repository.CaseMemberRepository;
import com.custodycalendar.api.domain.repository.CustodyCaseRepository;
import com.custodycalendar.api.domain.repository.EventRepository;
import com.custodycalendar.api.domain.repository.PersonRepository;
import com.custodycalendar.api.domain.repository.ScheduleRuleRepository;
import com.custodycalendar.api.domain.service.ScheduleSolveService;
import com.custodycalendar.api.domain.solver.RequestedScheduleEvent;
import com.custodycalendar.api.domain.solver.ScheduleRunHelper;
import com.custodycalendar.api.domain.solver.ScheduleSolveCommand;
import com.custodycalendar.api.domain.solver.SolveComputationResult;
import com.custodycalendar.api.domain.solver.SolverConstraints;
import com.custodycalendar.api.domain.solver.SolverWeights;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class SolverIntegrationTest extends BaseIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CustodyCaseRepository custodyCaseRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private CaseMemberRepository caseMemberRepository;
    @Autowired private ScheduleRuleRepository scheduleRuleRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ScheduleSolveService scheduleSolveService;
    @Autowired private ScheduleRunHelper scheduleRunHelper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE ledger_entries, schedule_days, schedule_versions, events, school_calendar_days, schedule_rules, children, case_members, people, cases RESTART IDENTITY CASCADE");
    }

    @Test
    void vacationWithKidsOverridesBaselineAndReturnsTopFiveWithDiffs() {
        Fixture fixture = seedFixture();

        SolveComputationResult result = scheduleSolveService.solveDetailed(
                fixture.caseId,
                new ScheduleSolveCommand(
                        null,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        new RequestedScheduleEvent("Summer Vacation", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 14), fixture.parentBId, false),
                        SolverConstraints.defaults(),
                        SolverWeights.defaults()));

        assertThat(result.options()).isNotEmpty();
        assertThat(result.options().size()).isLessThanOrEqualTo(5);
        assertThat(result.options().get(0).scoreBreakdown()).containsKeys("transitions", "parityDrift");
        assertThat(result.options().get(0).changedDays()).isNotEmpty();

        boolean hasRequestedRangeAssigned = result.options().stream().anyMatch(option -> {
            for (LocalDate d = LocalDate.of(2026, 6, 10); !d.isAfter(LocalDate.of(2026, 6, 14)); d = d.plusDays(1)) {
                if (!fixture.parentBId.equals(option.assignments().get(d).assignedParentId())) {
                    return false;
                }
            }
            return true;
        });
        assertThat(hasRequestedRangeAssigned).isTrue();
    }

    @Test
    void lockedHolidayConflictIsRejected() {
        Fixture fixture = seedFixture();
        Event lockedHoliday = new Event();
        lockedHoliday.setId(UUID.randomUUID());
        lockedHoliday.setCaseId(fixture.caseId);
        lockedHoliday.setTitle("Locked Holiday");
        lockedHoliday.setStartDate(LocalDate.of(2026, 12, 24));
        lockedHoliday.setEndDate(LocalDate.of(2026, 12, 26));
        lockedHoliday.setEventType(EventType.HOLIDAY_LOCKED);
        lockedHoliday.setAppliesTo(EventAppliesTo.KIDS_ASSIGNMENT);
        lockedHoliday.setParentId(fixture.parentAId);
        lockedHoliday.setLocked(true);
        eventRepository.save(lockedHoliday);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> scheduleSolveService.solveDetailed(
                fixture.caseId,
                new ScheduleSolveCommand(
                        null,
                        LocalDate.of(2026, 12, 20),
                        LocalDate.of(2026, 12, 31),
                        new RequestedScheduleEvent("Conflicting Vacation", LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 27), fixture.parentBId, false),
                        new SolverConstraints(2, 60, true),
                        SolverWeights.defaults())));

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(ex.getReason()).containsIgnoringCase("locked");
    }

    @Test
    void minRunDaysIsEnforcedSoNoOneDayStaysAppearInReturnedOptions() {
        Fixture fixture = seedFixture();

        SolveComputationResult result = scheduleSolveService.solveDetailed(
                fixture.caseId,
                new ScheduleSolveCommand(
                        null,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 20),
                        new RequestedScheduleEvent("One Day Override", LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 3), fixture.parentAId, false),
                        new SolverConstraints(2, 60, true),
                        SolverWeights.defaults()));

        assertThat(result.options()).isNotEmpty();
        result.options().forEach(option -> assertThat(scheduleRunHelper.deriveRuns(option.assignments()))
                .allMatch(run -> run.lengthDays() >= 2));
    }

    private Fixture seedFixture() {
        CustodyCase custodyCase = new CustodyCase();
        custodyCase.setId(UUID.randomUUID());
        custodyCase.setName("Test Case");
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
        rule.setAnchorDate(LocalDate.of(2026, 6, 1));
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