package com.agentguard.identity;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class AgentIdentityService {

    public static class AgentIdentity {
        private final String agentId;
        private final String agentType; // IDE_AGENT, TERMINAL_USER, BACKGROUND_DAEMON
        private final double trustScore;

        public AgentIdentity(String agentId, String agentType, double trustScore) {
            this.agentId = agentId;
            this.agentType = agentType;
            this.trustScore = trustScore;
        }

        public String getAgentId() { return agentId; }
        public String getAgentType() { return agentType; }
        public double getTrustScore() { return trustScore; }
    }

    // Process counters for formal Trust Score calculation
    private final Map<String, LongAdder> agentTotalActions = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> agentApprovedActions = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> agentCumulativeRisk = new ConcurrentHashMap<>();

    public AgentIdentityService() {
        // Seed baseline historical metrics
        seedAgentMetrics("cursor", 50, 48, 500); // 96% approval rate, avg 10 risk
        seedAgentMetrics("windsurf", 30, 28, 300); // 93% approval rate, avg 10 risk
        seedAgentMetrics("claude", 20, 18, 250); // 90% approval rate, avg 12 risk
        seedAgentMetrics("development-human", 100, 100, 1000); // 100% approval rate
    }

    private void seedAgentMetrics(String prefix, int total, int approved, int riskSum) {
        LongAdder tAdder = new LongAdder(); tAdder.add(total);
        agentTotalActions.put(prefix, tAdder);

        LongAdder aAdder = new LongAdder(); aAdder.add(approved);
        agentApprovedActions.put(prefix, aAdder);

        LongAdder rAdder = new LongAdder(); rAdder.add(riskSum);
        agentCumulativeRisk.put(prefix, rAdder);
    }

    /**
     * Ingests action outputs to update the dynamic trust telemetry maps.
     */
    public void recordAgentAction(String agentPrefix, boolean approved, int riskScore) {
        agentTotalActions.computeIfAbsent(agentPrefix, k -> new LongAdder()).increment();
        if (approved) {
            agentApprovedActions.computeIfAbsent(agentPrefix, k -> new LongAdder()).increment();
        }
        agentCumulativeRisk.computeIfAbsent(agentPrefix, k -> new LongAdder()).add(riskScore);
    }

    /**
     * Resolves agent type and calculates Trust Score dynamically using a formal mathematical model:
     * Trust = (Ha * 0.4) + (Sc * 0.3) + (Wa * 0.2) + (Dt * 0.1)
     */
    public AgentIdentity resolveAgentIdentity(String sessionId, String deviceId) {
        String normalizedSession = (sessionId != null) ? sessionId.toLowerCase() : "development-human";

        String agentPrefix = "development-human";
        String agentId = "human-terminal";
        String agentType = "TERMINAL_USER";
        double agentWeight = 1.0; // Wa factor

        if (normalizedSession.contains("cursor")) {
            agentPrefix = "cursor";
            agentId = "cursor-ide";
            agentType = "IDE_AGENT";
            agentWeight = 0.9;
        } else if (normalizedSession.contains("windsurf")) {
            agentPrefix = "windsurf";
            agentId = "windsurf-ide";
            agentType = "IDE_AGENT";
            agentWeight = 0.9;
        } else if (normalizedSession.contains("claude")) {
            agentPrefix = "claude";
            agentId = "claude-cli";
            agentType = "BACKGROUND_DAEMON";
            agentWeight = 0.7;
        } else if (!"development-human".equals(normalizedSession)) {
            agentPrefix = "custom-script";
            agentId = "ai-agent-" + normalizedSession.substring(0, Math.min(8, normalizedSession.length()));
            agentType = "BACKGROUND_DAEMON";
            agentWeight = 0.7;
        }

        // Calculate factors
        long total = agentTotalActions.computeIfAbsent(agentPrefix, k -> new LongAdder()).sum();
        long approved = agentApprovedActions.computeIfAbsent(agentPrefix, k -> new LongAdder()).sum();
        long riskSum = agentCumulativeRisk.computeIfAbsent(agentPrefix, k -> new LongAdder()).sum();

        // 1. Historical Approval Rate (Ha - 40%)
        double ha = (total == 0) ? 1.0 : (double) approved / total;

        // 2. Command Safety (Sc - 30%)
        double avgRisk = (total == 0) ? 10.0 : (double) riskSum / total;
        double sc = Math.max(0.0, 1.0 - (avgRisk / 100.0));

        // 3. Agent Weight (Wa - 20%) -> Already resolved as agentWeight

        // 4. Time Decay Factor (Dt - 10%)
        // Simulates positive decay reinforcement. For every 10 safe commands, reward a +0.02 trust offset
        double dt = Math.min(1.0, 0.7 + ((double) (approved / 10) * 0.02));

        // Dynamic Trust Score Math
        double trustScore = (ha * 0.4) + (sc * 0.3) + (agentWeight * 0.2) + (dt * 0.1);
        double finalTrustScore = Math.max(0.10, Math.min(1.0, trustScore));

        return new AgentIdentity(agentId, agentType, finalTrustScore);
    }

    /**
     * Penalizes trust variables dynamically by adding artificial high risk metrics on infraction.
     */
    public void penalizeAgentTrust(String agentId, double penalty) {
        String prefix = agentId.split("-")[0];
        // Artificially inject high-risk operations to penalize the calculated avgRisk
        agentTotalActions.computeIfAbsent(prefix, k -> new LongAdder()).add(5);
        agentCumulativeRisk.computeIfAbsent(prefix, k -> new LongAdder()).add(400); // 400 total risk penalty points
    }
}
