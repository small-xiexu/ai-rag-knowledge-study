package com.xbk.xfg.dev.tech.config;

// ==================== Spring AI 相关依赖导入 ====================
// Ollama API：底层 HTTP 客户端，负责与 Ollama 服务通信
import org.springframework.ai.ollama.api.OllamaApi;
// 文本分割器：将长文档按 Token 数量分割成多个小块
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
// PostgreSQL 向量存储：使用 pgvector 扩展持久化存储向量数据
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

// Spring 配置值注入注解
import org.springframework.beans.factory.annotation.Value;
// Spring Bean 定义注解
import org.springframework.context.annotation.Bean;
// Spring 配置类注解
import org.springframework.context.annotation.Configuration;
// JDBC 模板：用于执行 SQL 操作，PgVectorStore 依赖它
import org.springframework.jdbc.core.JdbcTemplate;

import com.xbk.xfg.dev.tech.domain.factory.DynamicEmbeddingFactory;
import com.xbk.xfg.dev.tech.domain.factory.LazyEmbeddingModel;

/**
 * Spring AI 配置类 - RAG (检索增强生成) 应用的核心配置
 * 
 * 本配置类负责初始化以下组件：
 * 1. API 客户端：连接 Ollama 服务
 * 2. 文本分割器：将文档切分成合适的块
 * 3. 向量存储：存储文本的向量表示，支持相似性搜索（动态 Embedding）
 *
 * @author xiexu
 */
@Configuration
public class AiConfig {

    // ==================== 1. API 客户端配置 ====================

    /**
     * 创建 Ollama API 客户端
     */
    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        return new OllamaApi(baseUrl);
    }

    // ==================== 2. 文本分割器配置 ====================

    /**
     * 创建 Token 文本分割器
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    // ==================== 3. 向量存储配置 ====================

    /**
     * 创建 PostgreSQL 向量存储（基于 pgvector 扩展）
     */
    @Bean
    public PgVectorStore pgVectorStore(JdbcTemplate jdbcTemplate,
                                        DynamicEmbeddingFactory embeddingFactory) {
        // 使用 LazyEmbeddingModel 支持运行时切换 Embedding 模型
        return PgVectorStore.builder(
                jdbcTemplate,
                new LazyEmbeddingModel(embeddingFactory)
        ).build();
    }

}
