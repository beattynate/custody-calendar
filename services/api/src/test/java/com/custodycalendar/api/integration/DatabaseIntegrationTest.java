package com.custodycalendar.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.custodycalendar.api.domain.repository.CustodyCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DatabaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CustodyCaseRepository custodyCaseRepository;

    @Test
    void flywaySchemaIsApplied() {
        assertThat(custodyCaseRepository.count()).isGreaterThanOrEqualTo(0);
    }
}
