package com.xbk.xfg.dev.tech.trigger.http;

import com.xbk.xfg.dev.tech.api.IRAGService;
import com.xbk.xfg.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RBucket;
import com.xbk.xfg.dev.tech.api.dto.TaskProgressDTO;
import org.springframework.core.io.PathResource;


/**
 * @author xiexu
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * 【查询知识库标签列表接口】
     * 获取系统中所有已注册的 RAG 知识库标签。
     * <p>
     * 执行流程：
     * 1. 访问 Redis 的 "ragTag" 列表。
     * 2. 返回所有存储的项目名称或标签。
     * <p>
     * 作用：
     * 前端在"知识库列表"页面展示数据，或在"AI 对话"页面的下拉框中供用户选择对话上下文。
     *
     * @return 包含所有知识库标签的列表
     */
    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    @Override
    public Response<List<String>> queryRagTagList() {
        // 从 Redis 中获取 key 为 "ragTag" 的列表
        // RList 是 Redisson 提供的分布式列表，底层是 Redis 的 List 数据结构
        // 这里存储的是所有知识库的标签名称，比如 ["产品手册", "技术文档", "FAQ"]
        RList<String> elements = redissonClient.getList("ragTag");
        
        // 构建统一响应格式返回
        return Response.<List<String>>builder().code("0000").info("调用成功").data(elements).build();
    }

    /**
     * 【删除知识库标签接口】
     * 允许用户删除不再需要的知识库标签。
     * <p>
     * 执行流程：
     * 1. 接收前端传递的 ragTag 参数。
     * 2. 删除 PgVector 数据库中对应的向量数据 (Hard Delete)。
     * 3. 在 Redis 的 "ragTag" 列表中查找并移除该元素。
     * 4. 返回操作结果。
     * 
     * @param ragTag 要删除的标签名称
     * @return true 表示删除成功，false 表示标签不存在
     */
    @Override
    @RequestMapping(value = "delete_rag_tag", method = RequestMethod.POST)
    public Response<Boolean> deleteRagTag(@RequestParam String ragTag) {
        try {
            // 1. 从向量数据库中删除 (PostgreSQL)
            // vector_store 是 Spring AI PgVector 默认表名
            // metadata是 JSONB 类型，使用 ->> 提取字段值
            String sql = "DELETE FROM vector_store WHERE metadata->>'knowledge' = ?";
            int deletedRows = jdbcTemplate.update(sql, ragTag);
            log.info("已物理删除知识库 '{}' 对应的 {} 条向量数据", ragTag, deletedRows);

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
     * 这是 RAG 系统的数据准备阶段（Ingestion）
     *
     * @param ragTag 知识库标签名称（用于分类和后续检索过滤）
     * @param files  用户上传的文件列表（支持 PDF、Word、TXT 等格式）
     * @return 上传结果
     */
    @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam("file") List<MultipartFile> files) {
        // 记录上传开始日志，方便排查问题
        log.info("上传知识库开始 {}", ragTag);
        
        // 遍历用户上传的每一个文件
        for (MultipartFile file : files) {
            // ==================== 第一步：读取文档 ====================
            // 使用 Apache Tika 解析文件内容（Tika 支持 PDF、Word、TXT 等多种格式）
            // file.getResource() 将 MultipartFile 转换为 Spring 的 Resource 对象
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            
            // 调用 get() 方法获取解析后的文档列表
            // 每个 Document 对象包含：content（文本内容）和 metadata（元数据）
            List<Document> documents = documentReader.get();
            
            // ==================== 第二步：切割文档 ====================
            // 使用 TokenTextSplitter 将长文档切割成小块（Chunk）
            // 为什么要切？1. 大模型有 token 限制  2. 小块检索更精准
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            // ==================== 第三步：添加元数据标签 ====================
            // 给原始文档和切割后的文档块都打上知识库标签
            // 这个标签用于后续检索时过滤，比如只查"产品手册"相关的内容
            documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

            // ==================== 第四步：存入向量数据库 ====================
            // 【核心步骤！】调用 pgVectorStore.accept() 方法
            // 这一步会自动完成：
            //   1. 调用 Ollama 的 nomic-embed-text 模型，把每个文本块转成向量
            //   2. 把向量 + 原文 + 元数据 一起存入 PostgreSQL（pgvector 表）
            pgVectorStore.accept(documentSplitterList);

            // ==================== 第五步：记录标签到 Redis ====================
            // 将知识库标签存入 Redis 列表，用于前端展示可选的知识库
            RList<String> elements = redissonClient.getList("ragTag");
            
            // 判断标签是否已存在，避免重复添加
            if (!elements.contains(ragTag)) {
                elements.add(ragTag);
            }
        }

        // 记录上传完成日志
        log.info("上传知识库完成 {}", ragTag);
        
        // 返回成功响应
        return Response.<String>builder().code("0000").info("调用成功").build();
    }


    /**
     * 【分析 Git 仓库 - 异步任务提交接口】
     * 接收用户的 Git 仓库地址，立即返回一个任务 ID (taskId)，并启动后台线程处理耗时任务。
     * 
     * @param repoUrl 仓库 HTTPS 地址 (如 https://github.com/user/repo.git)
     * @param userName 用户名 (私有仓库需要)
     * @param token 访问令牌 (私有仓库需要)
     * @return 包含 taskId 的响应，前端后续凭此 ID 轮询进度
     */
    @RequestMapping(value = "analyze_git_repository", method = RequestMethod.POST)
    @Override
    public Response<String> analyzeGitRepository(
            @RequestParam("repoUrl") String repoUrl, 
            @RequestParam("userName") String userName, 
            @RequestParam("token") String token) {
        
        // 1. 生成全局唯一的任务 ID (UUID)
        String taskId = UUID.randomUUID().toString();
        
        // 2. 初始化任务进度对象，并存入 Redis
        // 状态: PROCESSING, 进度: 0%
        TaskProgressDTO progress = TaskProgressDTO.builder()
                .taskId(taskId)
                .percentage(0)
                .statusDescription("准备开始克隆仓库...")
                .state("PROCESSING")
                .build();
        
        // 存入 Redis，设置 1 小时过期 (防止僵尸任务占用内存)
        redissonClient.getBucket("task:progress:" + taskId).set(progress, 1, TimeUnit.HOURS);
        
        // 3. 启动异步线程执行实际的 Git 分析逻辑
        // CompletableFuture.runAsync 会使用 ForkJoinPool.commonPool() 线程池
        CompletableFuture.runAsync(() -> {
            processGitRepository(taskId, repoUrl, userName, token);
        });

        // 4. 立即返回任务 ID 给前端
        return Response.<String>builder().code("0000").info("任务已提交").data(taskId).build();
    }
    
    /**
     * 【查询任务进度接口】
     * 前端通过定时轮询 (Polling) 此接口，获取任务的实时状态 (百分比、当前步骤)。
     * 
     * @param taskId 任务 ID
     * @return 当前的任务进度对象
     */
    @RequestMapping(value = "query_task_progress", method = RequestMethod.GET)
    public Response<TaskProgressDTO> queryTaskProgress(@RequestParam("taskId") String taskId) {
        // 从 Redis 中取出最新的进度信息
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
     * 用户点击"取消"按钮时调用，向后台发送一个停止信号。
     * 
     * @param taskId 要取消的任务 ID
     */
    @RequestMapping(value = "cancel_task", method = RequestMethod.POST)
    public Response<String> cancelTask(@RequestParam("taskId") String taskId) {
        // 在 Redis 中设置一个特殊的 Key "task:stop:{taskId}"
        // 异步线程在运行过程中会不断检查这个 Key 是否存在，一旦发现存在，就会抛出异常立即停止
        redissonClient.getBucket("task:stop:" + taskId).set("STOP", 5, TimeUnit.MINUTES);
        return Response.<String>builder().code("0000").info("任务取消指令已下达").build();
    }

    /**
     * 后台处理逻辑
     */
    /**
     * 【核心后台任务：处理 Git 仓库】
     * 这是一个耗时操作，包含以下步骤：
     * 1. 准备工作：清理旧数据，初始化进度
     * 2. 克隆代码：使用 JGit 将远程仓库下载到本地临时目录
     * 3. 扫描文件：遍历本地目录，统计有效文件数量（用于计算进度）
     * 4. 解析入库：逐个读取文件 -> Tika 解析 -> Token 切分 -> 向量化 -> 存入 PgVector
     * 5. 收尾工作：清理临时文件，更新 Redis 状态
     * 
     * @param taskId 任务 ID
     * @param repoUrl 仓库地址
     * @param userName 用户名
     * @param token Token
     */
    private void processGitRepository(String taskId, String repoUrl, String userName, String token) {
        // 定义本地克隆的临时路径
        String localPath = "./git-cloned-repo/" + taskId;
        
        // 获取 Redis 中的进度对象和停止信号对象
        RBucket<TaskProgressDTO> bucket = redissonClient.getBucket("task:progress:" + taskId);
        RBucket<String> stopSignal = redissonClient.getBucket("task:stop:" + taskId);
        
        try {
            // 从 URL 中提取项目名称作为知识库标签 (例如 "xfg-dev-tech-trigger")
            String repoProjectName = extractProjectName(repoUrl);
            log.info("异步任务 {}: 开始分析 Git 仓库 {}", taskId, repoUrl);

            // ==================== 阶段 1: 克隆代码 ====================
            updateProgress(bucket, 5, "正在连接远程仓库...", "PROCESSING");

            // 确保目录为空
            FileUtils.deleteDirectory(new File(localPath));

            updateProgress(bucket, 10, "正在克隆代码 (这可能需要几分钟)...", "PROCESSING");
            
            // 【检查点】在耗时操作前检查是否需要取消
            if (stopSignal.isExists()) {
                throw new InterruptedException("用户取消任务");
            }
            
            // 执行 JGit 克隆
            Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(localPath))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                    .call();

            updateProgress(bucket, 30, "克隆完成，开始扫描文件...", "PROCESSING");

            // ==================== 阶段 2: 统计文件 ====================
            // 第一次遍历：只为了统计有多少个有效文件，方便后续计算进度条的百分比
            // 使用 AtomicInteger 保证线程安全（虽然这里是单线程执行，但作为计数器很方便）
            AtomicInteger totalFiles = new AtomicInteger(0);
            Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                     // 快速扫描时也检查取消
                    if (stopSignal.isExists()) {
                        return FileVisitResult.TERMINATE;
                    }
                    // 只统计符合过滤规则的文件
                    if (isValidFile(file)) {
                        totalFiles.incrementAndGet();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // 【检查点】
            if (stopSignal.isExists()) {
                throw new InterruptedException("用户取消任务");
            }

            log.info("任务 {}: 扫描到 {} 个文件", taskId, totalFiles.get());
            updateProgress(bucket, 35, "扫描完成，共 " + totalFiles.get() + " 个文件，开始解析...", "PROCESSING");

            // ==================== 阶段 3: 解析与向量化 ====================
            // 第二次遍历：真正的处理逻辑
            AtomicInteger current = new AtomicInteger(0);
            int total = totalFiles.get() > 0 ? totalFiles.get() : 1; // 避免除以 0

            Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 【核心检查点】每处理一个文件前，都看一眼 Redis 有没有停止信号
                    // 如果有，立即终止遍历（FileVisitResult.TERMINATE）
                    if (stopSignal.isExists()) {
                        return FileVisitResult.TERMINATE;
                    }

                    // 跳过因黑名单或格式不符的文件
                    if (!isValidFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // 计算并更新进度
                    int c = current.incrementAndGet();
                    // 进度条逻辑：35% 是起始点，剩下 65% 分配给文件处理
                    int p = 35 + (int)((c * 60.0) / total);
                    
                    // 只有当进度变化明显时才更新 Redis，避免过于频繁的网络 I/O
                    if (c % 5 == 0 || p % 10 == 0) {
                        updateProgress(bucket, p, "正在解析: " + file.getFileName(), "PROCESSING");
                    }

                    try {
                        // 1. Tika 读取文件内容
                        TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                        List<Document> documents = reader.get();
                        
                        // 2. 文本分块
                        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                        // 3. 注入元数据 (知识库标签)
                        documents.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));

                        // 4. 向量化并入库
                        pgVectorStore.accept(documentSplitterList);
                        
                    } catch (Exception e) {
                        // 单个文件失败不影响整体任务，记录日志即可
                        log.error("处理文件失败: " + file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // 最后再次检查是否是因为取消而退出的
            if (stopSignal.isExists()) {
                throw new InterruptedException("用户取消任务");
            }

            // ==================== 阶段 4: 清理与完成 ====================
            // 删除本地临时代码目录
            FileUtils.deleteDirectory(new File(localPath));
            
            // 将项目名添加到 Redis 的知识库列表
            RList<String> elements = redissonClient.getList("ragTag");
            if (!elements.contains(repoProjectName)) {
                elements.add(repoProjectName);
            }
            git.close();

            // 标记任务完成
            updateProgress(bucket, 100, "分析完成！", "COMPLETED");
            log.info("任务 {}: 分析完成", taskId);

        } catch (InterruptedException e) {
            // 处理逻辑：如果捕获到取消异常，更新状态为 CANCELLED
            log.warn("任务 {} 已被取消", taskId);
            updateProgress(bucket, 0, "任务已取消", "CANCELLED");
            try { FileUtils.deleteDirectory(new File(localPath)); } catch (IOException ignored) {}
        } catch (Exception e) {
            // 处理逻辑：意外错误，更新状态为 FAILED
            log.error("任务 " + taskId + " 失败", e);
            updateProgress(bucket, 0, "任务失败: " + e.getMessage(), "FAILED");
            try { FileUtils.deleteDirectory(new File(localPath)); } catch (IOException ignored) {}
        } finally {
            // 清除停止信号，释放 Redis 空间
            stopSignal.delete();
        }
    }

    /**
     * 【文件有效性校验】
     * 检查给定的文件路径是否应该被处理。
     * 过滤规则包括：
     * 1. 忽略特定目录 (如 .git, target, node_modules 等)
     * 2. 忽略空文件或过大文件 (> 1MB)
     * 3. 只包含特定后缀名的开发文件 (.java, .xml, .md 等)
     * 
     * @param file 文件路径
     * @return true 如果文件有效且需要被处理，否则 false
     */
    private boolean isValidFile(Path file) {
        String pathStr = file.toString();
        
        // 1. 目录黑名单 (包含这些路径的统统跳过)
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

        // 2. 检查文件大小 (跳过空文件或过大文件 > 1MB)
        try { 
            long size = Files.size(file);
            if (size == 0 || size > 1024 * 1024) {
                return false; 
            }
        } catch (IOException e) { 
            return false; 
        }

        // 3. 后缀白名单 (只处理这些核心开发文件)
        // 如果你需要更宽泛的规则，可以改回黑名单模式，但推荐白名单以保证速度
        return pathStr.endsWith(".java") || 
               // MyBatis Mapper
               pathStr.endsWith(".xml") ||  
               // Config
               pathStr.endsWith(".yml") ||  
               pathStr.endsWith(".yaml") || 
               pathStr.endsWith(".properties") ||
               // DB Script
               pathStr.endsWith(".sql") ||  
               // Documentation
               pathStr.endsWith(".md") ||   
               pathStr.endsWith(".txt");
    }

    /**
     * 【更新任务进度】
     * 这是一个辅助方法，用于更新 Redis 中的 TaskProgressDTO 对象。
     * 
     * @param bucket Redisson 的 Bucket 对象，指向 Redis key
     * @param percentage 当前进度百分比 (0-100)
     * @param msg 当前状态描述文字
     * @param state 任务状态 (PROCESSING, COMPLETED, FAILED, CANCELLED)
     */
    private void updateProgress(RBucket<TaskProgressDTO> bucket, int percentage, String msg, String state) {
        // 先取出旧对象，更新属性再塞回去（如果是原子操作会更好，但这里简化处理）
        // 或者直接构建新对象覆盖
        TaskProgressDTO p = bucket.get();
        if (p == null) {
            return;
        }
        p.setPercentage(percentage);
        p.setStatusDescription(msg);
        p.setState(state);
        bucket.set(p, 1, TimeUnit.HOURS);
    }

    /**
     * 【提取项目名称】
     * 从 Git 仓库 URL 中解析出项目名称，作为知识库的 Tag。
     * 例如: https://github.com/user/my-project.git -> my-project
     * 
     * @param repoUrl 仓库 URL
     * @return 项目名称
     */
    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }
}