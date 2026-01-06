package com.xbk.xfg.dev.tech.domain.strategy.impl;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.domain.factory.DynamicChatClientFactory.ChatClientWrapper;
import com.xbk.xfg.dev.tech.domain.strategy.ChatClientStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * OpenAI 客户端创建策略
 * 
 * 支持 OpenAI 官方 API 和兼容服务（如 DeepSeek、GLM、Gemini、OneAPI 等）
 * 
 * 注意：baseUrl 应该配置到 /v1 层级，如：
 * - OpenAI 官方：https://api.openai.com/v1
 * - 代理站：https://www.88code.ai/openai/v1
 * 
 * 系统会自动在 baseUrl 后追加 /chat/completions
 * 
 * @author xiexu
 */
@Slf4j
@Component
public class OpenAiChatClientStrategy implements ChatClientStrategy {

    @Override
    public boolean supports(String providerType) {
        String type = providerType.toUpperCase();
        return "OPENAI".equals(type) || "GLM".equals(type) || "DEEPSEEK".equals(type) || "GEMINI".equals(type);
    }

    @Override
    public ChatClientWrapper createClient(LlmProviderConfigDTO config) {
        // 规范化 baseUrl：去除末尾的 /chat/completions 和 /
        // 这样无论用户输入 https://api.siliconflow.cn/v1 还是 
        // https://api.siliconflow.cn/v1/chat/completions 都能正常工作
        String baseUrl = normalizeBaseUrl(config.getBaseUrl());
        log.info("创建 OpenAI 客户端: {}, baseUrl: {} (原始: {})", config.getName(), baseUrl, config.getBaseUrl());
        
        // 使用 builder 模式创建 OpenAiApi，设置 completionsPath 为 /chat/completions
        // 这样用户可以在 baseUrl 中完全控制路径（如 /openai/v1）
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(config.getApiKey())
                .completionsPath("/chat/completions")
                .build();
        
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getDefaultModel())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
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

    /**
     * 规范化 baseUrl
     * 自动去除末尾的 /chat/completions 和多余的斜杠
     * 这样无论用户输入什么格式的 URL 都能正常工作
     * 
     * 例如：
     * - https://api.siliconflow.cn/v1/chat/completions -> https://api.siliconflow.cn/v1
     * - https://api.openai.com/v1/ -> https://api.openai.com/v1
     */
    private String normalizeBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        String normalized = url.trim();
        
        // 去除末尾的 /chat/completions（不区分大小写）
        if (normalized.toLowerCase().endsWith("/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        
        // 去除末尾的斜杠
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        return normalized;
    }
}
