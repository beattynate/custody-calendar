package com.custodycalendar.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "from_parent_id", nullable = false)
    private UUID fromParentId;

    @Column(name = "to_parent_id", nullable = false)
    private UUID toParentId;

    @Column(name = "amount_days", nullable = false)
    private Integer amountDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false)
    private LedgerReasonType reasonType;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_bucket")
    private LedgerDayBucket dayBucket;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "version_id")
    private UUID versionId;

    @Column
    private String notes;

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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public UUID getFromParentId() {
        return fromParentId;
    }

    public void setFromParentId(UUID fromParentId) {
        this.fromParentId = fromParentId;
    }

    public UUID getToParentId() {
        return toParentId;
    }

    public void setToParentId(UUID toParentId) {
        this.toParentId = toParentId;
    }

    public Integer getAmountDays() {
        return amountDays;
    }

    public void setAmountDays(Integer amountDays) {
        this.amountDays = amountDays;
    }

    public LedgerReasonType getReasonType() {
        return reasonType;
    }

    public void setReasonType(LedgerReasonType reasonType) {
        this.reasonType = reasonType;
    }

    public LedgerDayBucket getDayBucket() {
        return dayBucket;
    }

    public void setDayBucket(LedgerDayBucket dayBucket) {
        this.dayBucket = dayBucket;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
