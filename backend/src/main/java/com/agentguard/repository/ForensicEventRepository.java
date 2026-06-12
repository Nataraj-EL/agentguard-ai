package com.agentguard.repository;

import com.agentguard.model.ForensicEventEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForensicEventRepository extends JpaRepository<ForensicEventEntry, Long> {
    List<ForensicEventEntry> findBySessionIdOrderByTimestampAsc(String sessionId);
}
