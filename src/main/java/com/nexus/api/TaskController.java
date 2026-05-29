package com.nexus.api;

import com.nexus.model.entity.AgentLog;
import com.nexus.model.entity.AgentTask;
import com.nexus.model.entity.Artifact;
import com.nexus.repository.AgentLogRepository;
import com.nexus.repository.AgentTaskRepository;
import com.nexus.repository.ArtifactRepository;
import com.nexus.service.AgentOrchestrator;
import com.nexus.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TaskController {

    private final TaskService taskService;
    private final AgentOrchestrator agentOrchestrator;
    
    private final AgentTaskRepository taskRepository;
    private final AgentLogRepository logRepository;
    private final ArtifactRepository artifactRepository;

    /**
     * Entry point for executing an agentic workflow.
     * 
     * @param request The userId and the AI prompt.
     * @return 202 Accepted with the generated taskId.
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, UUID>> executeTask(@RequestBody TaskRequest request) {
        // 1. Create the task record (Status: PENDING)
        AgentTask task = taskService.createTask(request.userId(), request.prompt());
        
        // 2. Trigger the async agentic workflow
        agentOrchestrator.runAgenticWorkflow(task.getId());
        
        // 3. Return immediately
        return ResponseEntity.accepted().body(Map.of("taskId", task.getId()));
    }

    /**
     * Stops/cancels an executing task.
     */
    @PostMapping("/{taskId}/stop")
    public ResponseEntity<Map<String, String>> stopTask(@PathVariable UUID taskId) {
        taskService.updateTaskStatus(taskId, com.nexus.model.enums.TaskStatus.FAILED);
        taskService.addLog(taskId, com.nexus.model.enums.LogLevel.ERROR, "Task execution stopped by user.");
        return ResponseEntity.ok(Map.of("message", "Task stopped successfully."));
    }

    /**
     * Retrieves all tasks ordered by creation date descending.
     */
    @GetMapping
    public ResponseEntity<List<AgentTask>> getAllTasks() {
        return ResponseEntity.ok(taskRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * Retrieves all logs for a specific task.
     */
    @GetMapping("/{taskId}/logs")
    public ResponseEntity<List<AgentLog>> getTaskLogs(@PathVariable UUID taskId) {
        return ResponseEntity.ok(logRepository.findByTaskIdOrderByCreatedAtAsc(taskId));
    }

    /**
     * Retrieves a task's full status details, metrics, and associated artifacts.
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskDetails(@PathVariable UUID taskId) {
        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        
        List<Artifact> artifacts = artifactRepository.findByTaskId(taskId);
        
        return ResponseEntity.ok(Map.of(
                "task", task,
                "artifacts", artifacts
        ));
    }
}
