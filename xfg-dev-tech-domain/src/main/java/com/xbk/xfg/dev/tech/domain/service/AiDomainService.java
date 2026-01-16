package com.xbk.xfg.dev.tech.domain.service;

import com.xbk.xfg.dev.tech.domain.factory.DynamicChatClientFactory;
import com.xbk.xfg.dev.tech.domain.factory.DynamicChatClientFactory.ChatClientWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 领域服务
 * 负责 AI 对话的核心业务逻辑
 *
 * @author xiexu
 */
@Slf4j
@Service
public class AiDomainService {

    private static final double RAG_SIMILARITY_THRESHOLD = 0.7d;

    @Resource
    private DynamicChatClientFactory dynamicChatClientFactory;

    @Resource
    private PgVectorStore pgVectorStore;

    /**
     * 同步生成
     */
    public ChatResponse generate(String model, String message) {
        ChatClientWrapper client = dynamicChatClientFactory.getActiveChatClient();
        return client.call(new Prompt(message, createOptions(model)));
    }

    /**
     * 流式生成
     */
    public Flux<ChatResponse> generateStream(String model, String message) {
        try {
            ChatClientWrapper client = dynamicChatClientFactory.getActiveChatClient();
            log.info("执行流式生成 - 客户端: {}, 模型: {}", client.getClass().getSimpleName(), model);
            return client.stream(new Prompt(message, createOptions(model)))
                    .doOnError(e -> log.error("调用 ChatClient 流式接口失败", e));
        } catch (Exception e) {
            log.error("根据配置获取 ChatClient 失败", e);
            return Flux.error(e);
        }
    }

    /**
     * RAG 流式对话（支持多知识库）
     * 使用 OR 策略合并多个知识库的检索结果
     *
     * @param model   模型名称
     * @param ragTags 知识库标签列表，为空则走普通对话
     * @param message 用户消息
     */
    public Flux<ChatResponse> generateStreamRag(String model, List<String> ragTags, String message) {
        // 如果没有选择知识库，走普通对话流程
        if (CollectionUtils.isEmpty(ragTags)) {
            log.info("【RAG】未选择知识库，走普通对话流程");
            return generateStream(model, message);
        }

        String SYSTEM_PROMPT = """
                请根据【参考文档】部分的信息来回答用户的问题。
                回答时要表现得像你本来就知道这些信息一样，不要提及"根据文档"之类的话。
                如果文档中没有相关信息，请直接说"我不太清楚这个问题"。

                【参考文档】
                {documents}
                """;

        // 构建 OR 过滤表达式：knowledge == 'doc1' || knowledge == 'doc2'
        String filterExpression = ragTags.stream()
                .map(tag -> "knowledge == '" + tag + "'")
                .collect(Collectors.joining(" || "));

        log.info("【RAG】多知识库检索，过滤表达式: {}", filterExpression);

        SearchRequest request = SearchRequest.builder()
                .query(message)
                .topK(5)
                .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                .filterExpression(filterExpression)
                .build();

        List<Document> documents = pgVectorStore.similaritySearch(request);
        if (documents == null) {
            documents = List.of();
        }
        log.info("【RAG】检索到 {} 条相关文档", documents.size());
        // 低相关时不注入文档，直接走普通对话
        if (documents.isEmpty()) {
            log.info("【RAG】相似度低于阈值 {}，退回普通对话", RAG_SIMILARITY_THRESHOLD);
            return generateStream(model, message);
        }

        String documentCollectors = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT)
                .createMessage(Map.of("documents", documentCollectors));

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        try {
            ChatClientWrapper client = dynamicChatClientFactory.getActiveChatClient();
            log.info("执行RAG流式生成 - 客户端: {}, 模型: {}", client.getClass().getSimpleName(), model);
            return client.stream(new Prompt(messages, createOptions(model)))
                    .doOnError(e -> log.error("调用 ChatClient RAG流式接口失败", e));
        } catch (Exception e) {
            log.error("RAG模式下获取 ChatClient 失败", e);
            return Flux.error(e);
        }
    }

    /**
     * 根据模型名称创建对应的配置选项
     * 如果 model 为空，使用当前激活配置的默认模型
     */
    private org.springframework.ai.chat.prompt.ChatOptions createOptions(String model) {
        String providerType = dynamicChatClientFactory.getActiveProviderType();
        String actualModel = model;
        
        // 如果没有指定模型，使用默认模型
        if (actualModel == null || actualModel.isEmpty()) {
            actualModel = dynamicChatClientFactory.getActiveDefaultModel();
            log.info("使用默认模型: {}", actualModel);
        }
        
        if ("OLLAMA".equalsIgnoreCase(providerType)) {
            return org.springframework.ai.ollama.api.OllamaOptions.builder().model(actualModel).build();
        }
        return OpenAiChatOptions.builder().model(actualModel).build();
    }
}
