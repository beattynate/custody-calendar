package com.custodycalendar.api.domain.solver;

public record SolverConstraints(
        int minRunDays,
        int compensationWindowDays,
        boolean respectLocked
) {
    public static SolverConstraints defaults() {
        return new SolverConstraints(2, 60, true);
    }
}