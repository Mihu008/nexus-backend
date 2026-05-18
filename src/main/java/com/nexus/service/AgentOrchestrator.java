package com.nexus.service;

import com.nexus.ai.AutonomousAgent;
import com.nexus.model.entity.AgentTask;
import com.nexus.model.entity.Artifact;
import com.nexus.model.enums.LogLevel;
import com.nexus.model.enums.TaskStatus;
import com.nexus.repository.AgentTaskRepository;
import com.nexus.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AgentOrchestrator {

    private final AutonomousAgent autonomousAgent;
    private final TaskService taskService;
    private final AgentTaskRepository taskRepository;
    private final ArtifactRepository artifactRepository;

    /**
     * Executes the agentic workflow asynchronously using Virtual Threads.
     * Implements an exception-resilient self-correcting retry loop.
     * 
     * @param taskId The task to process.
     */
    @Async
    public void runAgenticWorkflow(UUID taskId) {
        log.info("Starting agentic workflow for task: {}", taskId);
        
        try {
            // 1. Fetch Task
            AgentTask task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found"));

            // 2. Update status to RUNNING
            taskService.updateTaskStatus(taskId, TaskStatus.RUNNING);
            taskService.addLog(taskId, LogLevel.INFO, "Agent workflow initialized.");

            // 3. Call AutonomousAgent.chat() with a robust self-correcting retry loop
            String response = null;
            int maxRetries = 3;
            int attempt = 0;
            String promptOverride = task.getPrompt();

            while (attempt < maxRetries) {
                try {
                    attempt++;
                    taskService.addLog(taskId, LogLevel.THOUGHT, 
                        "Consulting Gemini Sentinel Agent (Reasoning Loop Attempt " + attempt + " of " + maxRetries + ")...");
                    
                    // Prepend the active task UUID context so that the LLM is fully aware of its transaction boundary
                    String agentPrompt = "Active Task ID Context: " + taskId + "\n\nUser Instruction:\n" + promptOverride;
                    response = autonomousAgent.chat(taskId, agentPrompt);
                    break; // Success!
                } catch (Exception toolException) {
                    log.warn("Agentic attempt {} failed for task {}: {}", attempt, taskId, toolException.getMessage());
                    taskService.addLog(taskId, LogLevel.ERROR, 
                        "Reasoning anomaly on attempt " + attempt + ": " + toolException.getMessage());
                    
                    if (attempt >= maxRetries) {
                        throw new RuntimeException("Maximum self-correcting retry attempts exhausted. Final exception: " + toolException.getMessage(), toolException);
                    }
                    
                    // Pause briefly to allow transactions/file-systems to relax
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    
                    // Build a detailed diagnostic hint containing the exact Java/reflection exception message
                    promptOverride = task.getPrompt() + "\n\n" +
                        "--- DIAGNOSTIC SELF-CORRECTION PROTOCOL ---\n" +
                        "Your previous execution attempt failed with the following error: " + toolException.getMessage() + "\n" +
                        "Please analyze this error, self-correct your arguments, and try again. Follow these strict rules:\n" +
                        "1. Ensure all task IDs passed are valid, clean 36-character UUID strings (e.g. '" + taskId + "').\n" +
                        "2. You MUST call postAgentLog with level 'THOUGHT' explaining your next action before calling any other tool.\n" +
                        "3. Ensure all parameters match their description schemas. Do not leave out required arguments.\n" +
                        "------------------------------------------";
                }
            }

            // 4. Save fallback artifact only if the agent didn't save one itself
            boolean hasArtifact = !artifactRepository.findByTaskId(taskId).isEmpty();
            if (!hasArtifact) {
                Artifact artifact = Artifact.builder()
                        .task(task)
                        .title("Sentinel Verification Report")
                        .content(response != null ? response : "Research complete. Verification complete.")
                        .artifactType("markdown")
                        .build();
                artifactRepository.save(artifact);
                taskService.addLog(taskId, LogLevel.SUCCESS, "Fallback report compiled and saved successfully.");
            } else {
                taskService.addLog(taskId, LogLevel.SUCCESS, "Agent verified: high-fidelity artifact saved successfully.");
            }

            // 5. Update status to COMPLETED
            taskService.updateTaskStatus(taskId, TaskStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Workflow failed for task {}: {}", taskId, e.getMessage());
            taskService.addLog(taskId, LogLevel.ERROR, "Critical Failure: " + e.getMessage());
            taskService.updateTaskStatus(taskId, TaskStatus.FAILED);
        }
    }
}
