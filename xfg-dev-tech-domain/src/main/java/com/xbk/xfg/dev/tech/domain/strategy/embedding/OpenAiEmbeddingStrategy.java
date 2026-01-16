package com.xbk.xfg.dev.tech.domain.strategy.embedding;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI/GLM Embedding 策略
 */
@Component
public class OpenAiEmbeddingStrategy implements EmbeddingStrategy {

    @Override
    public boolean supports(String providerType) {
        return "OPENAI".equalsIgnoreCase(providerType) 
            || "GLM".equalsIgnoreCase(providerType)
            || "DEEPSEEK".equalsIgnoreCase(providerType);
    }

    @Override
    public EmbeddingModel createEmbeddingModel(LlmProviderConfigDTO config) {
        if (!StringUtils.hasText(config.getEmbeddingModel())) {
            throw new IllegalArgumentException("OpenAI embedding 模型名称不能为空");
        }
        OpenAiApi api = new OpenAiApi(config.getBaseUrl(), config.getApiKey());
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(config.getEmbeddingModel())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }
}
