package com.agentguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AgentGuardApplication implements org.springframework.boot.CommandLineRunner {

    private final com.agentguard.service.YamlPolicyLoader yamlPolicyLoader;

    public AgentGuardApplication(com.agentguard.service.YamlPolicyLoader yamlPolicyLoader) {
        this.yamlPolicyLoader = yamlPolicyLoader;
    }

    public static void main(String[] args) {
        SpringApplication.run(AgentGuardApplication.class, args);
    }

    @Override
    public void run(String... args) {
        // Load dynamic YAML policies on service startup
        yamlPolicyLoader.loadPolicy("policy.yaml");
    }
}
