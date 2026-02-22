package com.custodycalendar.api.domain.solver;

import java.util.List;

public record SolveComputationResult(
        List<SolveOptionResult> options
) {
}