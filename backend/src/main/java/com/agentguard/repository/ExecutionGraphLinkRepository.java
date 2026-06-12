package com.agentguard.repository;

import com.agentguard.model.ExecutionGraphLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionGraphLinkRepository extends JpaRepository<ExecutionGraphLink, Long> {
    List<ExecutionGraphLink> findBySessionId(String sessionId);
}
