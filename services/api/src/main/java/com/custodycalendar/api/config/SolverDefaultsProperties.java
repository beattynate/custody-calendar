package com.custodycalendar.api.config;

import com.custodycalendar.api.domain.solver.SolverConstraints;
import com.custodycalendar.api.domain.solver.SolverWeights;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.solver.defaults")
public class SolverDefaultsProperties {

    private final Constraints constraints = new Constraints();
    private final Weights weights = new Weights();

    public Constraints getConstraints() {
        return constraints;
    }

    public Weights getWeights() {
        return weights;
    }

    public SolverConstraints toConstraints() {
        return new SolverConstraints(
                constraints.getMinRunDays(),
                constraints.getCompensationWindowDays(),
                constraints.isRespectLocked());
    }

    public SolverWeights toWeights() {
        return new SolverWeights(
                weights.getTransitionPenalty(),
                weights.getSchoolNightTransitionPenalty(),
                weights.getParityDriftPenalty(),
                weights.getLockedProximityPenalty(),
                weights.getOwedImbalancePenalty(),
                weights.getRunDaysOverThreePenalty());
    }

    public static class Constraints {
        private int minRunDays = 2;
        private int compensationWindowDays = 60;
        private boolean respectLocked = true;

        public int getMinRunDays() {
            return minRunDays;
        }

        public void setMinRunDays(int minRunDays) {
            this.minRunDays = minRunDays;
        }

        public int getCompensationWindowDays() {
            return compensationWindowDays;
        }

        public void setCompensationWindowDays(int compensationWindowDays) {
            this.compensationWindowDays = compensationWindowDays;
        }

        public boolean isRespectLocked() {
            return respectLocked;
        }

        public void setRespectLocked(boolean respectLocked) {
            this.respectLocked = respectLocked;
        }
    }

    public static class Weights {
        private int transitionPenalty = 50;
        private int schoolNightTransitionPenalty = 30;
        private int parityDriftPenalty = 40;
        private int lockedProximityPenalty = 100;
        private int owedImbalancePenalty = 5;
        private int runDaysOverThreePenalty = 10;

        public int getTransitionPenalty() {
            return transitionPenalty;
        }

        public void setTransitionPenalty(int transitionPenalty) {
            this.transitionPenalty = transitionPenalty;
        }

        public int getSchoolNightTransitionPenalty() {
            return schoolNightTransitionPenalty;
        }

        public void setSchoolNightTransitionPenalty(int schoolNightTransitionPenalty) {
            this.schoolNightTransitionPenalty = schoolNightTransitionPenalty;
        }

        public int getParityDriftPenalty() {
            return parityDriftPenalty;
        }

        public void setParityDriftPenalty(int parityDriftPenalty) {
            this.parityDriftPenalty = parityDriftPenalty;
        }

        public int getLockedProximityPenalty() {
            return lockedProximityPenalty;
        }

        public void setLockedProximityPenalty(int lockedProximityPenalty) {
            this.lockedProximityPenalty = lockedProximityPenalty;
        }

        public int getOwedImbalancePenalty() {
            return owedImbalancePenalty;
        }

        public void setOwedImbalancePenalty(int owedImbalancePenalty) {
            this.owedImbalancePenalty = owedImbalancePenalty;
        }

        public int getRunDaysOverThreePenalty() {
            return runDaysOverThreePenalty;
        }

        public void setRunDaysOverThreePenalty(int runDaysOverThreePenalty) {
            this.runDaysOverThreePenalty = runDaysOverThreePenalty;
        }
    }
}
