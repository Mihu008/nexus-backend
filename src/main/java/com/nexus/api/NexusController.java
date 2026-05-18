package com.nexus.api;

import com.nexus.agent.OrchestratorAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nexus")
@RequiredArgsConstructor
public class NexusController {

    private final OrchestratorAgent orchestratorAgent;

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return orchestratorAgent.chat(request.message());
    }
}
