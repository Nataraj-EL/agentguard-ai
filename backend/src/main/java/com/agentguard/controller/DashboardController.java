package com.agentguard.controller;

import com.agentguard.correlation.PromptActionCorrelationService;
import com.agentguard.graph.ExecutionGraphService;
import com.agentguard.identity.AgentIdentityService;
import com.agentguard.model.ActivityLog;
import com.agentguard.model.Session;
import com.agentguard.repository.ActivityLogRepository;
import com.agentguard.repository.SessionRepository;
import com.agentguard.service.ComplianceReportService;
import com.agentguard.service.EventStoreService;
import com.agentguard.service.MetricsService;
import com.agentguard.service.SessionTokenService;
import com.agentguard.service.YamlPolicyLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final SessionRepository sessionRepository;
    private final ActivityLogRepository activityLogRepository;
    private final SessionTokenService sessionTokenService;
    private final MetricsService metricsService;
    private final PromptActionCorrelationService promptActionCorrelationService;
    private final ExecutionGraphService executionGraphService;
    private final EventStoreService eventStoreService;
    private final ComplianceReportService complianceReportService;
    private final AgentIdentityService agentIdentityService;
    private final YamlPolicyLoader yamlPolicyLoader;

    @Value("${agentguard.profile:balanced}")
    private String activeProfile;

    @Value("${spring.datasource.url:jdbc:sqlite:agentguard.db}")
    private String datasourceUrl;

    public DashboardController(SessionRepository sessionRepository,
                               ActivityLogRepository activityLogRepository,
                               SessionTokenService sessionTokenService,
                               MetricsService metricsService,
                               PromptActionCorrelationService promptActionCorrelationService,
                               ExecutionGraphService executionGraphService,
                               EventStoreService eventStoreService,
                               ComplianceReportService complianceReportService,
                               AgentIdentityService agentIdentityService,
                               YamlPolicyLoader yamlPolicyLoader) {
        this.sessionRepository = sessionRepository;
        this.activityLogRepository = activityLogRepository;
        this.sessionTokenService = sessionTokenService;
        this.metricsService = metricsService;
        this.promptActionCorrelationService = promptActionCorrelationService;
        this.executionGraphService = executionGraphService;
        this.eventStoreService = eventStoreService;
        this.complianceReportService = complianceReportService;
        this.agentIdentityService = agentIdentityService;
        this.yamlPolicyLoader = yamlPolicyLoader;
    }

    @GetMapping("/events")
    public ResponseEntity<List<ActivityLog>> getEvents() {
        return ResponseEntity.ok(activityLogRepository.findAll());
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<Session>> getSessions() {
        return ResponseEntity.ok(sessionRepository.findAll());
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        return ResponseEntity.ok(Map.of(
                "total_commands", metricsService.getTotalCommands(),
                "blocked_commands", metricsService.getBlockedCommands(),
                "total_telemetry_events", metricsService.getTotalTelemetryEvents(),
                "avg_validation_latency_ms", metricsService.getAverageValidationLatencyMs()
        ));
    }

    /**
     * Endpoint to fetch the dynamic weekly enterprise compliance report.
     */
    @GetMapping("/compliance/report")
    public ResponseEntity<?> getComplianceReport() {
        return ResponseEntity.ok(complianceReportService.generateWeeklyComplianceReport());
    }

    @GetMapping("/sessions/{id}/correlation")
    public ResponseEntity<?> getSessionCorrelation(@PathVariable String id) {
        return ResponseEntity.ok(promptActionCorrelationService.generateCorrelationReport(id));
    }

    @GetMapping("/sessions/{id}/graph")
    public ResponseEntity<?> getSessionGraph(@PathVariable String id) {
        return ResponseEntity.ok(executionGraphService.compileGraph(id));
    }

    @GetMapping("/sessions/{id}/replay")
    public ResponseEntity<?> getSessionReplay(@PathVariable String id) {
        return ResponseEntity.ok(eventStoreService.replaySession(id));
    }

    @PostMapping("/sessions/start")
    public ResponseEntity<?> startSession(@RequestBody Map<String, String> payload) {
        String prompt = payload.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt field is required."));
        }

        sessionRepository.findByStatus("ACTIVE").ifPresent(session -> {
            session.setStatus("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });

        String newSessionId = UUID.randomUUID().toString();
        Session session = new Session(
                newSessionId,
                prompt,
                "ACTIVE",
                LocalDateTime.now()
        );
        sessionRepository.save(session);

        String token = sessionTokenService.generateCompositeToken("nataraj", "ubuntu-local", newSessionId);

        return ResponseEntity.ok(Map.of(
                "session", session,
                "token", token
        ));
    }

    @PostMapping("/sessions/stop")
    public ResponseEntity<?> stopSession() {
        Optional<Session> activeSession = sessionRepository.findByStatus("ACTIVE");
        if (activeSession.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No active session to terminate."));
        }

        Session session = activeSession.get();
        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        return ResponseEntity.ok(session);
    }

    @GetMapping("/identities")
    public ResponseEntity<?> getIdentities() {
        Set<String> sessionIds = new HashSet<>();
        sessionIds.add("development-human");

        sessionRepository.findAll().forEach(s -> sessionIds.add(s.getId()));
        activityLogRepository.findAll().forEach(l -> sessionIds.add(l.getSessionId()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (String sId : sessionIds) {
            AgentIdentityService.AgentIdentity identity = agentIdentityService.resolveAgentIdentity(sId, "ubuntu-local");
            result.add(Map.of(
                "agentId", identity.getAgentId(),
                "agentType", identity.getAgentType(),
                "trustScore", identity.getTrustScore()
            ));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        String storageMode = "SQLite";
        if (datasourceUrl != null && datasourceUrl.contains("postgresql")) {
            storageMode = "PostgreSQL";
        }

        int yamlRulesCount = 0;
        try {
            yamlRulesCount = yamlPolicyLoader.getDynamicRules().size();
        } catch (Exception e) {
            // Fallback
        }

        return ResponseEntity.ok(Map.of(
            "profile", (activeProfile != null ? activeProfile.toUpperCase() : "BALANCED"),
            "policyIngress", "policy.yaml (" + yamlRulesCount + " rules)",
            "storageMode", storageMode + " Immutable Ledger"
        ));
    }
}
