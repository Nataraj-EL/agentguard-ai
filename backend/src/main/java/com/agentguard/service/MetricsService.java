package com.agentguard.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.LongAdder;

@Service
public class MetricsService {

    private final LongAdder totalCommands = new LongAdder();
    private final LongAdder blockedCommands = new LongAdder();
    private final LongAdder totalTelemetryEvents = new LongAdder();
    private final LongAdder cumulativeValidationTimeNs = new LongAdder();

    public void incrementCommands(long latencyNs, boolean blocked) {
        totalCommands.increment();
        cumulativeValidationTimeNs.add(latencyNs);
        if (blocked) {
            blockedCommands.increment();
        }
    }

    public void incrementTelemetry() {
        totalTelemetryEvents.increment();
    }

    public long getTotalCommands() {
        return totalCommands.sum();
    }

    public long getBlockedCommands() {
        return blockedCommands.sum();
    }

    public long getTotalTelemetryEvents() {
        return totalTelemetryEvents.sum();
    }

    public double getAverageValidationLatencyMs() {
        long count = totalCommands.sum();
        if (count == 0) return 0.0;
        // Convert total nanoseconds to milliseconds, divide by count
        return (cumulativeValidationTimeNs.sum() / 1_000_000.0) / count;
    }
}
