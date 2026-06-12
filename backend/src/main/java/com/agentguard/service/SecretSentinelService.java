package com.agentguard.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SecretSentinelService {

    private static class SecretPattern {
        private final String name;
        private final Pattern pattern;

        public SecretPattern(String name, String regex) {
            this.name = name;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }

        public boolean matches(String content) {
            return pattern.matcher(content).find();
        }

        public String getName() { return name; }
    }

    private final List<SecretPattern> secretPatterns = new ArrayList<>();

    public SecretSentinelService() {
        // High-fidelity secret detection regex rules
        secretPatterns.add(new SecretPattern("Private SSL/SSH Key", "-----BEGIN (RSA|EC|DSA|GPG|OPENSSH)? PRIVATE KEY-----"));
        secretPatterns.add(new SecretPattern("AWS API Credentials", "(?i)(A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|ASCA|ASIA)[A-Z0-9]{16}"));
        secretPatterns.add(new SecretPattern("Stripe Secret API Key", "sk_live_[0-9a-zA-Z]{24}"));
        secretPatterns.add(new SecretPattern("Slack OAuth Access Token", "xox[bapr]-[0-9a-zA-Z]{10,12}"));
        secretPatterns.add(new SecretPattern("Generic High-Entropy Key Assignment", "(?i)(api_key|api-key|secret|token|password|auth_token)\\s*[:=]\\s*['\"][a-zA-Z0-9_\\-]{16,}['\"]"));
    }

    /**
     * Checks if target string payload contains leaked developer secrets.
     */
    public boolean scanForSecrets(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        for (SecretPattern sp : secretPatterns) {
            if (sp.matches(content)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identifies which specific secret classification rule was matched.
     */
    public String getTriggeredSecretPattern(String content) {
        if (content == null || content.isEmpty()) {
            return "none";
        }

        for (SecretPattern sp : secretPatterns) {
            if (sp.matches(content)) {
                return sp.getName();
            }
        }
        return "none";
    }
}
