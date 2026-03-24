package com.custodycalendar.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedule_proposals")
public class ScheduleProposal {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleProposalStatus status;

    @Column(name = "option_id", nullable = false)
    private String optionId;

    @Column(name = "solve_command_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String solveCommandJson;

    @Column(name = "option_snapshot_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String optionSnapshotJson;

    @Column
    private String reason;

    @Column(name = "accepted_version_id")
    private UUID acceptedVersionId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ScheduleProposalStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleProposalStatus status) {
        this.status = status;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getSolveCommandJson() {
        return solveCommandJson;
    }

    public void setSolveCommandJson(String solveCommandJson) {
        this.solveCommandJson = solveCommandJson;
    }

    public String getOptionSnapshotJson() {
        return optionSnapshotJson;
    }

    public void setOptionSnapshotJson(String optionSnapshotJson) {
        this.optionSnapshotJson = optionSnapshotJson;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getAcceptedVersionId() {
        return acceptedVersionId;
    }

    public void setAcceptedVersionId(UUID acceptedVersionId) {
        this.acceptedVersionId = acceptedVersionId;
    }
}
