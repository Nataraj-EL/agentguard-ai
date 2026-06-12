package com.agentguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class GeminiRiskProvider implements RiskAssessmentProvider {

    private static final Logger logger = Logger.getLogger(GeminiRiskProvider.class.getName());
    private final RestClient restClient;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    public GeminiRiskProvider() {
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    @Override
    public RiskAssessmentResult assessCommandRisk(String command, String context) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warning("[Gemini Provider] GEMINI_API_KEY environment variable is empty. Simulating Gemini evaluation (offline fallback).");
            return simulateGeminiAssessment(command);
        }

        try {
            String prompt = String.format(
                    "You are the AgentGuard AI security policy manager. Analyze the following shell command executed by an autonomous coding agent. " +
                    "Command: '%s'. Context: '%s'. " +
                    "Return a JSON response matching exactly this format: " +
                    "{\"riskScore\": <integer 0-100>, \"decision\": \"<APPROVED|REVIEW|REJECTED>\", \"explanation\": \"<brief reason>\", \"triggeredRule\": \"<banned_regex|none>\"}. " +
                    "Do not include any markdown styling or wrapper text. Only raw JSON.",
                    command, context
            );

            // Construct payload following Gemini API specifications
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of(
                                    "text", prompt
                            ))
                    ))
            );

            Map<?, ?> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/gemini-1.5-flash:generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                // Parse out response candidates -> parts -> text content
                List<?> candidates = (List<?>) response.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                    Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
                    List<?> parts = (List<?>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                        String rawJsonText = (String) firstPart.get("text");
                        
                        logger.info("[Gemini Provider] Ingested AI Classification: " + rawJsonText);
                        return parseJsonResult(rawJsonText);
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("[Gemini Provider] REST request failed: " + e.getMessage() + ". Falling back to simulated AI mode.");
        }

        return simulateGeminiAssessment(command);
    }

    @Override
    public String generateExplainabilityLog(String command, String decision, int score, String ruleId) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return String.format("Fast-path decision %s (Score: %d) triggered by local security rule: %s.", decision, score, ruleId);
        }

        try {
            String prompt = String.format(
                    "Provide a highly explainable, professional security trace audit string for developers detailing why " +
                    "the command '%s' was evaluated with decision: %s and risk score: %d (matched rule: %s). " +
                    "Explain the risks to environments, branches, and codebases clearly in 2 sentences.",
                    command, decision, score, ruleId
            );

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of(
                                    "text", prompt
                            ))
                    ))
            );

            Map<?, ?> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/gemini-1.5-flash:generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                List<?> candidates = (List<?>) response.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                    Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
                    List<?> parts = (List<?>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                        return (String) firstPart.get("text");
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("[Gemini Provider] Failed to generate AI explainability context: " + e.getMessage());
        }

        return String.format("Local Policy Alert: Matched rule %s with severity score of %d.", ruleId, score);
    }

    private RiskAssessmentResult parseJsonResult(String rawJson) {
        try {
            // Clean up potentially wrapped markdown markers from JSON outputs
            String cleanJson = rawJson.replace("```json", "").replace("```", "").trim();
            // Perform basic manual JSON parse to avoid adding additional dependencies
            int scoreIndex = cleanJson.indexOf("\"riskScore\":");
            int scoreEnd = cleanJson.indexOf(",", scoreIndex);
            int score = Integer.parseInt(cleanJson.substring(scoreIndex + 12, scoreEnd).trim());

            int decIndex = cleanJson.indexOf("\"decision\":");
            int decEnd = cleanJson.indexOf(",", decIndex);
            String decision = cleanJson.substring(decIndex + 11, decEnd).replace("\"", "").trim();

            int expIndex = cleanJson.indexOf("\"explanation\":");
            int expEnd = cleanJson.indexOf(",", expIndex);
            String explanation = cleanJson.substring(expIndex + 14, expEnd).replace("\"", "").trim();

            int ruleIndex = cleanJson.indexOf("\"triggeredRule\":");
            int ruleEnd = cleanJson.indexOf("}", ruleIndex);
            String rule = cleanJson.substring(ruleIndex + 16, ruleEnd).replace("\"", "").trim();

            return new RiskAssessmentResult(score, decision, explanation, rule);
        } catch (Exception e) {
            logger.warning("[Gemini Provider] JSON parse failed, utilizing baseline fallback extraction.");
            return new RiskAssessmentResult(50, "REVIEW", "Failed parsing raw AI classification model outputs.", "none");
        }
    }

    private RiskAssessmentResult simulateGeminiAssessment(String command) {
        if (command.contains("push --force") || command.contains("push -f")) {
            return new RiskAssessmentResult(95, "REJECTED", "Simulated Gemini: Destructive git force push operation is prohibited.", "destructive_git");
        } else if (command.contains("rm -rf")) {
            return new RiskAssessmentResult(98, "REJECTED", "Simulated Gemini: Destructive directory deletion is prohibited.", "destructive_rm");
        } else if (command.contains("cat .env")) {
            return new RiskAssessmentResult(60, "REVIEW", "Simulated Gemini: Access to env secret configurations requires explicit dashboard review.", "secret_access");
        }
        return new RiskAssessmentResult(10, "APPROVED", "Simulated Gemini: Command evaluated as low operational risk.", "none");
    }
}
