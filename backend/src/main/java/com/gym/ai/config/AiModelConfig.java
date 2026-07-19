package com.gym.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModelConfig {

    @Value("${ai.llm.api-key}")
    private String apiKey;

    @Value("${ai.llm.model}")
    private String modelName;

    @Value("${ai.llm.base-url}")
    private String baseUrl;

    @Bean
    public ChatLanguageModel dashScopeChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(0.7)
                .build();
    }
}
