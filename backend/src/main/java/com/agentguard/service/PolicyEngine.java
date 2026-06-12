package com.agentguard.service;

import com.agentguard.behavior.BehavioralAnalysisService;
import com.agentguard.identity.AgentIdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PolicyEngine {

    public static class ValidationResult {
        private final String decision; // APPROVED, REJECTED, REVIEW
        private final String message;
        private final int riskScore;
        private final String triggeredRule;
        private final long evaluationTimeNs;

        public ValidationResult(String decision, String message, int riskScore, String triggeredRule, long evaluationTimeNs) {
            this.decision = decision;
            this.message = message;
            this.riskScore = riskScore;
            this.triggeredRule = triggeredRule;
            this.evaluationTimeNs = evaluationTimeNs;
        }

        public String getDecision() { return decision; }
        public String getMessage() { return message; }
        public int getRiskScore() { return riskScore; }
        public String getTriggeredRule() { return triggeredRule; }
        public long getEvaluationTimeNs() { return evaluationTimeNs; }
    }

    private static class Rule {
        private final String id;
        private final Pattern pattern;
        private final int baseRiskScore;
        private final String description;

        public Rule(String id, String regex, int baseRiskScore, String description) {
            this.id = id;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.baseRiskScore = baseRiskScore;
            this.description = description;
        }

        public boolean matches(String command) {
            return pattern.matcher(command).find();
        }

        public String getId() { return id; }
        public int getBaseRiskScore() { return baseRiskScore; }
        public String getDescription() { return description; }
    }

    private final List<Rule> deterministicRules = new ArrayList<>();
    private final MetricsService metricsService;
    private final AgentIdentityService agentIdentityService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final YamlPolicyLoader yamlPolicyLoader;

    @Value("${agentguard.profile:balanced}")
    private String activeProfile;

    public PolicyEngine(MetricsService metricsService,
                        AgentIdentityService agentIdentityService,
                        BehavioralAnalysisService behavioralAnalysisService,
                        YamlPolicyLoader yamlPolicyLoader) {
        this.metricsService = metricsService;
        this.agentIdentityService = agentIdentityService;
        this.behavioralAnalysisService = behavioralAnalysisService;
        this.yamlPolicyLoader = yamlPolicyLoader;

        // Base Command Risk Configurations
        deterministicRules.add(new Rule("force_push_block", "git\\s+push\\s+.*(--force|-f(\\s+|$))", 95, "Force push operations violate team safety guidelines."));
        deterministicRules.add(new Rule("destructive_rm", "rm\\s+-rf\\s+.*", 98, "Recursive destructive file deletions are prohibited."));
        deterministicRules.add(new Rule("schema_drop", "drop\\s+database\\s+.*", 100, "Database drops are blocked in development environments."));
        deterministicRules.add(new Rule("env_secrets_read", "cat\\s+\\.env.*", 60, "Accessing local environment configuration files is flagged for review."));
        deterministicRules.add(new Rule("git_standard_push", "git\\s+push(\\s+|$)", 10, "Standard collaborative repository push approved."));
        deterministicRules.add(new Rule("npm_install", "npm\\s+install(\\s+|$)", 5, "Standard package installer execute approved."));
        deterministicRules.add(new Rule("npm_build", "npm\\s+run\\s+.*", 5, "Development build execution approved."));
    }

    /**
     * Evaluates command safety using a production-grade multi-factor equation,
     * merging custom YAML policies at runtime.
     */
    public ValidationResult evaluateCommand(String command, String sessionId) {
        long startTime = System.nanoTime();
        
        String matchedRuleId = "none";
        int commandRisk = 10;
        String message = "No specific policy rules triggered.";

        // 1. Resolve Attribution and Agent Identity
        AgentIdentityService.AgentIdentity agent = agentIdentityService.resolveAgentIdentity(sessionId, "ubuntu-local");

        // 2. CHECK DYNAMIC ENTERPRISE YAML RULES (Priority Ingestion)
        boolean dynamicMatched = false;
        List<YamlPolicyLoader.DynamicRule> dynamicRules = yamlPolicyLoader.getDynamicRules();
        if (dynamicRules != null) {
            for (YamlPolicyLoader.DynamicRule drule : dynamicRules) {
                if (drule.getMatch() != null && command.toLowerCase().contains(drule.getMatch().toLowerCase())) {
                    matchedRuleId = "dynamic_" + drule.getId();
                    message = "Dynamic Enterprise Policy Alert: Custom rule match.";
                    
                    if ("REJECTED".equals(drule.getAction())) {
                        commandRisk = 95;
                    } else if ("REVIEW".equals(drule.getAction())) {
                        commandRisk = 50;
                    } else {
                        commandRisk = 10;
                    }
                    dynamicMatched = true;
                    break;
                }
            }
        }

        // 3. Fallback: Base Command Risk (Rules Core - 40% Weight)
        if (!dynamicMatched) {
            for (Rule rule : deterministicRules) {
                if (rule.matches(command)) {
                    matchedRuleId = rule.getId();
                    commandRisk = rule.getBaseRiskScore();
                    message = rule.getDescription();
                    break;
                }
            }
        }

        // 4. Context Risk (File & Repo Context - 40% Weight)
        int fileRisk = 10;
        if (command.contains(".env") || command.contains(".pem") || command.contains(".key") || command.contains("pom.xml")) {
            fileRisk = 100;
            message = message + " [Secrets Configuration Target]";
        }

        int repoRisk = 10;
        if (command.contains("main") || command.contains("master") || command.contains("production")) {
            repoRisk = 100;
            message = message + " [Protected Branch Context]";
        }

        double ruleRisk = (commandRisk * 0.4) + (fileRisk * 0.2) + (repoRisk * 0.2);

        // 5. Behavioral Risk (Anomaly Detection - 20% Weight)
        int anomalyRisk = 10;
        if (behavioralAnalysisService.isBehavioralAnomaly(command)) {
            anomalyRisk = 100;
            message = message + " [Behavioral Anomaly Triggered]";
        }
        double behavioralRisk = anomalyRisk * 0.2;

        // 6. Agent Trust Score Adjustment
        double trustAdjustment = (agent.getTrustScore() * 20);

        // Compute Multi-Factor Weighted Score
        double rawRiskScore = ruleRisk + behavioralRisk - trustAdjustment;
        int finalRiskScore = Math.max(0, Math.min(100, (int) Math.round(rawRiskScore)));

        // 7. Dynamic Decision mapping based on profile bounds
        String decision;
        String normalizedProfile = (activeProfile != null) ? activeProfile.trim().toLowerCase() : "balanced";

        switch (normalizedProfile) {
            case "conservative":
                if (finalRiskScore > 50) {
                    decision = "REJECTED";
                } else if (finalRiskScore >= 20) {
                    decision = "REVIEW";
                } else {
                    decision = "APPROVED";
                }
                break;
            case "aggressive":
                if (finalRiskScore > 90) {
                    decision = "REJECTED";
                } else if (finalRiskScore >= 60) {
                    decision = "REVIEW";
                } else {
                    decision = "APPROVED";
                }
                break;
            case "balanced":
            default:
                if (finalRiskScore > 70) {
                    decision = "REJECTED";
                } else if (finalRiskScore >= 30) {
                    decision = "REVIEW";
                } else {
                    decision = "APPROVED";
                }
                break;
        }

        // Apply dynamic trust penalty for rule violations
        if ("REJECTED".equals(decision)) {
            agentIdentityService.penalizeAgentTrust(agent.getAgentId(), 0.05);
        }

        // Record validated actions to improve behavioral learning models
        behavioralAnalysisService.recordAction(command, !"REJECTED".equals(decision));

        long evaluationTimeNs = System.nanoTime() - startTime;

        System.out.printf("[AgentGuard Trace] Profile: %s | Command: '%s' | Decision: %s | Score: %d | Agent: %s (Trust: %.2f) | Rule: %s | Latency: %.3f ms%n",
                normalizedProfile, command, decision, finalRiskScore, agent.getAgentId(), agent.getTrustScore(), matchedRuleId, evaluationTimeNs / 1_000_000.0);

        boolean isBlocked = "REJECTED".equals(decision);
        metricsService.incrementCommands(evaluationTimeNs, isBlocked);

        return new ValidationResult(decision, message, finalRiskScore, matchedRuleId, evaluationTimeNs);
    }
}
