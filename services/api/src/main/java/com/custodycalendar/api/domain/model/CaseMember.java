package com.custodycalendar.api.domain.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "case_members")
public class CaseMember {

    @EmbeddedId
    private CaseMemberId id;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    public CaseMemberId getId() {
        return id;
    }

    public void setId(CaseMemberId id) {
        this.id = id;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }
}
