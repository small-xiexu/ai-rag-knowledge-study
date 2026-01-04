package com.xbk.xfg.dev.tech.domain.strategy;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.domain.factory.DynamicChatClientFactory.ChatClientWrapper;

/**
 * 聊天客户端创建策略接口
 * 
 * 使用策略模式，每个大模型提供商实现自己的客户端创建策略
 * 
 * @author xiexu
 */
public interface ChatClientStrategy {

    /**
     * 判断是否支持指定的提供商类型
     * 
     * @param providerType 提供商类型（如 OPENAI, ANTHROPIC, OLLAMA 等）
     * @return true 如果支持该类型
     */
    boolean supports(String providerType);

    /**
     * 创建聊天客户端包装器
     * 
     * @param config 配置信息
     * @return 客户端包装器
     */
    ChatClientWrapper createClient(LlmProviderConfigDTO config);
}
