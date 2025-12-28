package com.xbk.xfg.dev.tech.api;

import com.xbk.xfg.dev.tech.api.dto.OllamaRequest;
import org.springframework.ai.chat.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * AI 服务接口
 * 
 * @author xiexu
 */
public interface IAiService {

    ChatResponse generate(OllamaRequest request);

    Flux<ChatResponse> generateStream(OllamaRequest request);

}
