package com.custodycalendar.api.domain.service;

import com.custodycalendar.api.domain.model.Person;
import com.custodycalendar.api.domain.model.ScheduleProposal;
import com.custodycalendar.api.domain.model.ScheduleProposalApproval;
import com.custodycalendar.api.domain.model.ScheduleProposalApprovalDecision;
import com.custodycalendar.api.domain.model.ScheduleProposalStatus;
import com.custodycalendar.api.domain.model.ScheduleRule;
import com.custodycalendar.api.domain.repository.PersonRepository;
import com.custodycalendar.api.domain.repository.ScheduleProposalApprovalRepository;
import com.custodycalendar.api.domain.repository.ScheduleProposalRepository;
import com.custodycalendar.api.domain.repository.ScheduleRuleRepository;
import com.custodycalendar.api.domain.solver.ScheduleSolveCommand;
import com.custodycalendar.api.domain.solver.SolveComputationResult;
import com.custodycalendar.api.domain.solver.SolveOptionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleProposalService {

    private final CaseService caseService;
    private final PersonRepository personRepository;
    private final PersonDirectoryService personDirectoryService;
    private final ScheduleRuleRepository scheduleRuleRepository;
    private final ScheduleProposalRepository scheduleProposalRepository;
    private final ScheduleProposalApprovalRepository scheduleProposalApprovalRepository;
    private final ScheduleSolveService scheduleSolveService;
    private final ScheduleVersionService scheduleVersionService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ScheduleProposalService(
            CaseService caseService,
            PersonRepository personRepository,
            PersonDirectoryService personDirectoryService,
            ScheduleRuleRepository scheduleRuleRepository,
            ScheduleProposalRepository scheduleProposalRepository,
            ScheduleProposalApprovalRepository scheduleProposalApprovalRepository,
            ScheduleSolveService scheduleSolveService,
            ScheduleVersionService scheduleVersionService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.caseService = caseService;
        this.personRepository = personRepository;
        this.personDirectoryService = personDirectoryService;
        this.scheduleRuleRepository = scheduleRuleRepository;
        this.scheduleProposalRepository = scheduleProposalRepository;
        this.scheduleProposalApprovalRepository = scheduleProposalApprovalRepository;
        this.scheduleSolveService = scheduleSolveService;
        this.scheduleVersionService = scheduleVersionService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProposalView createProposal(
            UUID caseId,
            String actorExternalSubject,
            ScheduleSolveCommand command,
            String optionId,
            String reason) {
        caseService.requireCase(caseId);
        Person actor = requirePerson(actorExternalSubject);
        ScheduleRule rule = requireScheduleRule(caseId);
        requireApprover(rule, actor.getId());

        SolveComputationResult solveResult = scheduleSolveService.solveDetailed(caseId, command);
        SolveOptionResult selected = solveResult.options().stream()
                .filter(option -> option.optionId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected optionId not found in solver result"));

        ScheduleProposal proposal = new ScheduleProposal();
        proposal.setId(UUID.randomUUID());
        proposal.setCaseId(caseId);
        proposal.setCreatedBy(actor.getId());
        proposal.setCreatedAt(OffsetDateTime.now());
        proposal.setStatus(ScheduleProposalStatus.PENDING_APPROVAL);
        proposal.setOptionId(optionId);
        proposal.setSolveCommandJson(writeJson(command));
        proposal.setOptionSnapshotJson(writeJson(buildOptionSnapshot(selected)));
        proposal.setReason(reason == null || reason.isBlank() ? "Solver proposal " + optionId : reason.trim());
        scheduleProposalRepository.save(proposal);

        List<UUID> approverIds = List.of(rule.getParentAId(), rule.getParentBId());
        for (UUID approverId : approverIds) {
            ScheduleProposalApproval approval = new ScheduleProposalApproval();
            approval.setId(UUID.randomUUID());
            approval.setProposalId(proposal.getId());
            approval.setPersonId(approverId);
            if (approverId.equals(actor.getId())) {
                approval.setDecision(ScheduleProposalApprovalDecision.APPROVE);
                approval.setDecidedAt(OffsetDateTime.now());
                approval.setComment("Auto-approved by proposer");
            }
            scheduleProposalApprovalRepository.save(approval);
        }

        auditLogService.record(caseId, actor.getId(), "PROPOSAL_CREATED", "PROPOSAL", proposal.getId(),
                Map.of("optionId", optionId, "reason", proposal.getReason()));
        return toProposalView(proposal);
    }

    @Transactional(readOnly = true)
    public List<ProposalView> listProposals(UUID caseId) {
        caseService.requireCase(caseId);
        return scheduleProposalRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
                .map(this::toProposalView)
                .toList();
    }

    @Transactional
    public ProposalView approveProposal(UUID caseId, UUID proposalId, String actorExternalSubject, String comment) {
        caseService.requireCase(caseId);
        ScheduleProposal proposal = scheduleProposalRepository.findByIdAndCaseId(proposalId, caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
        if (proposal.getStatus() == ScheduleProposalStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposal is already rejected");
        }

        Person actor = requirePerson(actorExternalSubject);
        ScheduleRule rule = requireScheduleRule(caseId);
        requireApprover(rule, actor.getId());

        ScheduleProposalApproval approval = scheduleProposalApprovalRepository
                .findByProposalIdAndPersonId(proposalId, actor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not an approver for this proposal"));
        approval.setDecision(ScheduleProposalApprovalDecision.APPROVE);
        approval.setDecidedAt(OffsetDateTime.now());
        approval.setComment(trimToNull(comment));
        scheduleProposalApprovalRepository.save(approval);

        if (proposal.getStatus() == ScheduleProposalStatus.PENDING_APPROVAL && allRequiredApprovalsApproved(proposal.getId())) {
            ScheduleSolveCommand command = readJson(proposal.getSolveCommandJson(), ScheduleSolveCommand.class);
            var accepted = scheduleVersionService.acceptSolveOption(
                    caseId,
                    actorExternalSubject,
                    proposal.getOptionId(),
                    command,
                    "Approved by both parents via proposal " + proposal.getId());
            proposal.setStatus(ScheduleProposalStatus.APPROVED);
            proposal.setAcceptedVersionId(accepted.versionId());
            scheduleProposalRepository.save(proposal);
        }

        auditLogService.record(caseId, actor.getId(), "PROPOSAL_APPROVED", "PROPOSAL", proposal.getId(),
                Map.of("status", proposal.getStatus().name()));
        return toProposalView(proposal);
    }

    @Transactional
    public ProposalView rejectProposal(UUID caseId, UUID proposalId, String actorExternalSubject, String comment) {
        caseService.requireCase(caseId);
        ScheduleProposal proposal = scheduleProposalRepository.findByIdAndCaseId(proposalId, caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
        if (proposal.getStatus() == ScheduleProposalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proposal is already approved");
        }

        Person actor = requirePerson(actorExternalSubject);
        ScheduleRule rule = requireScheduleRule(caseId);
        requireApprover(rule, actor.getId());

        ScheduleProposalApproval approval = scheduleProposalApprovalRepository
                .findByProposalIdAndPersonId(proposalId, actor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not an approver for this proposal"));
        approval.setDecision(ScheduleProposalApprovalDecision.REJECT);
        approval.setDecidedAt(OffsetDateTime.now());
        approval.setComment(trimToNull(comment));
        scheduleProposalApprovalRepository.save(approval);

        proposal.setStatus(ScheduleProposalStatus.REJECTED);
        scheduleProposalRepository.save(proposal);
        auditLogService.record(caseId, actor.getId(), "PROPOSAL_REJECTED", "PROPOSAL", proposal.getId(), null);
        return toProposalView(proposal);
    }

    private boolean allRequiredApprovalsApproved(UUID proposalId) {
        List<ScheduleProposalApproval> approvals = scheduleProposalApprovalRepository.findByProposalIdOrderByPersonIdAsc(proposalId);
        return approvals.size() >= 2 && approvals.stream().allMatch(a -> a.getDecision() == ScheduleProposalApprovalDecision.APPROVE);
    }

    private ProposalView toProposalView(ScheduleProposal proposal) {
        List<ScheduleProposalApproval> approvals = scheduleProposalApprovalRepository.findByProposalIdOrderByPersonIdAsc(proposal.getId());
        Map<UUID, Person> people = personRepository.findAllById(
                        approvals.stream().map(ScheduleProposalApproval::getPersonId)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        Person creator = personRepository.findById(proposal.getCreatedBy()).orElse(null);

        List<ApprovalView> approvalViews = new ArrayList<>();
        for (ScheduleProposalApproval approval : approvals) {
            Person person = people.get(approval.getPersonId());
            approvalViews.add(new ApprovalView(
                    approval.getPersonId(),
                    person == null ? null : person.getDisplayName(),
                    approval.getDecision() == null ? null : approval.getDecision().name(),
                    approval.getDecidedAt(),
                    approval.getComment()));
        }

        return new ProposalView(
                proposal.getId(),
                proposal.getStatus().name(),
                proposal.getCreatedBy(),
                creator == null ? null : creator.getDisplayName(),
                proposal.getCreatedAt(),
                proposal.getOptionId(),
                proposal.getReason(),
                proposal.getAcceptedVersionId(),
                readJsonNode(proposal.getOptionSnapshotJson()),
                List.copyOf(approvalViews));
    }

    private Map<String, Object> buildOptionSnapshot(SolveOptionResult option) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("optionId", option.optionId());
        snapshot.put("scoreTotal", option.scoreTotal());
        snapshot.put("scoreBreakdown", option.scoreBreakdown());
        snapshot.put("scoreDetails", option.scoreDetails());
        snapshot.put("patchOperations", option.patchOperations());
        snapshot.put("changedDays", option.changedDays());
        snapshot.put("ledgerImpact", option.ledgerImpact());
        snapshot.put("owedBalances", option.owedBalances());
        return snapshot;
    }

    private Person requirePerson(String externalSubject) {
        return personDirectoryService.requireBySubject(externalSubject);
    }

    private ScheduleRule requireScheduleRule(UUID caseId) {
        return scheduleRuleRepository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule rule is required before proposals"));
    }

    private void requireApprover(ScheduleRule rule, UUID personId) {
        if (!rule.getParentAId().equals(personId) && !rule.getParentBId().equals(personId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only schedule rule parents can approve proposals");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize proposal snapshot");
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read proposal snapshot");
        }
    }

    private JsonNode readJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read proposal snapshot");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ProposalView(
            UUID proposalId,
            String status,
            UUID createdBy,
            String createdByName,
            OffsetDateTime createdAt,
            String optionId,
            String reason,
            UUID acceptedVersionId,
            JsonNode optionSnapshot,
            List<ApprovalView> approvals) {
    }

    public record ApprovalView(
            UUID personId,
            String displayName,
            String decision,
            OffsetDateTime decidedAt,
            String comment) {
    }
}
