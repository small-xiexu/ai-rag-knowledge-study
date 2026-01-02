package com.xbk.xfg.dev.tech.trigger.http;

import com.xbk.xfg.dev.tech.api.IAiService;
import com.xbk.xfg.dev.tech.service.AiDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
    public ChatResponse generate(@RequestParam("model") String model, @RequestParam("message") String message) {
        return aiDomainService.generate(model, message);
    }

    /**
     * 流式生成接口
     * GET /api/v1/ai/generate_stream?model=gpt-4o&message=你好
     */
    @Override
    @GetMapping(value = "generate_stream", produces = "text/event-stream")
    public Flux<ChatResponse> generateStream(@RequestParam("model") String model, @RequestParam("message") String message) {
        return aiDomainService.generateStream(model, message);
    }

    /**
     * RAG 流式对话接口
     * GET /api/v1/ai/generate_stream_rag?model=gpt-4o&ragTag=test&message=你好
     */
    @Override
    @GetMapping(value = "generate_stream_rag", produces = "text/event-stream")
    public Flux<ChatResponse> generateStreamRag(
            @RequestParam("model") String model,
            @RequestParam("ragTag") String ragTag,
            @RequestParam("message") String message) {
        return aiDomainService.generateStreamRag(model, ragTag, message);
    }
}
