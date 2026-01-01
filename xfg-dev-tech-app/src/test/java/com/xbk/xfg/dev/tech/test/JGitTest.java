package com.xbk.xfg.dev.tech.test;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.PathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
/**
 * JGit 测试类
 * 演示如何使用 JGit 克隆远程 Git 仓库，并将仓库中的文件向量化存入 PgVector 数据库
 * 这是一个将代码仓库作为知识库的 RAG 应用场景
 */
public class JGitTest {

    // 注入 Ollama 聊天客户端，用于后续的 AI 对话（本测试中未使用）
    @Resource
    private OllamaChatClient ollamaChatClient;
    
    // 注入文本分割器，用于将长文档切割成小块
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    
    // 注入简单向量存储（内存/文件存储，本测试中未使用）
    @Resource
    private SimpleVectorStore simpleVectorStore;
    
    // 注入 PgVector 向量数据库存储，用于存储文档向量
    @Resource
    private PgVectorStore pgVectorStore;

    /**
     * 测试方法：克隆远程 Git 仓库到本地
     * 使用 JGit 库实现 Git 操作
     */
    @Test
    public void test() throws Exception {
        // Git 仓库的远程地址（HTTPS 格式）
        String repoURL = "https://gitcode.com/sj15814963053/seckill-2558-sean";
        // Git 平台的用户名（用于身份验证）
        String username = "@sj15814963053";
        // Git 平台的访问令牌或密码（用于身份验证）
        String password = "iyVu6vUfzRQz_GybFwzFmWCM";

        // 定义本地克隆目录路径（相对路径，相对于项目根目录）
        String localPath = "./cloned-repo";
        // 打印克隆目录的绝对路径，方便调试
        log.info("克隆路径：" + new File(localPath).getAbsolutePath());

        // 如果本地目录已存在，先删除整个目录（避免克隆冲突）
        FileUtils.deleteDirectory(new File(localPath));

        // 使用 JGit 克隆远程仓库
        // cloneRepository() 创建克隆命令构建器
        Git git = Git.cloneRepository()
                // 设置远程仓库 URL
                .setURI(repoURL)
                // 设置本地目标目录
                .setDirectory(new File(localPath))
                // 设置认证凭证（用户名 + 密码/令牌）
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                // 执行克隆操作
                .call();

        // 关闭 Git 对象，释放资源
        git.close();
    }

    /**
     * 测试方法：遍历克隆的仓库文件，将每个文件向量化存入数据库
     * 这是将代码仓库转化为 RAG 知识库的核心逻辑
     */
    @Test
    public void test_file() throws IOException {
        // 使用 Java NIO 的 Files.walkFileTree 递归遍历目录下的所有文件
        // Paths.get("./cloned-repo") 指定要遍历的根目录
        // SimpleFileVisitor 是文件访问器的简化实现
        Files.walkFileTree(Paths.get("./cloned-repo"), new SimpleFileVisitor<>() {
            
            // 当访问到一个文件时，该方法会被调用
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                
                // 过滤条件：跳过不需要处理的文件
                String pathStr = file.toString();
                // 1. 跳过 .git 目录下的文件
                if (pathStr.contains(".git")) {
                    return FileVisitResult.CONTINUE;
                }
                // 2. 跳过空文件（0 字节），避免 ZeroByteFileException
                if (attrs.size() == 0) {
                    log.warn("跳过空文件: {}", pathStr);
                    return FileVisitResult.CONTINUE;
                }
                // 3. 跳过二进制文件（.class, .jar, .png, .jpg 等）
                if (pathStr.endsWith(".class") || pathStr.endsWith(".jar") ||
                    pathStr.endsWith(".png") || pathStr.endsWith(".jpg") ||
                    pathStr.endsWith(".gif") || pathStr.endsWith(".ico") ||
                    pathStr.endsWith(".woff") || pathStr.endsWith(".woff2") ||
                    pathStr.endsWith(".ttf") || pathStr.endsWith(".eot")) {
                    log.info("跳过二进制文件: {}", file.getFileName());
                    return FileVisitResult.CONTINUE;
                }

                try {
                    // 打印当前处理的文件路径
                    log.info("文件路径:{}", file.toString());

                    // 将 Path 转换为 Spring 的 PathResource，作为 Tika 的输入源
                    PathResource resource = new PathResource(file);
                    // 使用 Apache Tika 读取文件内容（支持多种格式：Java、Python、MD、TXT 等）
                    TikaDocumentReader reader = new TikaDocumentReader(resource);

                    // 调用 get() 方法解析文件，返回 Document 列表
                    // 每个 Document 包含：content（文件内容）和 metadata（元数据）
                    List<Document> documents = reader.get();
                    // 使用 TokenTextSplitter 将长文档切割成小块
                    // 切割的目的：1. 大模型有 token 限制  2. 小块检索更精准
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                    // 给原始文档添加知识库标签（元数据）
                    // 这个标签用于后续检索时过滤，只查询指定知识库的文档
                    documents.forEach(doc -> doc.getMetadata().put("knowledge", "seckill-2558-sean"));
                    // 给切割后的文档块也添加相同的知识库标签
                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "seckill-2558-sean"));

                    // 将切割后的文档块存入 PgVector 向量数据库
                    // 这一步会自动完成：
                    //   1. 调用 Embedding 模型（如 nomic-embed-text）将文本转成向量
                    //   2. 将向量 + 原文 + 元数据 存入 PostgreSQL
                    pgVectorStore.accept(documentSplitterList);
                    
                } catch (Exception e) {
                    // 捕获异常，打印警告日志，继续处理下一个文件
                    log.warn("处理文件失败，跳过: {} - {}", file.getFileName(), e.getMessage());
                }

                // 返回 CONTINUE 表示继续遍历下一个文件
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 【并发版本】遍历克隆的仓库文件，并行处理文件向量化
     * 使用线程池并发处理，显著提升处理速度
     * 注意：并发数不宜过高，因为 Ollama Embedding 模型有并发限制
     */
    @Test
    public void test_file_concurrent() throws IOException, InterruptedException {
        // 创建固定大小的线程池，并发数设为 4（可根据机器性能调整）
        // 注意：如果 Ollama 部署在本地，过高的并发可能导致内存不足
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // 用于统计处理进度
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(0);
        
        // 存储所有文件路径
        List<Path> filePaths = new ArrayList<>();
        
        // 第一步：收集所有文件路径（不在遍历时处理，避免阻塞）
        Files.walkFileTree(Paths.get("./cloned-repo"), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String pathStr = file.toString();
                // 1. 跳过 .git 目录
                if (pathStr.contains(".git")) {
                    return FileVisitResult.CONTINUE;
                }
                // 2. 跳过空文件（0 字节）
                if (attrs.size() == 0) {
                    return FileVisitResult.CONTINUE;
                }
                // 3. 跳过二进制文件
                if (pathStr.endsWith(".class") || pathStr.endsWith(".jar") ||
                    pathStr.endsWith(".png") || pathStr.endsWith(".jpg") ||
                    pathStr.endsWith(".gif") || pathStr.endsWith(".ico") ||
                    pathStr.endsWith(".woff") || pathStr.endsWith(".woff2") ||
                    pathStr.endsWith(".ttf") || pathStr.endsWith(".eot")) {
                    return FileVisitResult.CONTINUE;
                }
                filePaths.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        
        totalCount.set(filePaths.size());
        log.info("共发现 {} 个文件，开始并发处理...", totalCount.get());
        
        // 第二步：并发提交所有文件处理任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (Path file : filePaths) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // 读取文件
                    PathResource resource = new PathResource(file);
                    TikaDocumentReader reader = new TikaDocumentReader(resource);
                    List<Document> documents = reader.get();
                    
                    // 切割文档
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
                    
                    // 添加元数据标签
                    documentSplitterList.forEach(doc -> 
                        doc.getMetadata().put("knowledge", "seckill-2558-sean"));
                    
                    // 存入向量数据库（这一步最耗时，因为要调用 Embedding 模型）
                    pgVectorStore.accept(documentSplitterList);
                    
                    // 更新进度
                    int current = processedCount.incrementAndGet();
                    log.info("进度: {}/{} - 已处理: {}", current, totalCount.get(), file.getFileName());
                    
                } catch (Exception e) {
                    log.error("处理文件失败: {} - {}", file, e.getMessage());
                }
            }, executor);
            
            futures.add(future);
        }
        
        // 第三步：等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // 关闭线程池
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        
        log.info("并发处理完成！共处理 {} 个文件", processedCount.get());
    }

}
