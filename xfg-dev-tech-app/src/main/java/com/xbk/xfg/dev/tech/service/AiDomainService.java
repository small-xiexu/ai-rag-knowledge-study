package com.xbk.xfg.dev.tech.service;

import com.xbk.xfg.dev.tech.factory.DynamicChatClientFactory;
import com.xbk.xfg.dev.tech.factory.DynamicChatClientFactory.ChatClientWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
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
        ChatClientWrapper client = dynamicChatClientFactory.getActiveChatClient();
        return client.stream(new Prompt(message, createOptions(model)));
    }

    /**
     * RAG 流式对话
     */
    public Flux<ChatResponse> generateStreamRag(String model, String ragTag, String message) {
        String SYSTEM_PROMPT = """
                请根据【参考文档】部分的信息来回答用户的问题。
                回答时要表现得像你本来就知道这些信息一样，不要提及"根据文档"之类的话。
                如果文档中没有相关信息，请直接说"我不太清楚这个问题"。

                【参考文档】
                {documents}
                """;

        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)
                .withFilterExpression("knowledge == '" + ragTag + "'");

        List<Document> documents = pgVectorStore.similaritySearch(request);

        String documentCollectors = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n"));

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT)
                .createMessage(Map.of("documents", documentCollectors));

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        ChatClientWrapper client = dynamicChatClientFactory.getActiveChatClient();
        return client.stream(new Prompt(messages, createOptions(model)));
    }

    /**
     * 根据模型名称创建对应的配置选项
     */
    private org.springframework.ai.chat.prompt.ChatOptions createOptions(String model) {
        String providerType = dynamicChatClientFactory.getActiveProviderType();
        if ("OLLAMA".equalsIgnoreCase(providerType)) {
            return org.springframework.ai.ollama.api.OllamaOptions.create().withModel(model);
        }
        return OpenAiChatOptions.builder().withModel(model).build();
    }
}
