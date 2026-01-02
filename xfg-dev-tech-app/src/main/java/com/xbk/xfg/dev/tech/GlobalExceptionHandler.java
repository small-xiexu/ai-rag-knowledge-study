package com.xbk.xfg.dev.tech;

import com.xbk.xfg.dev.tech.api.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 
 * @author xiexu
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public Response<String> handleIllegalStateException(IllegalStateException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Response.<String>builder()
                .code("4000") // 业务错误码
                .info(e.getMessage())
                .build();
    }

    @ExceptionHandler(Exception.class)
    public Response<String> handleException(Exception e) {
        log.error("系统异常", e);
        return Response.<String>builder()
                .code("5000")
                .info("系统繁忙，请稍后再试")
                .build();
    }
}
