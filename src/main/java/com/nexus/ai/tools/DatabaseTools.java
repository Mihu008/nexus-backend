package com.nexus.ai.tools;

import com.nexus.model.entity.AgentTask;
import com.nexus.model.entity.Artifact;
import com.nexus.model.enums.LogLevel;
import com.nexus.model.enums.TaskStatus;
import com.nexus.repository.AgentTaskRepository;
import com.nexus.repository.ArtifactRepository;
import com.nexus.service.TaskService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class DatabaseTools {

    private final TaskService taskService;
    private final AgentTaskRepository taskRepository;
    private final ArtifactRepository artifactRepository;

    /**
     * Helper to safely parse task UUID strings passed from the LLM context.
     * Cleans double/single quotes and brackets, returning a clean Java UUID.
     */
    private UUID parseUuid(String taskIdStr) {
        if (taskIdStr == null || taskIdStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Task ID cannot be null or empty.");
        }
        try {
            // Strip quotes and brackets that the model might wrap the argument in
            String cleaned = taskIdStr.replace("\"", "")
                                      .replace("'", "")
                                      .replace("[", "")
                                      .replace("]", "")
                                      .trim();
            return UUID.fromString(cleaned);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID string format: '" + taskIdStr + 
                "'. Please provide a valid 36-character hexadecimal UUID (e.g. '0ca99402-9e92-4782-90ea-01afde8e83c3').");
        }
    }

    @Tool("""
        Updates the high-level progress state of a task in the database.
        Use this tool to inform the system and user of the active processing phase.
        
        Parameters:
        - taskIdStr: The task ID as a clean UUID string (e.g., '0ca99402-9e92-4782-90ea-01afde8e83c3').
        - status: The new progress state, which MUST be one of: 'RUNNING', 'COMPLETED', 'FAILED'.
        """)
    public void updateTaskStatus(String taskIdStr, String status) {
        log.info("AI calling tool: updateTaskStatus for task {} to {}", taskIdStr, status);
        UUID taskId = parseUuid(taskIdStr);
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status state cannot be null or empty.");
        }
        try {
            TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase().trim());
            taskService.updateTaskStatus(taskId, taskStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status state: '" + status + "'. Valid options are: RUNNING, COMPLETED, FAILED.");
        }
    }

    @Tool("""
        Use this tool ONLY when you have completed your full research loop and have compiled a 
        comprehensive, highly detailed final report or source code to be stored for the user in the vault.
        
        Parameters:
        - taskIdStr: The task ID as a clean UUID string (e.g., '0ca99402-9e92-4782-90ea-01afde8e83c3').
        - title: A professional, clear title describing the generated artifact.
        - content: The complete artifact payload (e.g. detailed Markdown reports with checklists, tables, and warnings, or standard source code files).
        - type: The presentation category style, which MUST be one of: 'markdown', 'code'.
        """)
    public void saveArtifact(String taskIdStr, String title, String content, String type) {
        log.info("AI calling tool: saveArtifact for task {}: {}", taskIdStr, title);
        UUID taskId = parseUuid(taskIdStr);
        
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Artifact title cannot be null or empty.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Artifact content payload cannot be null or empty.");
        }
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Artifact type category cannot be null or empty.");
        }
        
        String cleanType = type.toLowerCase().trim();
        if (!cleanType.equals("markdown") && !cleanType.equals("code")) {
            throw new IllegalArgumentException("Invalid artifact type: '" + type + "'. Valid options are: markdown, code.");
        }

        AgentTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found in database for ID: " + taskId));
        
        Artifact artifact = Artifact.builder()
                .task(task)
                .title(title)
                .content(content)
                .artifactType(cleanType)
                .build();
        
        artifactRepository.save(artifact);
    }

    @Tool("""
        Posts a detailed, incremental log message to the task audit trail.
        Use this tool to log your step-by-step reasoning process, tool invocations, or sub-step status updates.
        Rule: You MUST log a thought with level 'THOUGHT' explaining your next action BEFORE calling any other tool.
        
        Parameters:
        - taskIdStr: The task ID as a clean UUID string (e.g., '0ca99402-9e92-4782-90ea-01afde8e83c3').
        - level: The log level category, which MUST be one of: 'INFO', 'THOUGHT', 'TOOL_CALL', 'SUCCESS', 'ERROR'.
        - message: The diagnostic log text.
        """)
    public void postAgentLog(String taskIdStr, String level, String message) {
        log.info("AI calling tool: postAgentLog for task {}: [{}] {}", taskIdStr, level, message);
        UUID taskId = parseUuid(taskIdStr);
        
        if (level == null || level.trim().isEmpty()) {
            throw new IllegalArgumentException("Log level cannot be null or empty.");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Log message cannot be null or empty.");
        }

        try {
            LogLevel logLevel = LogLevel.valueOf(level.toUpperCase().trim());
            taskService.addLog(taskId, logLevel, message);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid log level: '" + level + "'. Valid options are: INFO, THOUGHT, TOOL_CALL, SUCCESS, ERROR.");
        }
    }
}
