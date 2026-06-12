package com.agentguard.behavior;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class BehavioralAnalysisService {

    private final Map<String, LongAdder> commandApprovalCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> commandTotalCounts = new ConcurrentHashMap<>();
    
    // Tracks the rolling revalidation count to enforce manual audits every 100 executions
    private final Map<String, LongAdder> commandRollingExecutions = new ConcurrentHashMap<>();
    
    // Tracks timestamps to enforce 5% daily auto-approval confidence decay
    private final Map<String, LocalDateTime> commandLastActivity = new ConcurrentHashMap<>();

    public BehavioralAnalysisService() {
        // Seed baseline human developer patterns
        seedDeveloperPattern("npm install", 500, 500);
        seedDeveloperPattern("git status", 300, 300);
        seedDeveloperPattern("git diff", 150, 150);
        seedDeveloperPattern("npm run dev", 200, 200);
    }

    private void seedDeveloperPattern(String pattern, int approved, int total) {
        LongAdder approveAdder = new LongAdder(); approveAdder.add(approved);
        commandApprovalCounts.put(pattern, approveAdder);

        LongAdder totalAdder = new LongAdder(); totalAdder.add(total);
        commandTotalCounts.put(pattern, totalAdder);

        commandLastActivity.put(pattern, LocalDateTime.now());
        commandRollingExecutions.put(pattern, new LongAdder());
    }

    /**
     * Records a validated action, updating timeline indicators.
     */
    public void recordAction(String command, boolean approved) {
        String basePattern = extractBasePattern(command);
        commandTotalCounts.computeIfAbsent(basePattern, k -> new LongAdder()).increment();
        commandRollingExecutions.computeIfAbsent(basePattern, k -> new LongAdder()).increment();
        
        if (approved) {
            commandApprovalCounts.computeIfAbsent(basePattern, k -> new LongAdder()).increment();
        }
        commandLastActivity.put(basePattern, LocalDateTime.now());
    }

    /**
     * Computes the confidence of auto-approval (0.0 to 1.0) incorporating Time Decay 
     * and a rolling 100-run Revalidation Gate to prevent silent bypasses.
     */
    public double calculateAutoApprovalConfidence(String command) {
        String basePattern = extractBasePattern(command);
        long total = commandTotalCounts.getOrDefault(basePattern, new LongAdder()).sum();
        long approved = commandApprovalCounts.getOrDefault(basePattern, new LongAdder()).sum();
        long rolling = commandRollingExecutions.getOrDefault(basePattern, new LongAdder()).sum();

        // 1. Revalidation Gate check: Every 100 executions, drop confidence to force manual re-review
        if (rolling > 0 && rolling % 100 == 0) {
            System.out.printf("[Behavioral Guard] Revalidation Gate triggered for '%s' (Execution Count: %d). Forcing review override.%n", basePattern, rolling);
            // Reset the counter immediately to allow rebuilding confidence after this single forced check
            commandRollingExecutions.getOrDefault(basePattern, new LongAdder()).reset();
            return 0.10; // Forced low confidence triggers REVIEW state
        }

        if (total == 0) {
            return 0.20; // Default baseline trust
        }

        double baseConfidence = (double) approved / total;

        // 2. Time-Based Confidence Decay Check
        LocalDateTime lastActive = commandLastActivity.get(basePattern);
        if (lastActive != null) {
            long daysElapsed = Duration.between(lastActive, LocalDateTime.now()).toDays();
            if (daysElapsed > 0) {
                // Apply a strict 5% decay per day of inactivity
                double decayOffset = daysElapsed * 0.05;
                baseConfidence = Math.max(0.20, baseConfidence - decayOffset);
                System.out.printf("[Behavioral Guard] Inactivity decay applied to '%s': -%.2f confidence.%n", basePattern, decayOffset);
            }
        }

        return baseConfidence;
    }

    /**
     * Anomaly Check: Flag true if command is high-risk and rarely executed historically.
     */
    public boolean isBehavioralAnomaly(String command) {
        String basePattern = extractBasePattern(command);
        long total = commandTotalCounts.getOrDefault(basePattern, new LongAdder()).sum();
        
        boolean isDangerousKeyword = command.contains("rm -rf") || command.contains("--force") || command.contains("drop");
        return isDangerousKeyword && total < 3;
    }

    private String extractBasePattern(String command) {
        String trimmed = command.trim().toLowerCase();
        if (trimmed.startsWith("npm install")) return "npm install";
        if (trimmed.startsWith("git push")) return "git push";
        if (trimmed.startsWith("rm ")) return "rm";
        if (trimmed.startsWith("git status")) return "git status";
        if (trimmed.startsWith("git diff")) return "git diff";
        if (trimmed.startsWith("npm run")) {
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 3) {
                return parts[0] + " " + parts[1] + " " + parts[2];
            }
            return "npm run";
        }
        
        String[] words = trimmed.split("\\s+");
        if (words.length >= 2) {
            return words[0] + " " + words[1];
        }
        return words.length > 0 ? words[0] : "unknown";
    }
}
