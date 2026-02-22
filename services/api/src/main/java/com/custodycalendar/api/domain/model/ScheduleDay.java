package com.custodycalendar.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "schedule_days")
public class ScheduleDay {

    @EmbeddedId
    private ScheduleDayId id;

    @Column(name = "assigned_parent_id", nullable = false)
    private UUID assignedParentId;

    @Column(name = "locked_source_event_id")
    private UUID lockedSourceEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "derived_from", nullable = false)
    private ScheduleDaySource derivedFrom;

    public ScheduleDayId getId() {
        return id;
    }

    public void setId(ScheduleDayId id) {
        this.id = id;
    }

    public UUID getAssignedParentId() {
        return assignedParentId;
    }

    public void setAssignedParentId(UUID assignedParentId) {
        this.assignedParentId = assignedParentId;
    }

    public UUID getLockedSourceEventId() {
        return lockedSourceEventId;
    }

    public void setLockedSourceEventId(UUID lockedSourceEventId) {
        this.lockedSourceEventId = lockedSourceEventId;
    }

    public ScheduleDaySource getDerivedFrom() {
        return derivedFrom;
    }

    public void setDerivedFrom(ScheduleDaySource derivedFrom) {
        this.derivedFrom = derivedFrom;
    }
}
