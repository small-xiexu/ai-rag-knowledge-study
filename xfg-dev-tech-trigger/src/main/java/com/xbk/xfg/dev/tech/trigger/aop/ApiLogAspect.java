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
import java.lang.reflect.Method;

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
     * 环绕通知：记录请求和响应
     */
    @Around("apiPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        // 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        
        // 获取请求参数
        Object[] args = joinPoint.getArgs();
        String params = "";
        try {
            params = JSON.toJSONString(args);
        } catch (Exception e) {
            params = "参数序列化失败";
        }
        
        // 打印请求日志
        log.info("==================== API Request Start ====================");
        log.info("URL         : {}", request != null ? request.getRequestURL().toString() : "N/A");
        log.info("HTTP Method : {}", request != null ? request.getMethod() : "N/A");
        log.info("Class       : {}", className);
        log.info("Method      : {}", methodName);
        log.info("IP          : {}", getIpAddr(request));
        log.info("Params      : {}", params);
        
        // 执行目标方法
        Object result = joinPoint.proceed();
        
        long costTime = System.currentTimeMillis() - startTime;
        
        // 打印响应日志（对于流式响应，只记录类型）
        if (result instanceof Flux) {
            log.info("Response    : [Flux Stream Response]");
        } else {
            try {
                String resultJson = JSON.toJSONString(result);
                // 限制日志长度，避免过长
                if (resultJson.length() > 500) {
                    resultJson = resultJson.substring(0, 500) + "...";
                }
                log.info("Response    : {}", resultJson);
            } catch (Exception e) {
                log.info("Response    : 响应序列化失败");
            }
        }
        
        log.info("Cost Time   : {} ms", costTime);
        log.info("==================== API Request End ======================\n");
        
        return result;
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
