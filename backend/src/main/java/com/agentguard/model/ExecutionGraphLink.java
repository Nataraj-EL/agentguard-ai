package com.agentguard.model;

import jakarta.persistence.*;

@Entity
@Table(name = "execution_graph_links")
public class ExecutionGraphLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "source_node_id", nullable = false)
    private String sourceNodeId;

    @Column(name = "source_node_type", nullable = false)
    private String sourceNodeType; // PROMPT, COMMAND, FILE_CHANGE, GIT_OPERATION

    @Column(name = "target_node_id", nullable = false)
    private String targetNodeId;

    @Column(name = "target_node_type", nullable = false)
    private String targetNodeType;

    @Column(name = "relationship_type", nullable = false)
    private String relationshipType; // e.g. "CAUSED_BY", "TRIGGERED", "MODIFIED"

    public ExecutionGraphLink() {}

    public ExecutionGraphLink(String sessionId, String sourceNodeId, String sourceNodeType, String targetNodeId, String targetNodeType, String relationshipType) {
        this.sessionId = sessionId;
        this.sourceNodeId = sourceNodeId;
        this.sourceNodeType = sourceNodeType;
        this.targetNodeId = targetNodeId;
        this.targetNodeType = targetNodeType;
        this.relationshipType = relationshipType;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public String getSourceNodeType() { return sourceNodeType; }
    public void setSourceNodeType(String sourceNodeType) { this.sourceNodeType = sourceNodeType; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getTargetNodeType() { return targetNodeType; }
    public void setTargetNodeType(String targetNodeType) { this.targetNodeType = targetNodeType; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
}
