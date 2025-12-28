package com.xbk.xfg.dev.tech.api.dto;

import lombok.Data;

/**
 * Ollama API 请求参数
 * 
 * @author xiexu
 */
@Data
public class OllamaRequest {
    
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * 消息内容
     */
    private String message;
}
