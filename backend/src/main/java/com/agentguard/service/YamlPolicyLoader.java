package com.agentguard.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class YamlPolicyLoader {

    private static final Logger logger = Logger.getLogger(YamlPolicyLoader.class.getName());

    public static class DynamicRule {
        private final String id;
        private final String action; // APPROVED, REVIEW, REJECTED
        private final String match;

        public DynamicRule(String id, String action, String match) {
            this.id = id;
            this.action = action;
            this.match = match;
        }

        public String getId() { return id; }
        public String getAction() { return action; }
        public String getMatch() { return match; }
    }

    private final List<DynamicRule> dynamicRules = new ArrayList<>();

    /**
     * Parses standard structured YAML policies synchronously.
     * Keeps execution sub-millisecond and lightweight for the fast-path loop.
     */
    public void loadPolicy(String filePath) {
        dynamicRules.clear();
        logger.info("[Yaml Loader] Loading dynamic enterprise policies from: " + filePath);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            String currentId = null;
            String currentAction = null;
            String currentMatch = null;

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                    continue;
                }

                if (trimmed.startsWith("- id:")) {
                    // Flush previous parsed rule if complete
                    if (currentId != null) {
                        dynamicRules.add(new DynamicRule(currentId, currentAction, currentMatch));
                    }
                    currentId = trimmed.substring(5).replace("\"", "").replace("'", "").trim();
                    currentAction = "REVIEW"; // Default action
                    currentMatch = null;
                } else if (trimmed.startsWith("action:")) {
                    currentAction = trimmed.substring(7).replace("\"", "").replace("'", "").trim();
                } else if (trimmed.startsWith("match:")) {
                    currentMatch = trimmed.substring(6).replace("\"", "").replace("'", "").trim();
                }
            }

            // Flush final rule
            if (currentId != null) {
                dynamicRules.add(new DynamicRule(currentId, currentAction, currentMatch));
            }

            logger.info(String.format("[Yaml Loader] Successfully parsed %d dynamic enterprise rules.", dynamicRules.size()));
        } catch (IOException e) {
            logger.warning("[Yaml Loader] Policy file unavailable (" + e.getMessage() + "). Utilizing default baseline rules.");
        }
    }

    public List<DynamicRule> getDynamicRules() {
        return dynamicRules;
    }
}
