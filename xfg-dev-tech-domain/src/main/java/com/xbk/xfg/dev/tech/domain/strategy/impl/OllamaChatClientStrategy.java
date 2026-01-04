package com.xbk.xfg.dev.tech.domain.strategy.impl;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.domain.factory.DynamicChatClientFactory.ChatClientWrapper;
import com.xbk.xfg.dev.tech.domain.strategy.ChatClientStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Ollama 客户端创建策略
 * 
 * 用于本地部署的 Ollama 服务，无需 API Key
 * 
 * @author xiexu
 */
@Slf4j
@Component
public class OllamaChatClientStrategy implements ChatClientStrategy {

    @Override
    public boolean supports(String providerType) {
        return "OLLAMA".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatClientWrapper createClient(LlmProviderConfigDTO config) {
        log.info("创建 Ollama 客户端: {}", config.getName());
        
        OllamaApi api = new OllamaApi(config.getBaseUrl());
        OllamaOptions options = OllamaOptions.builder()
                .model(config.getDefaultModel())
                .build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();

        return new ChatClientWrapper() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return chatModel.call(prompt);
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return chatModel.stream(prompt);
            }
        };
    }
}
