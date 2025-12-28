package com.xbk.xfg.dev.tech.trigger.http;

import com.xbk.xfg.dev.tech.api.IAiService;
import com.xbk.xfg.dev.tech.api.dto.OllamaRequest;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * @author xiexu
 */
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama/")
public class OllamaController implements IAiService {

    @Resource
    private OllamaChatClient chatClient;

    /**
     * 同步生成接口
     * POST http://localhost:8090/api/v1/ollama/generate
     * Content-Type: application/json
     * {"model":"deepseek-r1:1.5b","message":"1+1"}
     */
    @PostMapping("generate")
    @Override
    public ChatResponse generate(@RequestBody OllamaRequest request) {
        return chatClient.call(new Prompt(request.getMessage(), OllamaOptions.create().withModel(request.getModel())));
    }

    /**
     * 流式生成接口
     * POST http://localhost:8090/api/v1/ollama/generate_stream
     * Content-Type: application/json
     * {"model":"deepseek-r1:1.5b","message":"hi"}
     */
    @PostMapping("generate_stream")
    @Override
    public Flux<ChatResponse> generateStream(@RequestBody OllamaRequest request) {
        return chatClient.stream(new Prompt(request.getMessage(), OllamaOptions.create().withModel(request.getModel())));
    }

}
