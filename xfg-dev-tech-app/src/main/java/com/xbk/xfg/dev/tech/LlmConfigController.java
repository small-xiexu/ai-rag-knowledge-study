package com.xbk.xfg.dev.tech;

import com.xbk.xfg.dev.tech.api.ILlmConfigService;
import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 大模型配置管理控制器
 * 
 * @author xiexu
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/llm/")
public class LlmConfigController {
    
    @Resource
    private ILlmConfigService llmConfigService;
    
    /**
     * 获取所有配置列表
     * GET /api/v1/llm/configs
     */
    @GetMapping("configs")
    public Response<List<LlmProviderConfigDTO>> getAllConfigs() {
        return llmConfigService.getAllConfigs();
    }
    
    /**
     * 获取单个配置详情
     * GET /api/v1/llm/configs/{id}
     */
    @GetMapping("configs/{id}")
    public Response<LlmProviderConfigDTO> getConfigById(@PathVariable("id") String id) {
        return llmConfigService.getConfigById(id);
    }
    
    /**
     * 新增配置
     * POST /api/v1/llm/configs
     */
    @PostMapping("configs")
    public Response<LlmProviderConfigDTO> createConfig(@RequestBody LlmProviderConfigDTO config) {
        return llmConfigService.createConfig(config);
    }
    
    /**
     * 更新配置
     * PUT /api/v1/llm/configs/{id}
     */
    @PutMapping("configs/{id}")
    public Response<LlmProviderConfigDTO> updateConfig(@PathVariable("id") String id, @RequestBody LlmProviderConfigDTO config) {
        return llmConfigService.updateConfig(id, config);
    }
    
    /**
     * 删除配置
     * DELETE /api/v1/llm/configs/{id}
     */
    @DeleteMapping("configs/{id}")
    public Response<Boolean> deleteConfig(@PathVariable("id") String id) {
        return llmConfigService.deleteConfig(id);
    }
    
    /**
     * 激活指定配置
     * POST /api/v1/llm/configs/{id}/activate
     */
    @PostMapping("configs/{id}/activate")
    public Response<Boolean> activateConfig(@PathVariable("id") String id) {
        return llmConfigService.activateConfig(id);
    }
    
    /**
     * 获取当前激活的配置
     * GET /api/v1/llm/configs/active
     */
    @GetMapping("configs/active")
    public Response<LlmProviderConfigDTO> getActiveConfig() {
        return llmConfigService.getActiveConfig();
    }
    
    /**
     * 测试配置连接
     * POST /api/v1/llm/configs/test
     */
    @PostMapping("configs/test")
    public Response<Boolean> testConnection(@RequestBody LlmProviderConfigDTO config) {
        return llmConfigService.testConnection(config);
    }
}
