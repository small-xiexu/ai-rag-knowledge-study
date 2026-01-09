package com.xbk.xfg.dev.tech.api;

import com.xbk.xfg.dev.tech.api.dto.TaskProgressDTO;
import com.xbk.xfg.dev.tech.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author xiexu
 * @date 2026/1/1 17:13
 */
public interface IRAGService {

    /**
     * 返回知识库标签列表
     *
     * @return
     */
    Response<List<String>> queryRagTagList();

    /**
     * 上传知识库相关文件
     *
     * @param ragTag
     * @param files
     * @return
     */
    Response<String> uploadFile(String ragTag, List<MultipartFile> files);

    /**
     * 分析 Git 仓库
     *
     * @param repoUrl  Git 仓库地址
     * @param userName Git 用户名
     * @param token    Git 密码或token令牌
     * @return
     */
    Response<String> analyzeGitRepository(String repoUrl, String userName, String token);

    /**
     * 删除知识库标签
     * @param ragTag 标签名
     * @return 是否成功
     */
    Response<Boolean> deleteRagTag(String ragTag);

    /**
     * 查询指定知识库的向量数量
     */
    Response<Long> countByRagTag(String ragTag);

    /**
     * 查询任务进度
     * @param taskId 任务ID
     * @return 任务进度
     */
    Response<TaskProgressDTO> queryTaskProgress(String taskId);

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    Response<String> cancelTask(String taskId);
}
