package com.xbk.xfg.dev.tech.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务进度 DTO
 *
 * @author xiexu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressDTO implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 总体进度 0-100
     */
    private int percentage;

    /**
     * 当前状态描述 ("正在克隆...", "正在解析 RAGController.java" ...)
     */
    private String statusDescription;

    /**
     * 任务状态 (PROCESSING, COMPLETED, FAILED)
     */
    private String state;

    /**
     * 错误信息 (如果有)
     */
    private String errorMessage;
}
