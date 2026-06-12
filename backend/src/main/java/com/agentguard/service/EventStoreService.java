package com.agentguard.service;

import com.agentguard.model.ForensicEventEntry;
import com.agentguard.repository.ForensicEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventStoreService {

    private final ForensicEventRepository forensicEventRepository;

    public EventStoreService(ForensicEventRepository forensicEventRepository) {
        this.forensicEventRepository = forensicEventRepository;
    }

    /**
     * Appends an event securely to the immutable forensic log ledger.
     */
    public void appendToLedger(ForensicEventEntry entry) {
        forensicEventRepository.save(entry);
    }

    /**
     * Reconstructs and replays session execution history in strict chronological sequence.
     */
    public List<ForensicEventEntry> replaySession(String sessionId) {
        return forensicEventRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }
}
