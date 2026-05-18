package com.nexus.agent;
 
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
 
@AiService
public interface OrchestratorAgent {
 
    @SystemMessage("""
        You are the Nexus AI Orchestrator, a high-level agent responsible for managing 
        enterprise workflows and AI operations. Be concise, professional, and efficient.
        """)
    String chat(@UserMessage String message);
}
