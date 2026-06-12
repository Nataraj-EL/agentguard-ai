package com.agentguard.service;

import com.agentguard.model.ActivityLog;
import com.agentguard.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComplianceReportService {

    private final ActivityLogRepository activityLogRepository;

    public ComplianceReportService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Aggregates database event telemetry to generate a structured Enterprise Compliance Audit Digest.
     */
    public Map<String, Object> generateWeeklyComplianceReport() {
        List<ActivityLog> allLogs = activityLogRepository.findAll();
        
        long totalActions = allLogs.size();
        long commandCount = 0;
        long fileCount = 0;
        long blockedCount = 0;
        long reviewCount = 0;
        
        Map<String, Integer> rulesTriggered = new HashMap<>();

        for (ActivityLog log : allLogs) {
            String type = log.getEventType();
            String status = log.getStatus();
            String details = log.getDetails();

            if ("COMMAND_EXECUTED".equals(type)) {
                commandCount++;
            } else if ("FILE_MODIFIED".equals(type)) {
                fileCount++;
            }

            if ("REJECTED".equals(status)) {
                blockedCount++;
            } else if ("REVIEW".equals(status)) {
                reviewCount++;
            }

            // Extract matched rules from details text to aggregate trigger stats
            if (details != null && details.contains("[Rule: ")) {
                int start = details.indexOf("[Rule: ") + 7;
                int end = details.indexOf("]", start);
                if (end > start) {
                    String rule = details.substring(start, end);
                    rulesTriggered.put(rule, rulesTriggered.getOrDefault(rule, 0) + 1);
                }
            }
        }

        // Establish the compliance grading index based on blocked and reviewed alerts
        String complianceGrade = "A - EXCELLENT";
        if (blockedCount > 10) {
            complianceGrade = "C - NEED ATTENTION";
        } else if (blockedCount > 3) {
            complianceGrade = "B - SECURE";
        }

        return Map.of(
                "report_period", "Weekly Governance Summary",
                "total_actions_logged", totalActions,
                "commands_executed", commandCount,
                "files_modified", fileCount,
                "blocked_infractions", blockedCount,
                "flagged_reviews", reviewCount,
                "rules_triggered_distribution", rulesTriggered,
                "compliance_grade", complianceGrade,
                "governance_status", blockedCount > 5 ? "WARNING" : "COMPLIANT"
        );
    }
}
