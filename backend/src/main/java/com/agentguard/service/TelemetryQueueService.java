package com.agentguard.service;

import com.agentguard.model.ActivityLog;
import com.agentguard.repository.ActivityLogRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

@Service
public class TelemetryQueueService {

    private static final Logger logger = Logger.getLogger(TelemetryQueueService.class.getName());
    private final ActivityLogRepository activityLogRepository;
    
    // Non-blocking in-memory queue for telemetry ingestion
    private final BlockingQueue<ActivityLog> eventQueue = new LinkedBlockingQueue<>(10000);

    public TelemetryQueueService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Offers an event to the buffer. Returns instantly to guarantee sub-millisecond response latency.
     */
    public void queueEvent(ActivityLog log) {
        boolean offered = eventQueue.offer(log);
        if (!offered) {
            logger.warning("[Queue Service] Telemetry buffer queue is full! Dropping event: " + log.getActionName());
        }
    }

    /**
     * Drains queue and bulk-persists events into SQLite in a single transaction block every 1000ms.
     */
    @Scheduled(fixedRate = 1000)
    public void flushQueue() {
        if (eventQueue.isEmpty()) {
            return;
        }

        List<ActivityLog> batch = new ArrayList<>();
        eventQueue.drainTo(batch);

        if (!batch.isEmpty()) {
            try {
                activityLogRepository.saveAll(batch);
                logger.fine(String.format("[Queue Service] Successfully flushed %d events to SQLite.", batch.size()));
            } catch (Exception e) {
                logger.severe("[Queue Service] Failed to persist telemetry event batch: " + e.getMessage());
            }
        }
    }
}
