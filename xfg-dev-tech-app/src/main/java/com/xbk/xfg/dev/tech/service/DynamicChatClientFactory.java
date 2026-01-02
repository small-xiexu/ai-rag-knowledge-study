package com.xbk.xfg.dev.tech.service;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 动态 ChatClient 工厂
 * 根据 Redis 中的配置动态创建和缓存 ChatClient 实例
 * 
 * 因为 OpenAiChatClient 和 OllamaChatClient 都同时支持 call() 和 stream()，
 * 这里使用一个包装器接口来统一处理。
 * 
 * @author xiexu
 */
@Slf4j
@Component
public class DynamicChatClientFactory {
    
    private static final String CONFIG_HASH_KEY = "llm:provider:configs";
    private static final String ACTIVE_CONFIG_KEY = "llm:provider:active";
    
    @Resource
    private RedissonClient redissonClient;
    
    // 缓存客户端包装器
    private final ConcurrentHashMap<String, ChatClientWrapper> clientCache = new ConcurrentHashMap<>();
    
    // 读写锁
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    // 当前激活的配置 ID
    private volatile String activeConfigId;
    
    /**
     * 客户端包装器接口，统一 call 和 stream 方法
     */
    public interface ChatClientWrapper {
        ChatResponse call(Prompt prompt);
        Flux<ChatResponse> stream(Prompt prompt);
    }
    
    /**
     * 获取当前激活的 ChatClient
     */
    public ChatClientWrapper getActiveChatClient() {
        rwLock.readLock().lock();
        try {
            String configId = getActiveConfigId();
            if (configId == null) {
                throw new IllegalStateException("没有激活的模型配置，请先在模型配置页面添加并激活一个配置");
            }
            
            return clientCache.computeIfAbsent(configId, id -> {
                LlmProviderConfigDTO config = getConfigById(id);
                if (config == null) {
                    throw new IllegalStateException("找不到配置: " + id);
                }
                log.info("创建新的 ChatClient: {} - {}", config.getName(), config.getProviderType());
                return createChatClient(config);
            });
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 切换激活配置时调用
     */
    public void onConfigActivated(String newConfigId) {
        rwLock.writeLock().lock();
        try {
            this.activeConfigId = newConfigId;
            if (!clientCache.containsKey(newConfigId)) {
                LlmProviderConfigDTO config = getConfigById(newConfigId);
                if (config != null) {
                    clientCache.put(newConfigId, createChatClient(config));
                    log.info("预热 ChatClient 缓存: {}", config.getName());
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    /**
     * 配置更新/删除时，移除对应缓存
     */
    public void invalidateCache(String configId) {
        clientCache.remove(configId);
        if (configId.equals(activeConfigId)) {
            activeConfigId = null;
        }
        log.info("清除 ChatClient 缓存: {}", configId);
    }
    
    /**
     * 测试配置连接
     */
    public boolean testConnection(LlmProviderConfigDTO config) {
        try {
            ChatClientWrapper client = createChatClient(config);
            // 如果配置了默认模型，则使用该模型进行测试
            Prompt prompt;
            if (config.getDefaultModel() != null && !config.getDefaultModel().isEmpty()) {
                if ("OLLAMA".equalsIgnoreCase(config.getProviderType())) {
                   prompt = new Prompt("hi", OllamaOptions.create().withModel(config.getDefaultModel()));
                } else {
                   prompt = new Prompt("hi", OpenAiChatOptions.builder().withModel(config.getDefaultModel()).build());
                }
            } else {
                prompt = new Prompt("hi");
            }
            ChatResponse response = client.call(prompt);
            String content = response.getResult().getOutput().getContent();
            log.info("测试连接成功! 提供商: {}, 模型: {}, 响应: {}", 
                    config.getProviderType(), 
                    config.getDefaultModel() != null ? config.getDefaultModel() : "default", 
                    content);
            return true;
        } catch (Exception e) {
            log.warn("测试连接失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取当前激活的提供商类型
     */
    public String getActiveProviderType() {
        rwLock.readLock().lock();
        try {
            String configId = getActiveConfigId();
            if (configId == null) {
                return null;
            }
            LlmProviderConfigDTO config = getConfigById(configId);
            return config != null ? config.getProviderType() : null;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 根据配置创建 ChatClient 包装器
     */
    private ChatClientWrapper createChatClient(LlmProviderConfigDTO config) {
        return switch (config.getProviderType().toUpperCase()) {
            case "OPENAI", "ANTHROPIC" -> createOpenAiWrapper(config);
            case "OLLAMA" -> createOllamaWrapper(config);
            default -> throw new IllegalArgumentException("不支持的提供商类型: " + config.getProviderType());
        };
    }
    
    private ChatClientWrapper createOpenAiWrapper(LlmProviderConfigDTO config) {
        OpenAiApi api = new OpenAiApi(config.getBaseUrl(), config.getApiKey());
        OpenAiChatClient client = new OpenAiChatClient(api);
        return new ChatClientWrapper() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return client.call(prompt);
            }
            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return client.stream(prompt);
            }
        };
    }
    
    private ChatClientWrapper createOllamaWrapper(LlmProviderConfigDTO config) {
        OllamaApi api = new OllamaApi(config.getBaseUrl());
        OllamaChatClient client = new OllamaChatClient(api);
        return new ChatClientWrapper() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return client.call(prompt);
            }
            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return client.stream(prompt);
            }
        };
    }


    
    private String getActiveConfigId() {
        if (activeConfigId != null) {
            return activeConfigId;
        }
        RBucket<String> activeBucket = redissonClient.getBucket(ACTIVE_CONFIG_KEY);
        activeConfigId = activeBucket.get();
        return activeConfigId;
    }
    
    private LlmProviderConfigDTO getConfigById(String id) {
        RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);
        return configMap.get(id);
    }
}
