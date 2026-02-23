package com.custodycalendar.api.domain.solver;

public record SolverWeights(
        int transitionPenalty,
        int schoolNightTransitionPenalty,
        int parityDriftPenalty,
        int lockedProximityPenalty,
        int owedImbalancePenalty,
        int runDaysOverThreePenalty
) {
    public static SolverWeights defaults() {
        return new SolverWeights(50, 30, 40, 100, 5, 10);
    }
}
