package com.custodycalendar.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ScheduleDayId implements Serializable {

    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "date")
    private LocalDate date;

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
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
        if (!(o instanceof ScheduleDayId that)) {
            return false;
        }
        return Objects.equals(versionId, that.versionId) && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, date);
    }
}
