package com.xbk.xfg.dev.tech.test;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {


    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private PgVectorStore pgVectorStore;

    /**
     * 【数据加载/知识库上传测试】
     * 这个方法演示了 RAG 的第一步：把文档存入向量数据库
     * 执行顺序：读取文件 -> 分割文本 -> 添加元数据 -> 存入 PgVector
     */
    @Test
    public void upload() {
        // ==================== 第一步：读取文档 ====================
        // 使用 Apache Tika 读取文件（Tika 支持 PDF、Word、TXT 等多种格式）
        // 这里读取的是 ./data/file.text 文件
        TikaDocumentReader reader = new TikaDocumentReader("./data/file.text");

        // 调用 get() 方法，将文件内容解析成 Document 对象列表
        // 每个 Document 包含：content（文本内容）和 metadata（元数据）
        List<Document> documents = reader.get();
        
        // ==================== 第二步：分割文档 ====================
        // 使用 TokenTextSplitter 将长文档切割成小块（Chunk）
        // 为什么要切？因为：1.大模型有 token 限制  2.小块检索更精准
        // tokenTextSplitter 会按 token 数量（而非字符数）来切割
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        // ==================== 第三步：添加元数据 ====================
        // 给每个文档块打上"标签"，比如这里标记为"知识库名称"
        // 元数据的作用：后续检索时可以按标签过滤，比如只查某个知识库的内容
        documents.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));

        // ==================== 第四步：存入向量数据库 ====================
        // 【核心步骤！】调用 pgVectorStore.accept() 方法
        // 这一步会自动完成：
        //   1. 调用 Ollama 的 nomic-embed-text 模型，把每个文本块转成向量
        //   2. 把向量 + 原文 + 元数据 一起存入 PostgreSQL（pgvector 表）
        pgVectorStore.accept(documentSplitterList);

        log.info("上传完成");
    }

}
