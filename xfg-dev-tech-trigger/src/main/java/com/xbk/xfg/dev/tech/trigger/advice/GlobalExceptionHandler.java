package com.xbk.xfg.dev.tech.trigger.advice;

import com.xbk.xfg.dev.tech.api.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一处理控制器层抛出的异常，转换为标准响应格式
 *
 * @author xiexu
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param e 非法状态异常
     * @return 业务错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public Response<String> handleIllegalStateException(IllegalStateException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Response.<String>builder()
                .code("4000")
                .info(e.getMessage())
                .build();
    }

    /**
     * 处理系统异常
     *
     * @param e 未知异常
     * @return 系统错误响应
     */
    @ExceptionHandler(Exception.class)
    public Response<String> handleException(Exception e) {
        log.error("系统异常", e);
        return Response.<String>builder()
                .code("5000")
                .info("系统繁忙，请稍后再试")
                .build();
    }
}
