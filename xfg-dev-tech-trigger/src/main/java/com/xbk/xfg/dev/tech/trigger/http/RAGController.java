package com.xbk.xfg.dev.tech.trigger.http;

import com.xbk.xfg.dev.tech.api.IRAGService;
import com.xbk.xfg.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author xiexu
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatClient ollamaChatClient;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private SimpleVectorStore simpleVectorStore;

    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 【查询知识库标签列表】
     * 从 Redis 中获取所有已创建的知识库标签名称
     * 用途：前端下拉框展示可选的知识库列表
     *
     * @return 知识库标签列表
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
     * 【RAG 对话接口 - 流式输出】
     * 检索知识库相关文档，拼接上下文后调用大模型生成回答
     * 这是 RAG 系统的核心功能：检索增强生成
     *
     * @param model   模型名称（如 deepseek-r1:1.5b）
     * @param ragTag  知识库标签名称（用于过滤检索范围）
     * @param message 用户问题
     * @return 流式 AI 回答
     */
    @GetMapping(value = "generate_stream_rag", produces = "text/event-stream")
    public Flux<ChatResponse> ragChatStream(
            @RequestParam String model,
            @RequestParam String ragTag,
            @RequestParam String message) {
        
        log.info("RAG对话请求 - 模型:{}, 知识库:{}, 问题:{}", model, ragTag, message);

        // 第一步：定义系统提示词模板
        String systemPrompt = """
                请根据【文档资料】部分的内容来准确回答用户的问题。
                回答时请表现得像你本来就知道这些信息一样，不要提及"根据文档"等字眼。
                如果你不确定答案，请直接说"我不知道"，不要编造信息。
                请务必使用中文回复！
                
                【文档资料】:
                    {documents}
                """;

        // 第二步：构建向量检索请求
        // 把用户问题转成向量，在知识库中查找最相似的文档块
        // 返回最相似的 5 条结果，按知识库标签过滤
        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)
                .withFilterExpression("knowledge == '" + ragTag + "'");

        // 第三步：执行向量相似度检索
        List<Document> documents = pgVectorStore.similaritySearch(request);
        
        // 把检索到的文档内容拼接成一个字符串
        String documentsContent = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));
        
        log.info("检索到 {} 条相关文档", documents.size());

        // 第四步：构建消息列表
        // 用检索到的文档替换模板中的 {documents} 占位符
        Message ragMessage = new SystemPromptTemplate(systemPrompt)
                .createMessage(Map.of("documents", documentsContent));

        // 组装消息列表
        ArrayList<Message> messages = new ArrayList<>();
        // 用户问题
        messages.add(new UserMessage(message));
        // 包含检索资料的系统提示
        messages.add(ragMessage);

        // 第五步：流式调用大模型生成回答
        return ollamaChatClient.stream(new Prompt(messages, OllamaOptions.create().withModel(model)));
    }

}