package com.xbk.xfg.dev.tech.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 大模型提供商配置 DTO
 * 
 * @author xiexu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmProviderConfigDTO implements Serializable {
    
    /** 唯一标识 (UUID) */
    private String id;
    
    /** 配置名称（如："OpenAI 官方"、"Claude Code"） */
    private String name;
    
    /** 提供商类型：OPENAI | OLLAMA | ANTHROPIC */
    private String providerType;
    
    /** API 地址 */
    private String baseUrl;
    
    /** API 密钥（Ollama 不需要） */
    private String apiKey;
    
    /** 默认模型名称 */
    private String defaultModel;
    
    /** 支持的模型列表 */
    private List<String> models;
    
    /** 是否为当前激活的配置 */
    private boolean active;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
