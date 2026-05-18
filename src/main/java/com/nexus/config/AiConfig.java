package com.nexus.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${google.ai.api-key}")
    private String apiKey;

    @Bean
    public ChatModel chatLanguageModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash") // Use supported Gemini 2.5 Flash model
                .temperature(0.7)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return taskId -> MessageWindowChatMemory.withMaxMessages(20);
    }
}
