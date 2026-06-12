package com.agentguard.correlation;

import com.agentguard.model.ActivityLog;
import com.agentguard.model.Session;
import com.agentguard.repository.ActivityLogRepository;
import com.agentguard.repository.SessionRepository;
import com.agentguard.service.GeminiRiskProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

@Service
public class PromptActionCorrelationService {

    private static final Logger logger = Logger.getLogger(PromptActionCorrelationService.class.getName());
    private final SessionRepository sessionRepository;
    private final ActivityLogRepository activityLogRepository;
    private final GeminiRiskProvider geminiRiskProvider;

    public PromptActionCorrelationService(SessionRepository sessionRepository,
                                          ActivityLogRepository activityLogRepository,
                                          GeminiRiskProvider geminiRiskProvider) {
        this.sessionRepository = sessionRepository;
        this.activityLogRepository = activityLogRepository;
        this.geminiRiskProvider = geminiRiskProvider;
    }

    /**
     * Compiles session telemetry and generates a correlation impact summary report.
     * Incorporates a zero-failure resilient fallback pattern protecting against LLM offline outages.
     */
    public Map<String, Object> generateCorrelationReport(String sessionId) {
        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return Map.of("error", "Target session not found.");
        }

        Session session = sessionOpt.get();
        List<ActivityLog> logs = activityLogRepository.findBySessionIdOrderByTimestampDesc(sessionId);

        List<String> actions = new ArrayList<>();
        int maxRisk = 0;
        int commandCount = 0;
        int fileCount = 0;

        for (ActivityLog log : logs) {
            String type = log.getEventType();
            String name = log.getActionName();
            if ("COMMAND_EXECUTED".equals(type)) {
                actions.add("Executed: " + name);
                commandCount++;
            } else if ("FILE_MODIFIED".equals(type)) {
                actions.add("Modified File: " + name);
                fileCount++;
            }
            if (log.getRiskScore() != null) {
                maxRisk = Math.max(maxRisk, log.getRiskScore());
            }
        }

        // 1. Establish high-speed, 100% reliable deterministic summary (Baseline)
        String impactSummary = String.format("Deterministic Ingestion: Synchronized codebase in response to prompt: '%s'. Executed %d developmental commands and updated %d target file configurations.",
                session.getPrompt(), commandCount, fileCount);

        // 2. Resilient AI Layer: Attempt to enrich narrative in background via Gemini
        try {
            if (fileCount > 0 || commandCount > 0) {
                String aiSummary = geminiRiskProvider.generateExplainabilityLog(
                        session.getPrompt(), 
                        "APPROVED", 
                        maxRisk, 
                        "Prompt-Correlation-Audit"
                );
                if (aiSummary != null && !aiSummary.trim().isEmpty() && !aiSummary.startsWith("Local Policy Alert")) {
                    impactSummary = aiSummary.trim(); // Enrich with the beautiful AI narrative!
                }
            }
        } catch (Exception e) {
            logger.fine("[Correlation Service] Resilient Fallback Activated: AI provider unavailable (" + e.getMessage() + "). Utilizing deterministic summary.");
        }

        return Map.of(
                "sessionId", sessionId,
                "prompt", session.getPrompt(),
                "actions", actions,
                "impactSummary", impactSummary,
                "riskScore", maxRisk
        );
    }
}
