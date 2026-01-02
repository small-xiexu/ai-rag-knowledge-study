package com.xbk.xfg.dev.tech.api;

import com.xbk.xfg.dev.tech.api.dto.LlmProviderConfigDTO;
import com.xbk.xfg.dev.tech.api.response.Response;

import java.util.List;

/**
 * 大模型配置管理服务接口
 * 
 * @author xiexu
 */
public interface ILlmConfigService {
    
    /**
     * 获取所有配置列表
     */
    Response<List<LlmProviderConfigDTO>> getAllConfigs();
    
    /**
     * 获取单个配置详情
     * @param id 配置ID
     */
    Response<LlmProviderConfigDTO> getConfigById(String id);
    
    /**
     * 新增配置
     * @param config 配置信息
     */
    Response<LlmProviderConfigDTO> createConfig(LlmProviderConfigDTO config);
    
    /**
     * 更新配置
     * @param id 配置ID
     * @param config 配置信息
     */
    Response<LlmProviderConfigDTO> updateConfig(String id, LlmProviderConfigDTO config);
    
    /**
     * 删除配置
     * @param id 配置ID
     */
    Response<Boolean> deleteConfig(String id);
    
    /**
     * 激活指定配置
     * @param id 配置ID
     */
    Response<Boolean> activateConfig(String id);
    
    /**
     * 获取当前激活的配置
     */
    Response<LlmProviderConfigDTO> getActiveConfig();
    
    /**
     * 测试配置连接
     * @param config 配置信息
     */
    Response<Boolean> testConnection(LlmProviderConfigDTO config);
}
