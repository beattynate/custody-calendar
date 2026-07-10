package com.custodycalendar.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false)
    private EventAppliesTo appliesTo;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Column
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private EventApprovalStatus approvalStatus = EventApprovalStatus.ACTIVE;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "change_requested_by")
    private UUID changeRequestedBy;

    @Column(name = "pending_change", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String pendingChange;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    public EventApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(EventApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getChangeRequestedBy() {
        return changeRequestedBy;
    }

    public void setChangeRequestedBy(UUID changeRequestedBy) {
        this.changeRequestedBy = changeRequestedBy;
    }

    public String getPendingChange() {
        return pendingChange;
    }

    public void setPendingChange(String pendingChange) {
        this.pendingChange = pendingChange;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public EventAppliesTo getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(EventAppliesTo appliesTo) {
        this.appliesTo = appliesTo;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getRecurrenceRule() {
        return recurrenceRule;
    }

    public void setRecurrenceRule(String recurrenceRule) {
        this.recurrenceRule = recurrenceRule;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
