package com.xbk.xfg.dev.tech.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Embedding 配置激活结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingActivationResultDTO implements Serializable {

    /**
     * 是否激活成功
     */
    private boolean success;

    /**
     * 是否需要清空知识库后才能激活
     */
    private boolean requireClearKnowledge;

    /**
     * 当前激活配置的向量维度（可能为空表示未激活过）
     */
    private Integer currentDimension;

    /**
     * 待激活配置的向量维度
     */
    private Integer newDimension;

    /**
     * 当前激活的配置
     */
    private LlmProviderConfigDTO currentConfig;

    /**
     * 待激活的配置
     */
    private LlmProviderConfigDTO newConfig;

    /**
     * 当前知识库数量（可选，用于前端提示）
     */
    private Long knowledgeCount;

    /**
     * 当前向量条目总数（可选，用于前端提示）
     */
    private Long vectorCount;
}
