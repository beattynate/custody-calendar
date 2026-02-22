package com.custodycalendar.api.web.dto;

public record SolveWeightsRequest(
        Integer transitionPenalty,
        Integer schoolNightTransitionPenalty,
        Integer parityDriftPenalty,
        Integer lockedProximityPenalty,
        Integer owedImbalancePenalty
) {
}