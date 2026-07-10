package com.custodycalendar.api.domain.model;

/**
 * Consent state for events. Locked events are hard schedule overrides, so
 * any mutation of one requires the other schedule-rule parent's approval:
 * creations start at PENDING_CREATE, and edits/deletions of an ACTIVE locked
 * event park at PENDING_UPDATE/PENDING_DELETE (the current values stay in
 * effect) until approved. Non-locked events are always ACTIVE.
 */
public enum EventApprovalStatus {
    PENDING_CREATE,
    ACTIVE,
    PENDING_UPDATE,
    PENDING_DELETE
}
