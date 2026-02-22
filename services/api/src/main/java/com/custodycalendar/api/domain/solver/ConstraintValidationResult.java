package com.custodycalendar.api.domain.solver;

import java.util.List;

public record ConstraintValidationResult(
        boolean valid,
        List<String> violations
) {
}