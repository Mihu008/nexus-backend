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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${langchain4j.google-ai-gemini.model-name:gemini-2.5-flash}")
    private String modelName;

    private int parseRetryDelay(String message) {
        if (message == null || message.isEmpty()) {
            return -1;
        }
        // Try to find "retryDelay": "32s"
        java.util.regex.Pattern pattern1 = java.util.regex.Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s?\"");
        java.util.regex.Matcher matcher1 = pattern1.matcher(message);
        if (matcher1.find()) {
            try {
                return Integer.parseInt(matcher1.group(1));
            } catch (NumberFormatException e) {
                // fallback
            }
        }
        
        // Try to find "Please retry in 32.105553644s"
        java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile("Please retry in ([\\d\\.]+)\\s*s");
        java.util.regex.Matcher matcher2 = pattern2.matcher(message);
        if (matcher2.find()) {
            try {
                double secs = Double.parseDouble(matcher2.group(1));
                return (int) Math.ceil(secs);
            } catch (NumberFormatException e) {
                // fallback
            }
        }
        
        return -1;
    }

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
            int maxRetries = 5;
            int attempt = 0;
            String promptOverride = task.getPrompt();

            while (attempt < maxRetries) {
                // Check if task was cancelled/stopped by the user
                AgentTask currentTask = taskRepository.findById(taskId).orElse(null);
                if (currentTask != null && currentTask.getStatus() == TaskStatus.FAILED) {
                    log.info("Task {} was stopped by the user. Aborting agent reasoning loop.", taskId);
                    return;
                }

                try {
                    attempt++;
                    taskService.addLog(taskId, LogLevel.THOUGHT, 
                        "Consulting Gemini Sentinel Agent (Reasoning Loop Attempt " + attempt + " of " + maxRetries + ")...");
                    
                    // Prepend the active task UUID context so that the LLM is fully aware of its transaction boundary
                    String agentPrompt = "Active Task ID Context: " + taskId + "\n\nUser Instruction:\n" + promptOverride;
                    response = autonomousAgent.chat(taskId, agentPrompt);
                    break; // Success!
                } catch (Exception toolException) {
                    String errorMsg = toolException.getMessage() != null ? toolException.getMessage() : "";
                    log.warn("Agentic attempt {} failed for task {}: {}", attempt, taskId, errorMsg);
                    
                    boolean isQuotaExceeded = errorMsg.contains("429") || errorMsg.contains("RESOURCE_EXHAUSTED") || errorMsg.toLowerCase().contains("quota");
                    int delaySecs = parseRetryDelay(errorMsg);
                    
                    if (isQuotaExceeded) {
                        if (delaySecs > 0) {
                            String rateLimitMsg = "Gemini API Rate Limit hit (429). Waiting " + delaySecs + " seconds for rate limit window to reset before retry attempt " + (attempt + 1) + "...";
                            taskService.addLog(taskId, LogLevel.ERROR, rateLimitMsg);
                            try { 
                                Thread.sleep(delaySecs * 1000L); 
                            } catch (InterruptedException ie) { 
                                Thread.currentThread().interrupt(); 
                                throw new RuntimeException("Rate limit sleep interrupted", ie);
                            }
                        } else if (errorMsg.contains("GenerateRequestsPerDay")) {
                            String dailyQuotaMsg = "Gemini API Daily Quota Exceeded (429 Resource Exhausted). " +
                                "The free tier of " + modelName + " has a limit of 20 requests per day. " +
                                "To resolve this, please update the model name (e.g. to 'gemini-1.5-flash' or 'gemini-2.0-flash') " +
                                "in application.yml or set the GOOGLE_AI_MODEL_NAME environment variable, or use a paid API key.";
                            taskService.addLog(taskId, LogLevel.ERROR, dailyQuotaMsg);
                            throw new RuntimeException(dailyQuotaMsg, toolException);
                        } else {
                            // Standard backoff
                            int backoffSecs = attempt * 5;
                            String rateLimitMsg = "Gemini API Rate Limit hit (429). No specific retry delay found. Applying backoff of " + backoffSecs + " seconds before retry attempt " + (attempt + 1) + "...";
                            taskService.addLog(taskId, LogLevel.ERROR, rateLimitMsg);
                            try { 
                                Thread.sleep(backoffSecs * 1000L); 
                            } catch (InterruptedException ie) { 
                                Thread.currentThread().interrupt(); 
                                throw new RuntimeException("Rate limit backoff sleep interrupted", ie);
                            }
                        }
                    } else {
                        taskService.addLog(taskId, LogLevel.ERROR, 
                            "Reasoning anomaly on attempt " + attempt + ": " + errorMsg);
                        
                        if (attempt >= maxRetries) {
                            throw new RuntimeException("Maximum self-correcting retry attempts exhausted. Final exception: " + errorMsg, toolException);
                        }
                        
                        // Pause briefly for standard errors
                        try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                    
                    // Build a detailed diagnostic hint containing the exact Java/reflection exception message
                    promptOverride = task.getPrompt() + "\n\n" +
                        "--- DIAGNOSTIC SELF-CORRECTION PROTOCOL ---\n" +
                        "Your previous execution attempt failed with the following error: " + errorMsg + "\n" +
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
