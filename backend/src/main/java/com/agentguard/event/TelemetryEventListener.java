package com.agentguard.event;

import com.agentguard.model.ActivityLog;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class TelemetryEventListener {

    private static final Logger logger = Logger.getLogger(TelemetryEventListener.class.getName());

    private final com.agentguard.service.AIExplainabilityService aiExplainabilityService;

    public TelemetryEventListener(com.agentguard.service.AIExplainabilityService aiExplainabilityService) {
        this.aiExplainabilityService = aiExplainabilityService;
    }

    @Async
    @EventListener
    public void handleTelemetryEvent(TelemetryEvent event) {
        ActivityLog log = event.getActivityLog();
        logger.info(String.format("[AgentGuard Slow-Path Async] Processing event %s of type %s for Session %s. Details: %s",
                log.getActionName(), log.getEventType(), log.getSessionId(), log.getDetails()));

        if ("COMMAND_EXECUTED".equals(log.getEventType()) && 
            ("REJECTED".equals(log.getStatus()) || "REVIEW".equals(log.getStatus()))) {
            
            // Extract the rule ID from the details text to feed the explainability engine
            String ruleId = "none";
            String details = log.getDetails();
            if (details != null && details.contains("[Rule: ")) {
                int ruleStart = details.indexOf("[Rule: ") + 7;
                int ruleEnd = details.indexOf("]", ruleStart);
                if (ruleEnd > ruleStart) {
                    ruleId = details.substring(ruleStart, ruleEnd);
                }
            }

            aiExplainabilityService.enrichActivityWithAIExplanation(log, ruleId);
        }
    }
}
