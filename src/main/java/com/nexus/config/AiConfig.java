package com.nexus.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return taskId -> MessageWindowChatMemory.withMaxMessages(20);
    }
}
