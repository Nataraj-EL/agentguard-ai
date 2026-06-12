package com.agentguard.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "forensic_events")
public class ForensicEventEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "action_name", nullable = false, length = 1024)
    private String actionName;

    @Column(nullable = false)
    private String decision;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "triggered_rule")
    private String triggeredRule;

    @Column(length = 2048)
    private String details;

    @Column(nullable = false, length = 1024)
    private String fingerprint;

    public ForensicEventEntry() {}

    public ForensicEventEntry(LocalDateTime timestamp, String sessionId, String eventType, String actionName, String decision, Integer riskScore, String triggeredRule, String details, String fingerprint) {
        this.timestamp = timestamp;
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.actionName = actionName;
        this.decision = decision;
        this.riskScore = riskScore;
        this.triggeredRule = triggeredRule;
        this.details = details;
        this.fingerprint = fingerprint;
    }

    // Getters
    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSessionId() { return sessionId; }
    public String getEventType() { return eventType; }
    public String getActionName() { return actionName; }
    public String getDecision() { return decision; }
    public Integer getRiskScore() { return riskScore; }
    public String getTriggeredRule() { return triggeredRule; }
    public String getDetails() { return details; }
    public String getFingerprint() { return fingerprint; }

    // Immutable design: NO SETTERS are created to prevent runtime tampering.

    /**
     * Active JPA Lifecycle Hook: Enforces strict database-level immutability.
     * Blocks Hibernate/JPA from ever executing UPDATE statements on this entity.
     */
    @PreUpdate
    public void blockUpdate() {
        throw new UnsupportedOperationException("Tamper Alert: Update operation is strictly prohibited on immutable forensic events.");
    }

    /**
     * Active JPA Lifecycle Hook: Enforces strict database-level immutability.
     * Blocks Hibernate/JPA from ever executing DELETE statements on this entity.
     */
    @PreRemove
    public void blockDelete() {
        throw new UnsupportedOperationException("Tamper Alert: Delete operation is strictly prohibited on immutable forensic events.");
    }
}
