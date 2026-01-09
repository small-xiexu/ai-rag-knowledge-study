package com.xbk.xfg.dev.tech.trigger.http;

import com.xbk.xfg.dev.tech.api.IRAGService;
import com.xbk.xfg.dev.tech.api.dto.TaskProgressDTO;
import com.xbk.xfg.dev.tech.api.response.Response;
import com.xbk.xfg.dev.tech.domain.service.RAGDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG 知识库管理控制器
 * DDD 架构 - HTTP 适配器层，实现应用服务接口
 *
 * @author xiexu
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

    @Resource
    private RAGDomainService ragDomainService;

    /**
     * 【查询知识库标签列表接口】
     * GET /api/v1/rag/query_rag_tag_list
     */
    @Override
    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    public Response<List<String>> queryRagTagList() {
        return ragDomainService.queryRagTagList();
    }

    /**
     * 【删除知识库标签接口】
     * POST /api/v1/rag/delete_rag_tag
     */
    @Override
    @RequestMapping(value = "delete_rag_tag", method = RequestMethod.POST)
    public Response<Boolean> deleteRagTag(@RequestParam("ragTag") String ragTag) {
        return ragDomainService.deleteRagTag(ragTag);
    }

    /**
     * 【查询知识库向量数量】
     * GET /api/v1/rag/tag_count
     */
    @Override
    @RequestMapping(value = "tag_count", method = RequestMethod.GET)
    public Response<Long> countByRagTag(@RequestParam("ragTag") String ragTag) {
        return ragDomainService.countByRagTag(ragTag);
    }

    /**
     * 【上传知识库文件】
     * POST /api/v1/rag/file/upload
     */
    @Override
    @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    public Response<String> uploadFile(@RequestParam("ragTag") String ragTag, @RequestParam("file") List<MultipartFile> files) {
        return ragDomainService.uploadFile(ragTag, files);
    }

    /**
     * 【分析 Git 仓库 - 异步任务提交接口】
     * POST /api/v1/rag/analyze_git_repository
     */
    @Override
    @RequestMapping(value = "analyze_git_repository", method = RequestMethod.POST)
    public Response<String> analyzeGitRepository(
            @RequestParam("repoUrl") String repoUrl,
            @RequestParam("userName") String userName,
            @RequestParam("token") String token) {
        return ragDomainService.analyzeGitRepository(repoUrl, userName, token);
    }

    /**
     * 【查询任务进度接口】
     * GET /api/v1/rag/query_task_progress
     */
    @Override
    @RequestMapping(value = "query_task_progress", method = RequestMethod.GET)
    public Response<TaskProgressDTO> queryTaskProgress(@RequestParam("taskId") String taskId) {
        return ragDomainService.queryTaskProgress(taskId);
    }

    /**
     * 【取消任务接口】
     * POST /api/v1/rag/cancel_task
     */
    @Override
    @RequestMapping(value = "cancel_task", method = RequestMethod.POST)
    public Response<String> cancelTask(@RequestParam("taskId") String taskId) {
        return ragDomainService.cancelTask(taskId);
    }
}
