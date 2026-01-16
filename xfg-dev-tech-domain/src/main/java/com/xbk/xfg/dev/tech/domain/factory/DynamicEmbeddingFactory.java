package com.xbk.xfg.dev.tech.domain.factory;

import com.xbk.xfg.dev.tech.api.dto.EmbeddingActivationResultDTO;
import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.domain.repository.VectorStoreRepository;
import com.xbk.xfg.dev.tech.domain.strategy.embedding.EmbeddingStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 动态 Embedding 工厂
 */
@Slf4j
@Component
public class DynamicEmbeddingFactory {

    private static final String CONFIG_HASH_KEY = "llm:provider:configs";
    private static final String ACTIVE_EMBEDDING_KEY = "llm:provider:active:embedding";
    private static final String RAG_TAG_KEY = "ragTag";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private List<EmbeddingStrategy> strategies;

    @Resource
    private VectorStoreRepository vectorStoreRepository;

    private volatile EmbeddingModel cachedEmbeddingModel;
    private volatile String activeConfigId;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * 获取当前激活的 EmbeddingModel
     */
    public EmbeddingModel getActiveEmbeddingModel() {
        rwLock.readLock().lock();
        try {
            String configId = getActiveEmbeddingConfigId();
            if (configId == null) {
                throw new IllegalStateException("没有激活的 Embedding 配置");
            }

            if (configId.equals(activeConfigId) && cachedEmbeddingModel != null) {
                return cachedEmbeddingModel;
            }

            LlmProviderConfigDTO config = getConfigById(configId);
            if (config == null) {
                throw new IllegalStateException("激活的 Embedding 配置不存在: " + configId);
            }

            cachedEmbeddingModel = createEmbeddingModel(config);
            activeConfigId = configId;
            log.info("创建新的 EmbeddingModel: {} - {}", config.getName(), config.getEmbeddingModel());
            return cachedEmbeddingModel;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 激活新的 Embedding 配置
     */
    public EmbeddingActivationResultDTO activateEmbeddingConfig(String configId, boolean force) {
        rwLock.writeLock().lock();
        try {
            LlmProviderConfigDTO newConfig = getConfigById(configId);
            if (newConfig == null) {
                throw new IllegalArgumentException("配置不存在: " + configId);
            }
            validateEmbeddingConfig(newConfig);

            String oldConfigId = getActiveEmbeddingConfigId();
            LlmProviderConfigDTO oldConfig = oldConfigId != null ? getConfigById(oldConfigId) : null;
            long knowledgeCount = getKnowledgeCount();
            long vectorCount = vectorStoreRepository.countAll();

            if (oldConfig != null && !Objects.equals(oldConfig.getEmbeddingDimension(), newConfig.getEmbeddingDimension())) {
                if (!force) {
                    return EmbeddingActivationResultDTO.builder()
                            .success(false)
                            .requireClearKnowledge(true)
                            .currentDimension(oldConfig.getEmbeddingDimension())
                            .newDimension(newConfig.getEmbeddingDimension())
                            .currentConfig(oldConfig)
                            .newConfig(newConfig)
                            .knowledgeCount(knowledgeCount)
                            .vectorCount(vectorCount)
                            .build();
                }
                clearAllKnowledge(newConfig.getEmbeddingDimension());
            }

            setActiveEmbeddingConfigId(configId);
            cachedEmbeddingModel = null;
            activeConfigId = null;

            return EmbeddingActivationResultDTO.builder()
                    .success(true)
                    .requireClearKnowledge(false)
                    .currentDimension(oldConfig != null ? oldConfig.getEmbeddingDimension() : null)
                    .newDimension(newConfig.getEmbeddingDimension())
                    .currentConfig(oldConfig)
                    .newConfig(newConfig)
                    .knowledgeCount(knowledgeCount)
                    .vectorCount(vectorCount)
                    .build();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取当前激活的 Embedding 配置
     */
    public LlmProviderConfigDTO getActiveEmbeddingConfig() {
        String activeId = getActiveEmbeddingConfigId();
        if (activeId == null) {
            return null;
        }
        return getConfigById(activeId);
    }

    /**
     * 配置变更时清理缓存，下一次使用时重新创建模型
     */
    public void invalidateCache(String configId) {
        rwLock.writeLock().lock();
        try {
            if (configId != null && configId.equals(activeConfigId)) {
                cachedEmbeddingModel = null;
                activeConfigId = null;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void validateEmbeddingConfig(LlmProviderConfigDTO config) {
        if (!StringUtils.hasText(config.getEmbeddingModel())) {
            throw new IllegalArgumentException("该配置未填写 embeddingModel");
        }
        if (config.getEmbeddingDimension() == null) {
            throw new IllegalArgumentException("embeddingDimension 不能为空");
        }
    }

    private EmbeddingModel createEmbeddingModel(LlmProviderConfigDTO config) {
        return strategies.stream()
                .filter(s -> s.supports(config.getProviderType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的提供商类型: " + config.getProviderType()))
                .createEmbeddingModel(config);
    }

    private void clearAllKnowledge(int newDimension) {
        boolean truncated = vectorStoreRepository.truncate();
        if (!truncated) {
            log.warn("清空向量表失败，请检查数据库连接");
        }
        // 修改向量维度
        boolean altered = vectorStoreRepository.alterVectorDimension(newDimension);
        if (!altered) {
            log.warn("修改向量维度失败，请检查数据库连接");
        }
        RList<String> ragTags = redissonClient.getList(RAG_TAG_KEY);
        if (!CollectionUtils.isEmpty(ragTags)) {
            ragTags.clear();
        }
        log.warn("所有知识库已被清空！向量数据已永久删除，维度已修改为 {}", newDimension);
    }

    private String getActiveEmbeddingConfigId() {
        if (activeConfigId != null) {
            return activeConfigId;
        }
        RBucket<String> bucket = redissonClient.getBucket(ACTIVE_EMBEDDING_KEY);
        activeConfigId = bucket.get();
        return activeConfigId;
    }

    private void setActiveEmbeddingConfigId(String configId) {
        RBucket<String> bucket = redissonClient.getBucket(ACTIVE_EMBEDDING_KEY);
        bucket.set(configId);
        activeConfigId = configId;
    }

    private LlmProviderConfigDTO getConfigById(String id) {
        RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);
        return configMap.get(id);
    }

    private long getKnowledgeCount() {
        RList<String> ragTags = redissonClient.getList(RAG_TAG_KEY);
        try {
            return ragTags.size();
        } catch (Exception e) {
            log.warn("统计知识库数量失败: {}", e.getMessage());
            return 0L;
        }
    }
}
