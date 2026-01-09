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
            
            // 单行日志格式: 带字段名，方便 ELK 索引
            log.info("method={} path={} ip={} duration={}ms method_name={} request={} response={}",
                    httpMethod, path, ip, costTime, methodName,
                    truncate(params, 100),
                    truncateJson(responseStr, 1000));
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
            Object[] safeArgs = sanitizeArgs(args);
            return JSON.toJSONString(safeArgs);
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
            Object safeResult = sanitizeArg(result);
            return JSON.toJSONString(safeResult);
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }

    /**
     * 处理不可序列化参数（如 MultipartFile/InputStream），替换为摘要信息
     */
    private Object[] sanitizeArgs(Object[] args) {
        if (args == null) return new Object[0];
        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            sanitized[i] = sanitizeArg(args[i]);
        }
        return sanitized;
    }

    private Object sanitizeArg(Object arg) {
        if (arg == null) return null;
        // 跳过 Reactor 流
        if (arg instanceof Flux) return "[Flux Stream]";
        // MultipartFile
        if (arg instanceof org.springframework.web.multipart.MultipartFile file) {
            return "MultipartFile(name=" + file.getName() + ", original=" + file.getOriginalFilename() + ", size=" + file.getSize() + ")";
        }
        // MultipartFile[]
        if (arg instanceof org.springframework.web.multipart.MultipartFile[] files) {
            return java.util.Arrays.stream(files)
                    .map(f -> "MultipartFile(name=" + f.getName() + ", original=" + f.getOriginalFilename() + ", size=" + f.getSize() + ")")
                    .toArray(String[]::new);
        }
        // InputStream
        if (arg instanceof java.io.InputStream) {
            return "[InputStream]";
        }
        // Servlet request/response
        if (arg instanceof jakarta.servlet.ServletRequest) return "[ServletRequest]";
        if (arg instanceof jakarta.servlet.ServletResponse) return "[ServletResponse]";
        return arg;
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
     * 智能截断 JSON，保证截断后仍是有效的 JSON 格式
     */
    private String truncateJson(String jsonStr, int maxLen) {
        if (jsonStr == null) return "null";
        if (jsonStr.length() <= maxLen) return jsonStr;
        
        try {
            Object obj = JSON.parse(jsonStr);
            return truncateJsonObject(obj, maxLen);
        } catch (Exception e) {
            // 不是有效 JSON，使用普通截断
            return truncate(jsonStr, maxLen);
        }
    }
    
    /**
     * 递归截断 JSON 对象
     */
    private String truncateJsonObject(Object obj, int maxLen) {
        if (obj == null) return "null";
        
        if (obj instanceof com.alibaba.fastjson.JSONObject) {
            com.alibaba.fastjson.JSONObject jsonObj = (com.alibaba.fastjson.JSONObject) obj;
            com.alibaba.fastjson.JSONObject result = new com.alibaba.fastjson.JSONObject(true);
            
            for (String key : jsonObj.keySet()) {
                Object value = jsonObj.get(key);
                // 对嵌套对象/数组进行截断
                if (value instanceof String) {
                    String strVal = (String) value;
                    result.put(key, strVal.length() > 100 ? strVal.substring(0, 100) + "..." : strVal);
                } else if (value instanceof com.alibaba.fastjson.JSONArray) {
                    com.alibaba.fastjson.JSONArray arr = (com.alibaba.fastjson.JSONArray) value;
                    if (arr.size() > 3) {
                        com.alibaba.fastjson.JSONArray truncatedArr = new com.alibaba.fastjson.JSONArray();
                        for (int i = 0; i < 3; i++) {
                            truncatedArr.add(arr.get(i));
                        }
                        truncatedArr.add("...[" + (arr.size() - 3) + " more]");
                        result.put(key, truncatedArr);
                    } else {
                        result.put(key, value);
                    }
                } else {
                    result.put(key, value);
                }
                
                // 检查当前长度
                String currentJson = result.toJSONString();
                if (currentJson.length() > maxLen) {
                    result.put(key, "...[truncated]");
                    break;
                }
            }
            return result.toJSONString();
        } else if (obj instanceof com.alibaba.fastjson.JSONArray) {
            com.alibaba.fastjson.JSONArray arr = (com.alibaba.fastjson.JSONArray) obj;
            if (arr.size() > 5) {
                com.alibaba.fastjson.JSONArray truncatedArr = new com.alibaba.fastjson.JSONArray();
                for (int i = 0; i < 5; i++) {
                    truncatedArr.add(arr.get(i));
                }
                truncatedArr.add("...[" + (arr.size() - 5) + " more]");
                return truncatedArr.toJSONString();
            }
            return arr.toJSONString();
        }
        
        return JSON.toJSONString(obj);
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
