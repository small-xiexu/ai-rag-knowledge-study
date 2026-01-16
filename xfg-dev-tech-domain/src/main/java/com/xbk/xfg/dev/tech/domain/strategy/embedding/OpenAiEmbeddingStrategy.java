package com.xbk.xfg.dev.tech.domain.strategy.embedding;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        // 自动处理 URL，Spring AI 会自动拼接 /v1/embeddings，需要去掉用户可能填写的后缀
        String baseUrl = config.getBaseUrl();
        if (baseUrl != null) {
            // 去掉末尾的斜杠
            baseUrl = baseUrl.replaceAll("/+$", "");
            // 去掉 /v1/embeddings 或 /embeddings 或 /v1 后缀
            if (baseUrl.endsWith("/v1/embeddings")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - "/v1/embeddings".length());
            } else if (baseUrl.endsWith("/embeddings")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - "/embeddings".length());
            } else if (baseUrl.endsWith("/v1")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - "/v1".length());
            }
        }
        log.info("创建 OpenAI Embedding 客户端: {}, baseUrl: {} (原始: {})",
                config.getEmbeddingModel(), baseUrl, config.getBaseUrl());
        OpenAiApi api = new OpenAiApi(baseUrl, config.getApiKey());
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(config.getEmbeddingModel())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }
}
