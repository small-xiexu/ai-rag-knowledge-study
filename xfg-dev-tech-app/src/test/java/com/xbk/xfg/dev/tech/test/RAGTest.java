package com.xbk.xfg.dev.tech.test;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
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

    /**
     * 【RAG 对话测试】
     * 这个方法演示了 RAG 的第二步：检索增强生成
     * 执行顺序：用户提问 -> 向量检索 -> 拼接上下文 -> 调用大模型生成回答
     */
    @Test
    public void chat() {
        // ==================== 第一步：接收用户问题 ====================
        String message = "王大瓜，哪年出生";

        // ==================== 第二步：定义系统提示词模板 ====================
        // 这是给 AI 的"角色设定"和"任务说明"
        // {documents} 是一个占位符，后面会被真实检索到的内容替换
        String SYSTEM_PROMPT = """
                请根据【文档资料】部分的内容来准确回答用户的问题。
                回答时请表现得像你本来就知道这些信息一样，不要提及"根据文档"等字眼。
                如果你不确定答案，请直接说"我不知道"，不要编造信息。
                请务必使用中文回复！
                
                【文档资料】:
                    {documents}
                """;

        // ==================== 第三步：构建向量检索请求 ====================
        // SearchRequest 用于配置检索参数：
        //   - query(message)：把用户的问题作为查询文本（会被转成向量去匹配）
        //   - withTopK(5)：返回最相似的前 5 条结果
        //   - withFilterExpression(...)：元数据过滤，只查 knowledge='知识库名称' 的文档
        SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == '知识库名称'");

        // ==================== 第四步：执行向量相似度检索 ====================
        // 【核心步骤！】调用 pgVectorStore.similaritySearch()
        // 这一步会自动完成：
        //   1. 调用 Ollama 把 message 转成向量
        //   2. 在 PgVector 中找出与这个向量最接近的 5 条记录
        //   3. 返回这些记录的原始文本
        List<Document> documents = pgVectorStore.similaritySearch(request);
        
        // 把检索到的多个文档内容拼接成一个大字符串
        String documentsCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());

        // ==================== 第五步：构建最终发送给 AI 的消息 ====================
        // 用检索到的文档内容替换模板中的 {documents} 占位符
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        // 组装消息列表（对话历史）
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));  // 用户的原始问题
        messages.add(ragMessage);                 // 包含检索资料的系统提示

        // ==================== 第六步：调用大模型生成回答 ====================
        // 把消息列表 + 模型配置一起发送给 Ollama
        // AI 会结合检索到的资料来回答问题（这就是"增强"的含义）
        ChatResponse chatResponse = ollamaChatClient.call(new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:1.5b")));

        // 输出最终结果
        log.info("测试结果:{}", JSON.toJSONString(chatResponse));

    }

}
