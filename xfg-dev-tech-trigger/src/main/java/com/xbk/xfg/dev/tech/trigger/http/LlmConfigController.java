package com.xbk.xfg.dev.tech.trigger.http;

import com.xbk.xfg.dev.tech.api.ILlmConfigService;
import com.xbk.xfg.dev.tech.api.dto.EmbeddingActivationResultDTO;
import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.api.response.Response;
import com.xbk.xfg.dev.tech.domain.service.LlmConfigDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 大模型配置管理控制器
 * DDD 架构 - HTTP 适配器层，实现应用服务接口
 *
 * @author xiexu
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/llm/")
public class LlmConfigController implements ILlmConfigService {

    @Resource
    private LlmConfigDomainService llmConfigDomainService;

    /**
     * 获取所有配置列表
     * GET /api/v1/llm/configs
     */
    @Override
    @GetMapping("configs")
    public Response<List<LlmProviderConfigDTO>> getAllConfigs() {
        return llmConfigDomainService.getAllConfigs();
    }

    /**
     * 获取单个配置详情
     * GET /api/v1/llm/configs/{id}
     */
    @Override
    @GetMapping("configs/{id}")
    public Response<LlmProviderConfigDTO> getConfigById(@PathVariable("id") String id) {
        return llmConfigDomainService.getConfigById(id);
    }

    /**
     * 新增配置
     * POST /api/v1/llm/configs
     */
    @Override
    @PostMapping("configs")
    public Response<LlmProviderConfigDTO> createConfig(@RequestBody LlmProviderConfigDTO config) {
        return llmConfigDomainService.createConfig(config);
    }

    /**
     * 更新配置
     * PUT /api/v1/llm/configs/{id}
     */
    @Override
    @PutMapping("configs/{id}")
    public Response<LlmProviderConfigDTO> updateConfig(@PathVariable("id") String id, @RequestBody LlmProviderConfigDTO config) {
        return llmConfigDomainService.updateConfig(id, config);
    }

    /**
     * 删除配置
     * DELETE /api/v1/llm/configs/{id}
     */
    @Override
    @DeleteMapping("configs/{id}")
    public Response<Boolean> deleteConfig(@PathVariable("id") String id) {
        return llmConfigDomainService.deleteConfig(id);
    }

    /**
     * 激活指定配置
     * POST /api/v1/llm/configs/{id}/activate
     */
    @Override
    @PostMapping("configs/{id}/activate")
    public Response<Boolean> activateConfig(@PathVariable("id") String id) {
        return llmConfigDomainService.activateConfig(id);
    }

    /**
     * 激活 Embedding 配置
     * POST /api/v1/llm/configs/{id}/activate-embedding
     */
    @Override
    @PostMapping("configs/{id}/activate-embedding")
    public Response<EmbeddingActivationResultDTO> activateEmbeddingConfig(@PathVariable("id") String id,
                                                                          @RequestParam(value = "force", defaultValue = "false") boolean force) {
        return llmConfigDomainService.activateEmbeddingConfig(id, force);
    }

    /**
     * 获取当前激活的配置
     * GET /api/v1/llm/configs/active
     */
    @Override
    @GetMapping("configs/active")
    public Response<LlmProviderConfigDTO> getActiveConfig() {
        return llmConfigDomainService.getActiveConfig();
    }

    /**
     * 获取当前激活的 Embedding 配置
     * GET /api/v1/llm/configs/active-embedding
     */
    @Override
    @GetMapping("configs/active-embedding")
    public Response<LlmProviderConfigDTO> getActiveEmbeddingConfig() {
        return llmConfigDomainService.getActiveEmbeddingConfig();
    }

    /**
     * 测试配置连接
     * POST /api/v1/llm/configs/test
     */
    @Override
    @PostMapping("configs/test")
    public Response<Boolean> testConnection(@RequestBody LlmProviderConfigDTO config) {
        return llmConfigDomainService.testConnection(config);
    }
}
