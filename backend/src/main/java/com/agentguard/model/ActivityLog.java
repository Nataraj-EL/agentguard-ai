package com.agentguard.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "event_type", nullable = false)
    private String eventType; // COMMAND_EXECUTED, FILE_MODIFIED, GIT_PUSH, PACKAGE_INSTALLED

    @Column(name = "action_name", nullable = false, length = 1024)
    private String actionName; // e.g. "git push --force", "/src/main/App.java"

    @Column(nullable = false)
    private String status; // APPROVED, REJECTED, REVIEW, PENDING

    @Column(length = 2048)
    private String details;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public ActivityLog() {}

    public ActivityLog(String sessionId, String eventType, String actionName, String status, String details, Integer riskScore, LocalDateTime timestamp) {
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.actionName = actionName;
        this.status = status;
        this.details = details;
        this.riskScore = riskScore;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
