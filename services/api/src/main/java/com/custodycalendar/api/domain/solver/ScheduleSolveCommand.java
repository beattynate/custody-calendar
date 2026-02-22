package com.custodycalendar.api.domain.solver;

import java.time.LocalDate;
import java.util.UUID;

public record ScheduleSolveCommand(
        UUID baseVersionId,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        RequestedScheduleEvent newEvent,
        SolverConstraints constraints,
        SolverWeights weights
) {
}