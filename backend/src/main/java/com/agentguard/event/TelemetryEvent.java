package com.agentguard.event;

import com.agentguard.model.ActivityLog;
import org.springframework.context.ApplicationEvent;

public class TelemetryEvent extends ApplicationEvent {
    private final ActivityLog activityLog;

    public TelemetryEvent(Object source, ActivityLog activityLog) {
        super(source);
        this.activityLog = activityLog;
    }

    public ActivityLog getActivityLog() {
        return activityLog;
    }
}
