package com.xbk.xfg.dev.tech.domain.strategy.embedding;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Ollama Embedding 策略
 */
@Component
public class OllamaEmbeddingStrategy implements EmbeddingStrategy {

    @Override
    public boolean supports(String providerType) {
        return "OLLAMA".equalsIgnoreCase(providerType);
    }

    @Override
    public EmbeddingModel createEmbeddingModel(LlmProviderConfigDTO config) {
        if (!StringUtils.hasText(config.getEmbeddingModel())) {
            throw new IllegalArgumentException("Ollama embedding 模型名称不能为空");
        }
        OllamaApi api = new OllamaApi(config.getBaseUrl());
        OllamaOptions options = OllamaOptions.builder()
                .model(config.getEmbeddingModel())
                .build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();
    }
}
