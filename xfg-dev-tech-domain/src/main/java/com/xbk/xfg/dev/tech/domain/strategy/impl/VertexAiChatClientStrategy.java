package com.xbk.xfg.dev.tech.domain.strategy.impl;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.domain.factory.DynamicChatClientFactory.ChatClientWrapper;
import com.xbk.xfg.dev.tech.domain.strategy.ChatClientStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Vertex AI Gemini 客户端创建策略
 * 
 * 用于 Google Vertex AI 服务，支持 Gemini 模型
 * 注意：需要配置 Google Cloud 认证
 * 
 * @author xiexu
 */
@Slf4j
@Component
public class VertexAiChatClientStrategy implements ChatClientStrategy {

    @Override
    public boolean supports(String providerType) {
        return "VERTEX_AI".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatClientWrapper createClient(LlmProviderConfigDTO config) {
        log.info("创建 Vertex AI 客户端: {}", config.getName());
        
        // Vertex AI 需要 projectId 和 location，配置较为复杂
        // 这里先提供一个占位实现，后续需要完善 Google Cloud 认证配置
        log.warn("Vertex AI 客户端需要配置 Google Cloud 认证，请确保环境已正确设置");
        
        return new ChatClientWrapper() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException("Vertex AI 客户端需要完整配置 Google Cloud 认证后才能使用");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                throw new UnsupportedOperationException("Vertex AI 客户端需要完整配置 Google Cloud 认证后才能使用");
            }
        };
    }
}
