package com.custodycalendar.api.domain.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "school_calendar_days")
public class SchoolCalendarDay {

    @EmbeddedId
    private SchoolCalendarDayId id;

    @Enumerated(EnumType.STRING)
    private SchoolDayType dayType;

    public SchoolCalendarDayId getId() {
        return id;
    }

    public void setId(SchoolCalendarDayId id) {
        this.id = id;
    }

    public SchoolDayType getDayType() {
        return dayType;
    }

    public void setDayType(SchoolDayType dayType) {
        this.dayType = dayType;
    }
}
