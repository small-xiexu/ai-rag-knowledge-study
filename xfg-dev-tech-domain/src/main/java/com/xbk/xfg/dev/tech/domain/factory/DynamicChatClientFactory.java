package com.xbk.xfg.dev.tech.domain.factory;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import com.xbk.xfg.dev.tech.domain.strategy.ChatClientStrategy;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 动态 ChatClient 工厂 - 大模型动态切换的核心实现
 *
 * <h2>核心功能</h2>
 * 1. 根据 Redis 中存储的配置，动态创建不同提供商的 ChatClient
 * 2. 缓存已创建的客户端，避免重复创建，提高性能
 * 3. 支持运行时切换大模型，无需重启应用
 * 4. 使用读写锁保证线程安全
 *
 * <h2>支持的提供商</h2>
 * - OpenAI（官方或兼容服务，如 OneAPI、FastGPT）
 * - Anthropic（Claude）
 * - Ollama（本地部署）
 *
 * <h2>设计模式</h2>
 * 1. 工厂模式：根据配置动态创建不同类型的客户端
 * 2. 适配器模式：ChatClientWrapper 统一不同客户端的接口
 * 3. 单例模式：每个配置只创建一个客户端实例并缓存
 *
 * <h2>数据存储</h2>
 * Redis 中存储两类数据：
 * - llm:provider:configs (Hash)：所有配置的详细信息，key 为配置 ID
 * - llm:provider:active (String)：当前激活的配置 ID
 *
 * @author xiexu
 */
@Slf4j
@Component
public class DynamicChatClientFactory {

    // ==================== Redis 键常量 ====================

    /**
     * Redis Hash 键：存储所有大模型配置
     * 数据结构：Hash<配置ID, LlmProviderConfigDTO>
     * 示例：llm:provider:configs = {
     *   "uuid-1": { name: "OpenAI 官方", providerType: "OPENAI", ... },
     *   "uuid-2": { name: "Ollama 本地", providerType: "OLLAMA", ... }
     * }
     */
    private static final String CONFIG_HASH_KEY = "llm:provider:configs";

    /**
     * Redis String 键：存储当前激活的配置 ID
     * 数据结构：String
     * 示例：llm:provider:active = "uuid-1"
     */
    private static final String ACTIVE_CONFIG_KEY = "llm:provider:active";

    // ==================== 依赖注入 ====================

    /**
     * Redisson 客户端：用于访问 Redis
     * 为什么选择 Redisson？
     * 1. 提供丰富的数据结构操作（Map、Bucket 等）
     * 2. 自动序列化/反序列化 Java 对象
     * 3. 支持分布式锁、分布式缓存等高级功能
     */
    @Resource
    private RedissonClient redissonClient;

    /**
     * 客户端创建策略列表
     * Spring 会自动注入所有实现 ChatClientStrategy 接口的 Bean
     */
    @Resource
    private List<ChatClientStrategy> strategies;

    // ==================== 缓存与并发控制 ====================

    /**
     * 客户端缓存
     *
     * <b>为什么需要缓存？</b>
     * 1. 创建 ChatClient 涉及网络连接，成本较高
     * 2. 避免每次调用都创建新实例
     * 3. 提升响应速度
     *
     * <b>缓存策略</b>
     * - Key: 配置 ID
     * - Value: ChatClientWrapper（客户端包装器）
     * - 懒加载：第一次使用时创建，后续复用
     *
     * <b>为什么使用 ConcurrentHashMap？</b>
     * 支持高并发读写，线程安全
     */
    private final ConcurrentHashMap<String, ChatClientWrapper> clientCache = new ConcurrentHashMap<>();

    /**
     * 读写锁
     *
     * <b>为什么需要锁？</b>
     * 虽然 ConcurrentHashMap 已经是线程安全的，但以下场景需要额外保护：
     * 1. 切换激活配置时（写操作）需要保证原子性
     * 2. 读取激活配置 + 获取客户端这两步需要一起保护
     *
     * <b>为什么选择读写锁？</b>
     * - 读操作（getActiveChatClient）频率高，允许并发
     * - 写操作（onConfigActivated）频率低，独占
     * - 读写锁可以提高并发性能
     */
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * 当前激活的配置 ID（内存缓存）
     *
     * <b>为什么需要这个字段？</b>
     * 避免每次都从 Redis 读取激活的配置 ID，减少网络开销
     *
     * <b>为什么使用 volatile？</b>
     * 1. 保证多线程之间的可见性
     * 2. 当配置切换时，其他线程能立即看到新值
     * 3. 防止指令重排序
     */
    private volatile String activeConfigId;

    // ==================== 客户端包装器接口 ====================

    /**
     * ChatClient 包装器接口 - 适配器模式的核心
     *
     * <b>为什么需要包装器？</b>
     * OpenAiChatClient 和 OllamaChatClient 虽然都有 call() 和 stream() 方法，
     * 但它们没有共同的父接口，无法统一处理。通过包装器统一接口。
     *
     * <b>统一的好处</b>
     * 1. 业务代码不需要关心底层是哪个提供商
     * 2. 可以轻松切换不同的大模型
     * 3. 便于扩展新的提供商
     */
    public interface ChatClientWrapper {
        /**
         * 同步调用：发送提示词，等待完整响应
         *
         * @param prompt 提示词（包含用户消息和配置）
         * @return 完整的响应结果
         */
        ChatResponse call(Prompt prompt);

        /**
         * 流式调用：发送提示词，逐步返回响应（类似打字机效果）
         *
         * @param prompt 提示词（包含用户消息和配置）
         * @return 响应流（Reactive Streams）
         */
        Flux<ChatResponse> stream(Prompt prompt);
    }

    // ==================== 核心方法：获取激活的客户端 ====================

    /**
     * 获取当前激活的 ChatClient - 最常用的方法
     *
     * <b>执行流程</b>
     * 1. 加读锁（允许多个线程同时读取）
     * 2. 从 Redis 获取激活的配置 ID
     * 3. 从缓存中获取客户端（如果不存在则创建）
     * 4. 释放读锁
     * 5. 返回客户端包装器
     *
     * <b>缓存策略</b>
     * 使用 computeIfAbsent 原子性地完成"检查-创建-存储"三个步骤
     *
     * @return ChatClient 包装器
     * @throws IllegalStateException 如果没有激活的配置
     */
    public ChatClientWrapper getActiveChatClient() {
        // 加读锁：允许多个线程同时调用这个方法
        rwLock.readLock().lock();
        try {
            // 步骤1: 获取激活的配置 ID
            String configId = getActiveConfigId();
            if (configId == null) {
                throw new IllegalStateException("没有激活的模型配置，请先在模型配置页面添加并激活一个配置");
            }

            // 步骤2: 从缓存获取或创建客户端
            // computeIfAbsent 的好处：原子性地完成"检查是否存在 -> 不存在则创建"
            return clientCache.computeIfAbsent(configId, id -> {
                // 从 Redis 读取配置详情
                LlmProviderConfigDTO config = getConfigById(id);
                if (config == null) {
                    throw new IllegalStateException("找不到配置: " + id);
                }
                log.info("创建新的 ChatClient: {} - {}", config.getName(), config.getProviderType());
                // 根据配置创建客户端
                return createChatClient(config);
            });
        } finally {
            // 确保锁一定会被释放
            rwLock.readLock().unlock();
        }
    }

    // ==================== 配置切换方法 ====================

    /**
     * 激活新配置 - 大模型切换的核心方法
     *
     * <b>执行流程</b>
     * 1. 加写锁（独占，阻止其他线程读写）
     * 2. 更新内存中的激活配置 ID
     * 3. 预热：如果新配置的客户端不在缓存中，提前创建
     * 4. 释放写锁
     *
     * <b>为什么需要预热？</b>
     * 避免切换后第一次调用时等待客户端创建，提升用户体验
     *
     * <b>调用时机</b>
     * 用户在前端点击"激活"按钮时，Controller 调用此方法
     *
     * @param newConfigId 新激活的配置 ID
     */
    public void onConfigActivated(String newConfigId) {
        // 加写锁：独占访问，阻止其他线程读写
        rwLock.writeLock().lock();
        try {
            // 步骤1: 更新内存中的激活配置 ID（volatile 保证可见性）
            this.activeConfigId = newConfigId;

            // 步骤2: 预热新配置的客户端
            if (!clientCache.containsKey(newConfigId)) {
                LlmProviderConfigDTO config = getConfigById(newConfigId);
                if (config != null) {
                    // 提前创建客户端并放入缓存
                    clientCache.put(newConfigId, createChatClient(config));
                    log.info("预热 ChatClient 缓存: {}", config.getName());
                }
            }
        } finally {
            // 确保锁一定会被释放
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 清除配置缓存 - 配置更新/删除时调用
     *
     * <b>使用场景</b>
     * 1. 用户修改了配置（如更换 API Key）
     * 2. 用户删除了配置
     *
     * <b>为什么需要清除缓存？</b>
     * 确保下次使用时会用新的配置重新创建客户端
     *
     * @param configId 要清除的配置 ID
     */
    public void invalidateCache(String configId) {
        // 从缓存中移除
        clientCache.remove(configId);

        // 如果清除的是当前激活的配置，重置激活 ID
        if (configId.equals(activeConfigId)) {
            activeConfigId = null;
        }

        log.info("清除 ChatClient 缓存: {}", configId);
    }

    // ==================== 测试连接方法 ====================

    /**
     * 测试配置连接 - 用于验证配置是否正确
     *
     * <b>测试逻辑</b>
     * 1. 根据配置创建临时客户端（不缓存）
     * 2. 发送简单的测试消息 "hi"
     * 3. 检查是否能收到响应
     *
     * <b>使用场景</b>
     * 用户在添加配置后，点击"测试连接"按钮
     *
     * @param config 要测试的配置
     * @return true 连接成功，false 连接失败
     */
    /**
     * 测试配置连接 - 用于验证配置是否正确
     * 遍历测试所有配置的模型
     *
     * @param config 要测试的配置
     * @return 测试结果列表
     */
    public List<com.xbk.xfg.dev.tech.api.dto.ModelTestResultDTO> testConnection(LlmProviderConfigDTO config) {
        List<com.xbk.xfg.dev.tech.api.dto.ModelTestResultDTO> results = new java.util.ArrayList<>();
        
        // 1. 确定要测试的模型列表
        List<String> modelsToTest = new java.util.ArrayList<>();
        
        // 优先使用 models列表
        if (config.getModels() != null && !config.getModels().isEmpty()) {
            modelsToTest.addAll(config.getModels());
        } 
        // 其次使用 defaultModel
        else if (config.getDefaultModel() != null && !config.getDefaultModel().isEmpty()) {
            modelsToTest.add(config.getDefaultModel());
        }
        // 最后使用系统默认
        else {
            modelsToTest.add(getTestModel(config));
        }

        // 去重
        modelsToTest = modelsToTest.stream().distinct().toList();

        // 2. 遍历测试
        // 创建临时客户端（不缓存）
        ChatClientWrapper client;
        try {
             client = createChatClient(config);
        } catch (Exception e) {
            // 如果连客户端都创建失败，所有模型都标记为失败
            for (String model : modelsToTest) {
                results.add(com.xbk.xfg.dev.tech.api.dto.ModelTestResultDTO.builder()
                        .model(model)
                        .success(false)
                        .errorInfo("客户端创建失败: " + e.getMessage())
                        .build());
            }
            return results;
        }

        for (String model : modelsToTest) {
            try {
                // 构建测试提示词
                Prompt prompt;
                if ("OLLAMA".equalsIgnoreCase(config.getProviderType())) {
                    prompt = new Prompt("hi", OllamaOptions.builder().model(model).build());
                } else {
                    prompt = new Prompt("hi", OpenAiChatOptions.builder().model(model).build());
                }

                // 发送测试请求
                ChatResponse response = client.call(prompt);
                String content = response.getResult().getOutput().getText();
                
                log.info("测试连接成功! 提供商: {}, 模型: {}, 响应: {}", 
                        config.getProviderType(), model, content);
                
                results.add(com.xbk.xfg.dev.tech.api.dto.ModelTestResultDTO.builder()
                        .model(model)
                        .success(true)
                        .build());
                        
            } catch (Exception e) {
                log.warn("测试连接失败: 提供商: {}, 模型: {}, 错误: {}", 
                        config.getProviderType(), model, e.getMessage());
                
                results.add(com.xbk.xfg.dev.tech.api.dto.ModelTestResultDTO.builder()
                        .model(model)
                        .success(false)
                        .errorInfo(e.getMessage())
                        .build());
            }
        }
        
        return results;
    }

    /**
     * 获取测试连接使用的模型名称
     * 优先使用配置的默认模型或 models 列表中的第一个，否则根据提供商类型返回合理的默认值
     */
    private String getTestModel(LlmProviderConfigDTO config) {
        // 1. 优先使用配置的默认模型
        if (config.getDefaultModel() != null && !config.getDefaultModel().isEmpty()) {
            return config.getDefaultModel();
        }
        
        // 2. 尝试使用 models 列表中的第一个
        if (config.getModels() != null && !config.getModels().isEmpty()) {
            return config.getModels().get(0);
        }
        
        // 3. 根据提供商类型返回合理的默认模型
        return switch (config.getProviderType().toUpperCase()) {
            case "OLLAMA" -> "llama3:8b";
            case "ANTHROPIC" -> "claude-3-5-sonnet-20241022";  // Claude 模型
            case "GLM" -> "glm-4";  // 智谱模型
            case "DEEPSEEK" -> "deepseek-chat";  // DeepSeek 模型
            case "GEMINI" -> "gemini-2.0-flash";  // Google Gemini 模型
            default -> "gpt-4o-mini";  // OpenAI 兼容的便宜模型
        };
    }

    /**
     * 获取当前激活的提供商类型
     *
     * @return 提供商类型（OPENAI、OLLAMA、ANTHROPIC）
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
     * 获取当前激活配置的默认模型
     *
     * @return 默认模型名称，如果没有配置则返回 null
     */
    public String getActiveDefaultModel() {
        rwLock.readLock().lock();
        try {
            String configId = getActiveConfigId();
            if (configId == null) {
                return null;
            }
            LlmProviderConfigDTO config = getConfigById(configId);
            return config != null ? config.getDefaultModel() : null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ==================== 私有方法：客户端创建 ====================

    private ChatClientWrapper createChatClient(LlmProviderConfigDTO config) {
        return strategies.stream()
                .filter(s -> s.supports(config.getProviderType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的提供商类型: " + config.getProviderType()))
                .createClient(config);
    }

    // ==================== 私有方法：Redis 数据访问 ====================

    /**
     * 获取当前激活的配置 ID
     *
     * <b>缓存策略</b>
     * 1. 先检查内存缓存（activeConfigId 字段）
     * 2. 如果内存中没有，从 Redis 读取
     * 3. 读取后更新内存缓存
     *
     * @return 激活的配置 ID，如果没有则返回 null
     */
    private String getActiveConfigId() {
        // 先从内存缓存读取
        if (activeConfigId != null) {
            return activeConfigId;
        }

        // 从 Redis 读取
        RBucket<String> activeBucket = redissonClient.getBucket(ACTIVE_CONFIG_KEY);
        activeConfigId = activeBucket.get();
        return activeConfigId;
    }

    /**
     * 根据 ID 获取配置详情
     *
     * @param id 配置 ID
     * @return 配置对象，如果不存在则返回 null
     */
    private LlmProviderConfigDTO getConfigById(String id) {
        // 从 Redis Hash 中获取
        RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);
        return configMap.get(id);
    }
}
