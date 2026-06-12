package com.agentguard.service;

public interface RiskAssessmentProvider {

    class RiskAssessmentResult {
        private final int riskScore; // 0-100
        private final String decision; // APPROVED, REVIEW, REJECTED
        private final String explanation;
        private final String triggeredRule;

        public RiskAssessmentResult(int riskScore, String decision, String explanation, String triggeredRule) {
            this.riskScore = riskScore;
            this.decision = decision;
            this.explanation = explanation;
            this.triggeredRule = triggeredRule;
        }

        public int getRiskScore() { return riskScore; }
        public String getDecision() { return decision; }
        public String getExplanation() { return explanation; }
        public String getTriggeredRule() { return triggeredRule; }
    }

    /**
     * Synchronously assesses command risk under strict SLA bounds if used on fast-path.
     */
    RiskAssessmentResult assessCommandRisk(String command, String context);

    /**
     * Asynchronously enriches audit entries with full developer intent explanation.
     */
    String generateExplainabilityLog(String command, String decision, int score, String ruleId);
}
