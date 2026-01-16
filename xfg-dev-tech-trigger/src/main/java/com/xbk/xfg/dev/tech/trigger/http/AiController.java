package com.xbk.xfg.dev.tech.trigger.http;

import com.xbk.xfg.dev.tech.api.IAiService;
import com.xbk.xfg.dev.tech.api.response.Response;
import com.xbk.xfg.dev.tech.domain.service.AiDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 对话控制器
 * DDD 架构 - HTTP 适配器层，实现应用服务接口
 *
 * @author xiexu
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/ai/")
public class AiController implements IAiService {

    @Resource
    private AiDomainService aiDomainService;

    /**
     * 同步生成接口
     * GET /api/v1/ai/generate?model=gpt-4o&message=你好
     */
    @Override
    @GetMapping("generate")
    public ChatResponse generate(
            @RequestParam(value = "model", required = false) String model,
            @RequestParam("message") String message) {
        return aiDomainService.generate(model, message);
    }

    /**
     * 流式生成接口
     * GET /api/v1/ai/generate_stream?model=gpt-4o&message=你好
     */
    @Override
    @GetMapping(value = "generate_stream", produces = "text/event-stream")
    public Flux<ServerSentEvent<Object>> generateStream(
            @RequestParam(value = "model", required = false) String model,
            @RequestParam("message") String message) {
        String requestId = java.util.UUID.randomUUID().toString();
        log.info("收到流式对话请求 [{}] - 模型: {}, 消息长度: {}", requestId, model, message.length());
        return aiDomainService.generateStream(model, message)
                .map(response -> ServerSentEvent.builder().data(response).build())
                .onErrorResume(e -> {
                    log.error("流式对话异常 [{}]", requestId, e);
                    return Flux.just(buildErrorEvent());
                })
                .doOnSubscribe(s -> log.info("流式对话开始订阅 [{}]", requestId))
                .doOnCancel(() -> log.info("流式对话取消 [{}]", requestId))
                .doOnComplete(() -> log.info("流式对话完成 [{}]", requestId));
    }

    /**
     * RAG 流式对话接口（支持多知识库）
     * 单个：GET /api/v1/ai/generate_stream_rag?ragTags=doc1&message=你好
     * 多个：GET /api/v1/ai/generate_stream_rag?ragTags=doc1&ragTags=doc2&message=你好
     */
    @Override
    @GetMapping(value = "generate_stream_rag", produces = "text/event-stream")
    public Flux<ServerSentEvent<Object>> generateStreamRag(
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "ragTags", required = false) List<String> ragTags,
            @RequestParam("message") String message) {
        log.info("收到RAG流式对话请求 - 模型: {}, 知识库: {}, 消息长度: {}", model, ragTags, message.length());
        return aiDomainService.generateStreamRag(model, ragTags, message)
                .map(response -> ServerSentEvent.builder().data(response).build())
                .onErrorResume(e -> {
                    log.error("RAG流式对话异常", e);
                    return Flux.just(buildErrorEvent());
                })
                .doOnSubscribe(s -> log.info("RAG流式对话开始订阅"))
                .doOnCancel(() -> log.info("RAG流式对话取消"))
                .doOnComplete(() -> log.info("RAG流式对话完成"));
    }

    private ServerSentEvent<Object> buildErrorEvent() {
        Response<String> error = Response.<String>builder()
                .code("5000")
                .info("系统繁忙，请稍后再试")
                .build();
        return ServerSentEvent.builder()
                .event("error")
                .data(error)
                .build();
    }
}
