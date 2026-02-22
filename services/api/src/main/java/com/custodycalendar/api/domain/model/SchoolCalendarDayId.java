package com.custodycalendar.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SchoolCalendarDayId implements Serializable {

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "date")
    private LocalDate date;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SchoolCalendarDayId that)) {
            return false;
        }
        return Objects.equals(caseId, that.caseId) && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId, date);
    }
}
