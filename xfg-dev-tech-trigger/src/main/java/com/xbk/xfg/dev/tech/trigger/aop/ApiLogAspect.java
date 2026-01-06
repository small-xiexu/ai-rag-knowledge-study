package com.xbk.xfg.dev.tech.trigger.aop;

import com.alibaba.fastjson.JSON;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Flux;

import jakarta.servlet.http.HttpServletRequest;

/**
 * API 日志切面
 * 用于记录接口的请求和响应信息
 */
@Aspect
@Component
public class ApiLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiLogAspect.class);

    /**
     * 切点：拦截所有 Controller 的方法
     */
    @Pointcut("execution(* com.xbk.xfg.dev.tech.trigger.http..*(..))")
    public void apiPointcut() {
    }

    /**
     * 环绕通知：记录请求和响应（单行日志格式）
     */
    @Around("apiPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        // 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        
        // 获取请求参数（简化处理）
        String params = formatParams(joinPoint.getArgs());
        
        // HTTP 方法和路径
        String httpMethod = request != null ? request.getMethod() : "N/A";
        String path = request != null ? request.getRequestURI() : "N/A";
        String ip = getIpAddr(request);
        
        // 执行目标方法
        Object result = null;
        String responseStr = "[unknown]";
        
        try {
            result = joinPoint.proceed();
            responseStr = formatResponse(result);
        } catch (Exception e) {
            responseStr = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            
            // 单行日志格式: [HTTP方法 路径] IP | 耗时 | 方法名 | 请求 | 响应
            log.info("[{} {}] {} | {}ms | {} | request={} | response={}",
                    httpMethod, path, ip, costTime, methodName,
                    truncate(params, 100),
                    truncate(responseStr, 200));
        }
        
        return result;
    }
    
    /**
     * 格式化参数
     */
    private String formatParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        try {
            return JSON.toJSONString(args);
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }
    
    /**
     * 格式化响应
     */
    private String formatResponse(Object result) {
        if (result instanceof Flux) {
            return "[Flux Stream]";
        }
        try {
            return JSON.toJSONString(result);
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    /**
     * 获取客户端 IP 地址
     */
    private String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时，第一个为真实 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
