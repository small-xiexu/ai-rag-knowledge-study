package com.xbk.xfg.dev.tech.api;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 服务接口
 * 提供基于大语言模型的对话服务，支持同步、流式以及 RAG 增强对话
 *
 * @author xiexu
 */
public interface IAiService {

    /**
     * 同步生成 AI 响应
     *
     * @param model   模型名称（如：gpt-4o, gpt-3.5-turbo, qwen-plus）
     * @param message 用户消息内容
     * @return AI 响应结果
     */
    ChatResponse generate(String model, String message);

    /**
     * 流式生成 AI 响应
     * 采用服务器推送事件（SSE）方式，逐步返回生成的内容
     *
     * @param model   模型名称（如：gpt-4o, gpt-3.5-turbo, qwen-plus）
     * @param message 用户消息内容
     * @return 流式响应 Flux
     */
    Flux<ChatResponse> generateStream(String model, String message);

    /**
     * 基于 RAG（检索增强生成）的流式对话
     * 从向量数据库检索相关知识，结合上下文生成更精准的回答
     * 支持同时选择多个知识库（OR 策略合并检索结果）
     *
     * @param model   模型名称（如：gpt-4o, gpt-3.5-turbo, qwen-plus）
     * @param ragTags 知识库标签列表（用于过滤特定知识库），为空则不使用知识库
     * @param message 用户消息内容
     * @return 流式响应 Flux
     */
    Flux<ChatResponse> generateStreamRag(String model, List<String> ragTags, String message);
}
