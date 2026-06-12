package com.agentguard.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.logging.Logger;

@Service
public class OllamaRiskProvider implements RiskAssessmentProvider {

    private static final Logger logger = Logger.getLogger(OllamaRiskProvider.class.getName());
    private final RestClient restClient;

    public OllamaRiskProvider() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:11434/api")
                .build();
    }

    @Override
    public RiskAssessmentResult assessCommandRisk(String command, String context) {
        try {
            String prompt = String.format(
                    "You are a local developer security engine. Evaluate this shell execution command: '%s'. " +
                    "Respond ONLY with raw JSON matching this format: " +
                    "{\"riskScore\": <integer 0-100>, \"decision\": \"<APPROVED|REVIEW|REJECTED>\", \"explanation\": \"<short reason>\", \"triggeredRule\": \"<rule_id|none>\"}.",
                    command
            );

            Map<String, Object> requestBody = Map.of(
                    "model", "llama3", // Standard baseline local model
                    "prompt", prompt,
                    "stream", false
            );

            Map<?, ?> response = restClient.post()
                    .uri("/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                String responseText = (String) response.get("response");
                logger.info("[Ollama Provider] Local Model Response: " + responseText);
                return parseJsonResult(responseText);
            }
        } catch (Exception e) {
            logger.warning("[Ollama Provider] Local instance offline: " + e.getMessage() + ". Falling back to simulated mode.");
        }

        return simulateOllamaAssessment(command);
    }

    @Override
    public String generateExplainabilityLog(String command, String decision, int score, String ruleId) {
        try {
            String prompt = String.format(
                    "Generate a short 1-sentence explanation of why the command '%s' is categorized as %s with score %d (Rule: %s).",
                    command, decision, score, ruleId
            );

            Map<String, Object> requestBody = Map.of(
                    "model", "llama3",
                    "prompt", prompt,
                    "stream", false
            );

            Map<?, ?> response = restClient.post()
                    .uri("/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                return (String) response.get("response");
            }
        } catch (Exception e) {
            logger.warning("[Ollama Provider] Failed generating local explainability: " + e.getMessage());
        }

        return String.format("Local Policy Alert: Flagged command '%s' under rule %s.", command, ruleId);
    }

    private RiskAssessmentResult parseJsonResult(String rawText) {
        try {
            String cleanJson = rawText.substring(rawText.indexOf("{"), rawText.lastIndexOf("}") + 1).trim();
            
            // Manual parse
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
            return new RiskAssessmentResult(50, "REVIEW", "Parsed local model output failure.", "none");
        }
    }

    private RiskAssessmentResult simulateOllamaAssessment(String command) {
        if (command.contains("push --force") || command.contains("push -f")) {
            return new RiskAssessmentResult(95, "REJECTED", "Ollama: Destructive git force push operation is prohibited.", "destructive_git");
        } else if (command.contains("rm -rf")) {
            return new RiskAssessmentResult(98, "REJECTED", "Ollama: Destructive directory deletion is prohibited.", "destructive_rm");
        }
        return new RiskAssessmentResult(10, "APPROVED", "Ollama: Command evaluated as low operational risk.", "none");
    }
}
