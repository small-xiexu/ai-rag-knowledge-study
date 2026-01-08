package com.xbk.xfg.dev.tech.domain.strategy.embedding;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Embedding 模型创建策略接口
 */
public interface EmbeddingStrategy {

    /**
     * 是否支持指定的提供商类型
     */
    boolean supports(String providerType);

    /**
     * 基于配置创建 EmbeddingModel
     */
    EmbeddingModel createEmbeddingModel(LlmProviderConfigDTO config);
}
