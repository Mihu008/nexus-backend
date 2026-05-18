package com.nexus.service;

import com.nexus.model.entity.AgentLog;
import com.nexus.model.entity.AgentTask;
import com.nexus.model.entity.Profile;
import com.nexus.model.enums.LogLevel;
import com.nexus.model.enums.TaskStatus;
import com.nexus.repository.AgentLogRepository;
import com.nexus.repository.AgentTaskRepository;
import com.nexus.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TaskService {

    private final AgentTaskRepository taskRepository;
    private final AgentLogRepository logRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public AgentTask createTask(UUID userId, String prompt) {
        Profile user = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        AgentTask task = AgentTask.builder()
                .user(user)
                .prompt(prompt)
                .status(TaskStatus.PENDING)
                .build();

        return taskRepository.save(task);
    }

    @Transactional
    public void updateTaskStatus(UUID taskId, TaskStatus status) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        
        task.setStatus(status);
        if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
            task.setCompletedAt(LocalDateTime.now());
        }
        taskRepository.save(task);
    }

    @Transactional
    public void addLog(UUID taskId, LogLevel level, String message) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        AgentLog log = AgentLog.builder()
                .task(task)
                .level(level)
                .message(message)
                .build();

        logRepository.save(log);
    }
}
