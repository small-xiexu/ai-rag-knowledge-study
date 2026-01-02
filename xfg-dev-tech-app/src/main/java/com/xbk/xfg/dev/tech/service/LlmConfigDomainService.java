package com.xbk.xfg.dev.tech.service;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.api.response.Response;
import com.xbk.xfg.dev.tech.factory.DynamicChatClientFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 大模型配置管理领域服务
 * DDD 架构 - 领域层，包含业务逻辑
 *
 * @author xiexu
 */
@Slf4j
@Service
public class LlmConfigDomainService {

    private static final String CONFIG_HASH_KEY = "llm:provider:configs";
    private static final String ACTIVE_CONFIG_KEY = "llm:provider:active";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private DynamicChatClientFactory dynamicChatClientFactory;

    public Response<List<LlmProviderConfigDTO>> getAllConfigs() {
        try {
            RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);
            String activeId = getActiveConfigId();

            List<LlmProviderConfigDTO> configs = new ArrayList<>(configMap.values());
            // 标记当前激活的配置
            configs.forEach(config -> config.setActive(config.getId().equals(activeId)));

            return Response.<List<LlmProviderConfigDTO>>builder()
                    .code("0000").info("查询成功").data(configs).build();
        } catch (Exception e) {
            log.error("获取配置列表失败", e);
            return Response.<List<LlmProviderConfigDTO>>builder()
                    .code("500").info("获取配置列表失败: " + e.getMessage()).build();
        }
    }

    public Response<LlmProviderConfigDTO> getConfigById(String id) {
        try {
            RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);
            LlmProviderConfigDTO config = configMap.get(id);

            if (config == null) {
                return Response.<LlmProviderConfigDTO>builder()
                        .code("4004").info("配置不存在").build();
            }

            config.setActive(id.equals(getActiveConfigId()));
            return Response.<LlmProviderConfigDTO>builder()
                    .code("0000").info("查询成功").data(config).build();
        } catch (Exception e) {
            log.error("获取配置失败", e);
            return Response.<LlmProviderConfigDTO>builder()
                    .code("500").info("获取配置失败: " + e.getMessage()).build();
        }
    }

    public Response<LlmProviderConfigDTO> createConfig(LlmProviderConfigDTO config) {
        try {
            // 生成唯一 ID
            config.setId(UUID.randomUUID().toString());
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());

            RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);
            configMap.put(config.getId(), config);

            log.info("创建配置成功: {} - {}", config.getId(), config.getName());
            return Response.<LlmProviderConfigDTO>builder()
                    .code("0000").info("创建成功").data(config).build();
        } catch (Exception e) {
            log.error("创建配置失败", e);
            return Response.<LlmProviderConfigDTO>builder()
                    .code("500").info("创建配置失败: " + e.getMessage()).build();
        }
    }

    public Response<LlmProviderConfigDTO> updateConfig(String id, LlmProviderConfigDTO config) {
        try {
            RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);

            if (!configMap.containsKey(id)) {
                return Response.<LlmProviderConfigDTO>builder()
                        .code("4004").info("配置不存在").build();
            }

            LlmProviderConfigDTO existing = configMap.get(id);
            config.setId(id);
            config.setCreatedAt(existing.getCreatedAt());
            config.setUpdatedAt(LocalDateTime.now());

            configMap.put(id, config);

            // 使缓存失效
            dynamicChatClientFactory.invalidateCache(id);

            log.info("更新配置成功: {}", id);
            return Response.<LlmProviderConfigDTO>builder()
                    .code("0000").info("更新成功").data(config).build();
        } catch (Exception e) {
            log.error("更新配置失败", e);
            return Response.<LlmProviderConfigDTO>builder()
                    .code("500").info("更新配置失败: " + e.getMessage()).build();
        }
    }

    public Response<Boolean> deleteConfig(String id) {
        try {
            RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);

            if (!configMap.containsKey(id)) {
                return Response.<Boolean>builder()
                        .code("4004").info("配置不存在").data(false).build();
            }

            // 不允许删除当前激活的配置
            if (id.equals(getActiveConfigId())) {
                return Response.<Boolean>builder()
                        .code("4003").info("不能删除当前激活的配置").data(false).build();
            }

            configMap.remove(id);
            dynamicChatClientFactory.invalidateCache(id);

            log.info("删除配置成功: {}", id);
            return Response.<Boolean>builder()
                    .code("0000").info("删除成功").data(true).build();
        } catch (Exception e) {
            log.error("删除配置失败", e);
            return Response.<Boolean>builder()
                    .code("500").info("删除配置失败: " + e.getMessage()).data(false).build();
        }
    }

    public Response<Boolean> activateConfig(String id) {
        try {
            RMap<String, LlmProviderConfigDTO> configMap = redissonClient.getMap(CONFIG_HASH_KEY);

            if (!configMap.containsKey(id)) {
                return Response.<Boolean>builder()
                        .code("4004").info("配置不存在").data(false).build();
            }

            RBucket<String> activeBucket = redissonClient.getBucket(ACTIVE_CONFIG_KEY);
            activeBucket.set(id);

            // 通知工厂更新激活配置
            dynamicChatClientFactory.onConfigActivated(id);

            log.info("激活配置成功: {}", id);
            return Response.<Boolean>builder()
                    .code("0000").info("激活成功").data(true).build();
        } catch (Exception e) {
            log.error("激活配置失败", e);
            return Response.<Boolean>builder()
                    .code("500").info("激活配置失败: " + e.getMessage()).data(false).build();
        }
    }

    public Response<LlmProviderConfigDTO> getActiveConfig() {
        try {
            String activeId = getActiveConfigId();
            if (activeId == null) {
                return Response.<LlmProviderConfigDTO>builder()
                        .code("4004").info("没有激活的配置").build();
            }

            return getConfigById(activeId);
        } catch (Exception e) {
            log.error("获取激活配置失败", e);
            return Response.<LlmProviderConfigDTO>builder()
                    .code("500").info("获取激活配置失败: " + e.getMessage()).build();
        }
    }

    public Response<Boolean> testConnection(LlmProviderConfigDTO config) {
        try {
            // 尝试创建客户端并发送简单请求
            boolean success = dynamicChatClientFactory.testConnection(config);

            return Response.<Boolean>builder()
                    .code(success ? "0000" : "5001")
                    .info(success ? "连接成功" : "连接失败")
                    .data(success)
                    .build();
        } catch (Exception e) {
            log.error("测试连接失败", e);
            return Response.<Boolean>builder()
                    .code("500").info("测试连接失败: " + e.getMessage()).data(false).build();
        }
    }

    private String getActiveConfigId() {
        RBucket<String> activeBucket = redissonClient.getBucket(ACTIVE_CONFIG_KEY);
        return activeBucket.get();
    }
}
