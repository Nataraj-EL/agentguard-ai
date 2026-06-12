package com.agentguard.graph;

import com.agentguard.model.ActivityLog;
import com.agentguard.model.ExecutionGraphLink;
import com.agentguard.repository.ExecutionGraphLinkRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ExecutionGraphService {

    private final ExecutionGraphLinkRepository graphLinkRepository;
    
    // Causal state tracking maps
    private final ConcurrentHashMap<String, String> lastCommandPerSession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> commandFileWriteCounts = new ConcurrentHashMap<>();

    public ExecutionGraphService(ExecutionGraphLinkRepository graphLinkRepository) {
        this.graphLinkRepository = graphLinkRepository;
    }

    public void recordGraphLink(String sessionId, String sourceId, String sourceType, String targetId, String targetType, String relationship) {
        ExecutionGraphLink link = new ExecutionGraphLink(sessionId, sourceId, sourceType, targetId, targetType, relationship);
        graphLinkRepository.save(link);
    }

    /**
     * Integrates logs into the causal Execution DAG, employing Level-2 Clustered Abstractions 
     * to resolve the "noisy graph explosion" performance bottleneck on large file updates.
     */
    public void linkEventToGraph(ActivityLog log) {
        String sessionId = log.getSessionId();
        String eventType = log.getEventType();
        String nodeName = log.getActionName();
        String nodeId = String.valueOf(log.getId() != null ? log.getId() : nodeName.hashCode());

        if ("COMMAND_EXECUTED".equals(eventType)) {
            // Level 1 Linkage: Prompt to Command Node
            recordGraphLink(sessionId, sessionId, "PROMPT", nodeId, "COMMAND_EXECUTED", "OWNED_BY");
            lastCommandPerSession.put(sessionId, nodeId);
            commandFileWriteCounts.put(nodeId, new AtomicInteger(0));
        } else if ("FILE_MODIFIED".equals(eventType)) {
            String lastCommandId = lastCommandPerSession.get(sessionId);
            if (lastCommandId != null) {
                AtomicInteger writeCount = commandFileWriteCounts.computeIfAbsent(lastCommandId, k -> new AtomicInteger(0));
                int currentWrites = writeCount.incrementAndGet();

                if (currentWrites < 5) {
                    // Level 3 Linkage: Detailed causal file-level logs for moderate writes
                    recordGraphLink(sessionId, lastCommandId, "COMMAND_EXECUTED", nodeId, "FILE_MODIFIED", "TRIGGERED_WRITE");
                } else if (currentWrites == 5) {
                    // Level 2 Linkage: Dynamically consolidate noisy leaf nodes into a single high-level Group Cluster
                    String clusterNodeId = "file-cluster-" + lastCommandId;
                    recordGraphLink(sessionId, lastCommandId, "COMMAND_EXECUTED", clusterNodeId, "FILE_CLUSTER", "TRIGGERED_CLUSTERED_WRITES");
                } else {
                    // Suppress additional duplicate lines to preserve graph readable integrity
                    // Clustered summaries are retrieved on-demand via regular event timelines.
                }
            } else {
                // Orphaned file modifications trace back directly to the session prompt
                recordGraphLink(sessionId, sessionId, "PROMPT", nodeId, "FILE_MODIFIED", "UNTRACKED_WRITE");
            }
        }
    }

    public List<ExecutionGraphLink> compileGraph(String sessionId) {
        return graphLinkRepository.findBySessionId(sessionId);
    }
}
