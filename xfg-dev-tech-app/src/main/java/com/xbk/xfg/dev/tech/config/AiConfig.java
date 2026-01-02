package com.xbk.xfg.dev.tech.config;

// ==================== Spring AI 相关依赖导入 ====================
// Ollama 聊天客户端：用于与 Ollama 本地大模型进行对话
import org.springframework.ai.ollama.OllamaChatClient;
// Ollama 嵌入客户端：用于将文本转换成向量（本地模型）
import org.springframework.ai.ollama.OllamaEmbeddingClient;
// Ollama API：底层 HTTP 客户端，负责与 Ollama 服务通信
import org.springframework.ai.ollama.api.OllamaApi;
// Ollama 配置选项：用于设置模型参数（如使用哪个模型）
import org.springframework.ai.ollama.api.OllamaOptions;
// OpenAI 嵌入客户端：用于将文本转换成向量（OpenAI 兼容接口）
import org.springframework.ai.openai.OpenAiEmbeddingClient;
// OpenAI 聊天客户端：用于与 OpenAI 兼容服务进行对话
import org.springframework.ai.openai.OpenAiChatClient;
// OpenAI API：底层 HTTP 客户端，负责与 OpenAI 兼容服务通信
import org.springframework.ai.openai.api.OpenAiApi;
// 文本分割器：将长文档按 Token 数量分割成多个小块
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
// PostgreSQL 向量存储：使用 pgvector 扩展持久化存储向量数据
import org.springframework.ai.vectorstore.PgVectorStore;
// 简单向量存储：基于内存的向量存储，适合开发测试

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
 * 2. 聊天客户端：用于 AI 对话
 * 3. 文本分割器：将文档切分成合适的块
 * 4. 向量存储：存储文本的向量表示，支持相似性搜索
 *
 * @author xiexu
 */
@Configuration  // 标记为 Spring 配置类，容器启动时会自动扫描并注册其中的 Bean
public class AiConfig {

    // ==================== 1. API 客户端配置 ====================

    /**
     * 创建 Ollama API 客户端
     * 
     * @param baseUrl 从配置文件读取的 Ollama 服务地址（如 http://localhost:11434）
     * @return OllamaApi 实例，用于与本地 Ollama 服务通信
     */
    @Bean  // 将方法返回值注册为 Spring Bean，其他组件可以自动注入使用
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        // 使用配置的 baseUrl 创建 Ollama API 客户端
        return new OllamaApi(baseUrl);
    }

    /**
     * 创建 OpenAI 兼容 API 客户端
     * 
     * @param baseUrl 从配置文件读取的 API 地址（可以是 OpenAI 官方或兼容服务）
     * @param apikey 从配置文件读取的 API 密钥
     * @return OpenAiApi 实例，用于与 OpenAI 兼容服务通信
     */
    @Bean
    public OpenAiApi openAiApi(@Value("${spring.ai.openai.base-url}") String baseUrl, 
                                @Value("${spring.ai.openai.api-key}") String apikey) {
        // 使用 baseUrl 和 apiKey 创建 OpenAI API 客户端
        return new OpenAiApi(baseUrl, apikey);
    }

    // ==================== 3. 文本分割器配置 ====================

    /**
     * 创建 Token 文本分割器
     * 
     * 作用：将长文档按 Token 数量分割成多个小块（chunks）
     * 
     * 为什么需要分割？
     * 1. 嵌入模型对输入长度有限制（如 8192 tokens）
     * 2. 更小的块可以提高检索精度
     * 3. 减少每次 API 调用的成本
     * 
     * @return TokenTextSplitter 实例，使用默认配置
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        // 创建默认的 Token 分割器
        // 默认配置：每块约 800 tokens，块之间有 400 tokens 重叠
        return new TokenTextSplitter();
    }

    // ==================== 4. 向量存储配置 ====================

    /**
     * 创建 PostgreSQL 向量存储（基于 pgvector 扩展）
     * 
     * 作用：数据持久化到 PostgreSQL 数据库
     * 
     * 优点：
     * 1. 数据持久化，重启不丢失
     * 2. 支持大规模数据存储
     * 3. 可以利用 PostgreSQL 的索引加速查询
     * 
     * 前提条件：
     * 1. PostgreSQL 数据库需要安装 pgvector 扩展
     * 2. 需要创建存储向量的表（通常 Spring AI 会自动创建）
     * 
     * @param model 使用的嵌入模型名称
     * @param ollamaApi Ollama API 客户端
     * @param openAiApi OpenAI API 客户端
     * @param jdbcTemplate JDBC 模板，用于执行 SQL 操作
     * @return PgVectorStore 实例
     */
    @Bean
    public PgVectorStore pgVectorStore(@Value("${spring.ai.rag.embed}") String model, 
                                        OllamaApi ollamaApi, 
                                        OpenAiApi openAiApi, 
                                        JdbcTemplate jdbcTemplate) {
        // 根据配置选择嵌入模型
        if ("nomic-embed-text".equalsIgnoreCase(model)) {
            // 使用 Ollama 本地嵌入模型
            OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
            embeddingClient.withDefaultOptions(OllamaOptions.create().withModel("nomic-embed-text"));
            // 创建 PgVectorStore
            return new PgVectorStore(jdbcTemplate, embeddingClient);
        } else {
            // 使用 OpenAI 嵌入模型
            OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(openAiApi);
            // 创建 PgVectorStore
            return new PgVectorStore(jdbcTemplate, embeddingClient);
        }
    }

}
