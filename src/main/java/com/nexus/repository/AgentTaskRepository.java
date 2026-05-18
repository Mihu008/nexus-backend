package com.nexus.repository;

import com.nexus.model.entity.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import java.util.List;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {
    List<AgentTask> findAllByOrderByCreatedAtDesc();
}
