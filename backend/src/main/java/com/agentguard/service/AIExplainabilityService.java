package com.agentguard.service;

import com.agentguard.model.ActivityLog;
import com.agentguard.repository.ActivityLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
public class AIExplainabilityService {

    private static final Logger logger = Logger.getLogger(AIExplainabilityService.class.getName());
    private final GeminiRiskProvider geminiRiskProvider;
    private final ActivityLogRepository activityLogRepository;

    public AIExplainabilityService(GeminiRiskProvider geminiRiskProvider, ActivityLogRepository activityLogRepository) {
        this.geminiRiskProvider = geminiRiskProvider;
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Asynchronously generates explainability string and appends it to the ActivityLog on disk.
     */
    @Async
    public void enrichActivityWithAIExplanation(ActivityLog log, String ruleId) {
        String decision = log.getStatus();
        int score = log.getRiskScore();
        String command = log.getActionName();

        logger.info(String.format("[AI Explainability] Triggering background risk explanation for command: '%s'...", command));

        String explanation = geminiRiskProvider.generateExplainabilityLog(command, decision, score, ruleId);

        logger.info("[AI Explainability] Explanation generated: " + explanation);

        // Update the persisted log with explainability details
        log.setDetails(log.getDetails() + " | [AI Explanation: " + explanation + "]");
        activityLogRepository.save(log);
    }
}
