package com.custodycalendar.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedule_rules")
public class ScheduleRule {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleRuleType type;

    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Column(name = "parent_a_id", nullable = false)
    private UUID parentAId;

    @Column(name = "parent_b_id", nullable = false)
    private UUID parentBId;

    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

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

    public ScheduleRuleType getType() {
        return type;
    }

    public void setType(ScheduleRuleType type) {
        this.type = type;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public void setAnchorDate(LocalDate anchorDate) {
        this.anchorDate = anchorDate;
    }

    public UUID getParentAId() {
        return parentAId;
    }

    public void setParentAId(UUID parentAId) {
        this.parentAId = parentAId;
    }

    public UUID getParentBId() {
        return parentBId;
    }

    public void setParentBId(UUID parentBId) {
        this.parentBId = parentBId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
