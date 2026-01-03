package com.xbk.xfg.dev.tech.domain.service;

import com.xbk.xfg.dev.tech.api.dto.TaskProgressDTO;
import com.xbk.xfg.dev.tech.api.response.Response;
import com.xbk.xfg.dev.tech.domain.repository.VectorStoreRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG 领域服务
 * 负责知识库管理的核心业务逻辑
 *
 * @author xiexu
 */
@Slf4j
@Service
public class RAGDomainService {

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private VectorStoreRepository vectorStoreRepository;

    /**
     * 【查询知识库标签列表接口】
     * 获取系统中所有已注册的 RAG 知识库标签。
     */
    public Response<List<String>> queryRagTagList() {
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder().code("0000").info("调用成功").data(elements).build();
    }

    /**
     * 【删除知识库标签接口】
     * 允许用户删除不再需要的知识库标签。
     *
     * <b>重构说明</b>
     * - 使用 VectorStoreRepository 代替直接写 SQL
     * - SQL 集中管理在 Repository 层
     * - Service 层只关注业务逻辑
     */
    public Response<Boolean> deleteRagTag(String ragTag) {
        try {
            // 1. 从向量数据库中删除（通过 Repository）
            int deletedRows = vectorStoreRepository.deleteByRagTag(ragTag);

            // 2. 从 Redis 列表中移除标签
            RList<String> elements = redissonClient.getList("ragTag");
            boolean removed = elements.remove(ragTag);

            return Response.<Boolean>builder()
                    .code("0000")
                    .info("删除成功 (清理向量: " + deletedRows + "条)")
                    .data(removed)
                    .build();
        } catch (Exception e) {
            log.error("删除知识库异常", e);
            return Response.<Boolean>builder()
                    .code("500")
                    .info("删除失败: " + e.getMessage())
                    .data(false)
                    .build();
        }
    }

    /**
     * 【上传知识库文件】
     * 将用户上传的文件解析、切块、向量化后存入 PgVector 数据库
     */
    public Response<String> uploadFile(String ragTag, List<MultipartFile> files) {
        log.info("上传知识库开始 {}", ragTag);

        for (MultipartFile file : files) {
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            List<Document> documents = documentReader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

            pgVectorStore.accept(documentSplitterList);

            RList<String> elements = redissonClient.getList("ragTag");
            if (!elements.contains(ragTag)) {
                elements.add(ragTag);
            }
        }

        log.info("上传知识库完成 {}", ragTag);
        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    /**
     * 【分析 Git 仓库 - 异步任务提交接口】
     */
    public Response<String> analyzeGitRepository(String repoUrl, String userName, String token) {
        String taskId = UUID.randomUUID().toString();

        TaskProgressDTO progress = TaskProgressDTO.builder()
                .taskId(taskId)
                .percentage(0)
                .statusDescription("准备开始克隆仓库...")
                .state("PROCESSING")
                .build();

        redissonClient.getBucket("task:progress:" + taskId).set(progress, 1, TimeUnit.HOURS);

        CompletableFuture.runAsync(() -> {
            processGitRepository(taskId, repoUrl, userName, token);
        });

        return Response.<String>builder().code("0000").info("任务已提交").data(taskId).build();
    }

    /**
     * 【查询任务进度接口】
     */
    public Response<TaskProgressDTO> queryTaskProgress(String taskId) {
        TaskProgressDTO progress =
                (TaskProgressDTO) redissonClient.getBucket("task:progress:" + taskId).get();

        if (progress == null) {
            return Response.<TaskProgressDTO>builder()
                    .code("4004").info("任务不存在或已过期").build();
        }

        return Response.<TaskProgressDTO>builder()
                .code("0000").info("查询成功").data(progress).build();
    }

    /**
     * 【取消任务接口】
     */
    public Response<String> cancelTask(String taskId) {
        redissonClient.getBucket("task:stop:" + taskId).set("STOP", 5, TimeUnit.MINUTES);
        return Response.<String>builder().code("0000").info("任务取消指令已下达").build();
    }

    /**
     * 【核心后台任务：处理 Git 仓库】
     */
    private void processGitRepository(String taskId, String repoUrl, String userName, String token) {
        String localPath = "./git-cloned-repo/" + taskId;
        RBucket<TaskProgressDTO> bucket = redissonClient.getBucket("task:progress:" + taskId);
        RBucket<String> stopSignal = redissonClient.getBucket("task:stop:" + taskId);

        try {
            String repoProjectName = extractProjectName(repoUrl);
            log.info("异步任务 {}: 开始分析 Git 仓库 {}", taskId, repoUrl);

            updateProgress(bucket, 5, "正在连接远程仓库...", "PROCESSING");
            FileUtils.deleteDirectory(new File(localPath));

            updateProgress(bucket, 10, "正在克隆代码 (这可能需要几分钟)...", "PROCESSING");

            if (stopSignal.isExists()) {
                throw new InterruptedException("用户取消任务");
            }

            Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(localPath))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                    .call();

            updateProgress(bucket, 30, "克隆完成，开始扫描文件...", "PROCESSING");

            AtomicInteger totalFiles = new AtomicInteger(0);
            Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (stopSignal.isExists()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (isValidFile(file)) {
                        totalFiles.incrementAndGet();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (stopSignal.isExists()) {
                throw new InterruptedException("用户取消任务");
            }

            log.info("任务 {}: 扫描到 {} 个文件", taskId, totalFiles.get());
            updateProgress(bucket, 35, "扫描完成，共 " + totalFiles.get() + " 个文件，开始解析...", "PROCESSING");

            AtomicInteger current = new AtomicInteger(0);
            int total = totalFiles.get() > 0 ? totalFiles.get() : 1;

            Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (stopSignal.isExists()) {
                        return FileVisitResult.TERMINATE;
                    }

                    if (!isValidFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    int c = current.incrementAndGet();
                    int p = 35 + (int) ((c * 60.0) / total);

                    if (c % 5 == 0 || p % 10 == 0) {
                        updateProgress(bucket, p, "正在解析: " + file.getFileName(), "PROCESSING");
                    }

                    try {
                        TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                        List<Document> documents = reader.get();

                        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                        documents.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));

                        pgVectorStore.accept(documentSplitterList);

                    } catch (Exception e) {
                        log.error("处理文件失败: " + file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (stopSignal.isExists()) {
                throw new InterruptedException("用户取消任务");
            }

            FileUtils.deleteDirectory(new File(localPath));

            RList<String> elements = redissonClient.getList("ragTag");
            if (!elements.contains(repoProjectName)) {
                elements.add(repoProjectName);
            }
            git.close();

            updateProgress(bucket, 100, "分析完成！", "COMPLETED");
            log.info("任务 {}: 分析完成", taskId);

        } catch (InterruptedException e) {
            log.warn("任务 {} 已被取消", taskId);
            updateProgress(bucket, 0, "任务已取消", "CANCELLED");
            try {
                FileUtils.deleteDirectory(new File(localPath));
            } catch (IOException ignored) {
            }
        } catch (Exception e) {
            log.error("任务 " + taskId + " 失败", e);
            updateProgress(bucket, 0, "任务失败: " + e.getMessage(), "FAILED");
            try {
                FileUtils.deleteDirectory(new File(localPath));
            } catch (IOException ignored) {
            }
        } finally {
            stopSignal.delete();
        }
    }

    private boolean isValidFile(Path file) {
        String pathStr = file.toString();

        if (pathStr.contains(".git/") ||
                pathStr.contains("/target/") ||
                pathStr.contains("/build/") ||
                pathStr.contains("/node_modules/") ||
                pathStr.contains("/dist/") ||
                pathStr.contains("/.idea/") ||
                pathStr.contains("/logs/") ||
                pathStr.contains("/.gradle/")) {
            return false;
        }

        try {
            long size = Files.size(file);
            if (size == 0 || size > 1024 * 1024) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        return pathStr.endsWith(".java") ||
                pathStr.endsWith(".xml") ||
                pathStr.endsWith(".yml") ||
                pathStr.endsWith(".yaml") ||
                pathStr.endsWith(".properties") ||
                pathStr.endsWith(".sql") ||
                pathStr.endsWith(".md") ||
                pathStr.endsWith(".txt");
    }

    private void updateProgress(RBucket<TaskProgressDTO> bucket, int percentage, String msg, String state) {
        TaskProgressDTO p = bucket.get();
        if (p == null) {
            return;
        }
        p.setPercentage(percentage);
        p.setStatusDescription(msg);
        p.setState(state);
        bucket.set(p, 1, TimeUnit.HOURS);
    }

    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }
}
