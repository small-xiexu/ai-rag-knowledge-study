package com.xbk.xfg.dev.tech.domain.strategy.impl;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.domain.factory.DynamicChatClientFactory.ChatClientWrapper;
import com.xbk.xfg.dev.tech.domain.strategy.ChatClientStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Anthropic Claude 客户端创建策略
 * 
 * 使用 Anthropic 原生 API 格式调用 Claude 模型
 * 
 * @author xiexu
 */
@Slf4j
@Component
public class AnthropicChatClientStrategy implements ChatClientStrategy {

    @Override
    public boolean supports(String providerType) {
        return "ANTHROPIC".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatClientWrapper createClient(LlmProviderConfigDTO config) {
        log.info("创建 Anthropic 客户端: {}", config.getName());
        
        AnthropicApi api = new AnthropicApi(config.getBaseUrl(), config.getApiKey());
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(config.getDefaultModel())
                .build();
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(api)
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
