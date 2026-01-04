package com.xbk.xfg.dev.tech.config;

// ==================== Spring AI 相关依赖导入 ====================
// Ollama API：底层 HTTP 客户端，负责与 Ollama 服务通信
import org.springframework.ai.ollama.api.OllamaApi;
// Ollama 嵌入模型：用于将文本转换成向量（本地模型）
import org.springframework.ai.ollama.OllamaEmbeddingModel;
// Ollama 配置选项：用于设置模型参数（如使用哪个模型）
import org.springframework.ai.ollama.api.OllamaOptions;
// OpenAI 嵌入模型：用于将文本转换成向量（OpenAI 兼容接口）
import org.springframework.ai.openai.OpenAiEmbeddingModel;
// OpenAI API：底层 HTTP 客户端，负责与 OpenAI 兼容服务通信
import org.springframework.ai.openai.api.OpenAiApi;
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

/**
 * Spring AI 配置类 - RAG (检索增强生成) 应用的核心配置
 * 
 * 本配置类负责初始化以下组件：
 * 1. API 客户端：连接 Ollama 和 OpenAI 服务
 * 2. 文本分割器：将文档切分成合适的块
 * 3. 向量存储：存储文本的向量表示，支持相似性搜索
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

    /**
     * 创建 OpenAI 兼容 API 客户端
     */
    @Bean
    public OpenAiApi openAiApi(@Value("${spring.ai.openai.base-url}") String baseUrl, 
                                @Value("${spring.ai.openai.api-key}") String apikey) {
        return new OpenAiApi(baseUrl, apikey);
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
    public PgVectorStore pgVectorStore(@Value("${spring.ai.rag.embed}") String model, 
                                        OllamaApi ollamaApi, 
                                        OpenAiApi openAiApi, 
                                        JdbcTemplate jdbcTemplate) {
        // 根据配置选择嵌入模型
        if ("nomic-embed-text".equalsIgnoreCase(model)) {
            // 使用 Ollama 本地嵌入模型
            OllamaOptions options = OllamaOptions.builder()
                    .model("nomic-embed-text")
                    .build();
            OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(options)
                    .build();
            // 创建 PgVectorStore
            return PgVectorStore.builder(jdbcTemplate, embeddingModel).build();
        } else {
            // 使用 OpenAI 嵌入模型
            OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi);
            // 创建 PgVectorStore
            return PgVectorStore.builder(jdbcTemplate, embeddingModel).build();
        }
    }

}
