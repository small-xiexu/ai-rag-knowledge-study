package com.xbk.xfg.dev.tech.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 模型测试结果 DTO
 *
 * @author xiexu
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelTestResultDTO implements Serializable {
    /**
     * 模型名称
     */
    private String model;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息（如果失败）
     */
    private String errorInfo;
}
