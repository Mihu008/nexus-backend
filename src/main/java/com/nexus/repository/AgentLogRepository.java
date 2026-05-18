package com.nexus.repository;

import com.nexus.model.entity.AgentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentLogRepository extends JpaRepository<AgentLog, UUID> {
    
    /**
     * Fetches logs for a specific task ordered by creation time.
     * 
     * @param taskId The UUID of the task.
     * @return Ordered list of agent logs.
     */
    List<AgentLog> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
