# API 格式切换功能规范

## 背景

88code 等中转站使用的是 **CLI 工具原生格式**（如 Claude Code、Codex），与标准 API 格式不同：

| 提供商 | CLI 格式 | 标准 API 格式 |
|--------|---------|--------------|
| Claude | `system` 为数组，Anthropic Messages API | `system` 为字符串 |
| Codex  | OpenAI Responses API (`instructions` 字段) | Chat Completions API (`messages` 字段) |

## 功能需求

在模型配置页面添加 **API 格式** 选项，允许用户选择：

1. **标准 API 格式** (默认) - Spring AI 原生客户端
2. **CLI 格式** - 自定义 HTTP 客户端，适配 CLI 工具中转站

## 数据模型变更

### LlmProviderConfigDTO

```java
public class LlmProviderConfigDTO {
    // 现有字段...
    
    /**
     * API 格式类型
     * STANDARD - 标准 API 格式 (Spring AI 客户端)
     * CLI - CLI 工具格式 (自定义 HTTP 客户端)
     */
    private String apiFormat; // "STANDARD" | "CLI"
}
```

## 前端变更

### model-config.html

在配置表单中添加格式切换：

```html
<div class="space-y-1.5">
    <label>API 格式</label>
    <div class="flex gap-4">
        <label class="flex items-center gap-2">
            <input type="radio" name="apiFormat" value="STANDARD" checked>
            <span>标准 API</span>
        </label>
        <label class="flex items-center gap-2">
            <input type="radio" name="apiFormat" value="CLI">
            <span>CLI 格式</span>
        </label>
    </div>
    <p class="text-xs text-gray-500">
        CLI 格式适用于 Claude Code/Codex 中转站
    </p>
</div>
```

### 格式切换逻辑

根据选择的 `providerType` 自动推荐格式：
- OLLAMA → 标准 API (不支持切换)
- OPENAI → 可选 标准/CLI
- ANTHROPIC → 可选 标准/CLI
- GLM → 标准 API (不支持切换)
- VERTEX_AI → 标准 API (不支持切换)

## 后端变更

### 策略模式扩展

```
ChatClientStrategy (接口)
├── OpenAiChatClientStrategy      // 标准 OpenAI Chat Completions
├── OpenAiCliChatClientStrategy   // Codex Responses API (CLI 格式)
├── AnthropicChatClientStrategy   // 标准 Anthropic Messages API
├── AnthropicCliChatClientStrategy // Claude Code CLI 格式
├── OllamaChatClientStrategy      // Ollama (仅标准)
└── VertexAiChatClientStrategy    // Vertex AI (仅标准)
```

### CLI 策略实现

#### OpenAiCliChatClientStrategy

```java
@Component
public class OpenAiCliChatClientStrategy implements ChatClientStrategy {
    
    @Override
    public boolean supports(String providerType, String apiFormat) {
        return "OPENAI".equalsIgnoreCase(providerType) 
            && "CLI".equalsIgnoreCase(apiFormat);
    }
    
    @Override
    public ChatClientWrapper createClient(LlmProviderConfigDTO config) {
        // 使用 RestTemplate/WebClient 发送 Responses API 格式请求
        // POST /v1/responses
        // Body: { "model": "...", "instructions": "...", "input": "..." }
    }
}
```

#### AnthropicCliChatClientStrategy

```java
@Component
public class AnthropicCliChatClientStrategy implements ChatClientStrategy {
    
    @Override
    public boolean supports(String providerType, String apiFormat) {
        return "ANTHROPIC".equalsIgnoreCase(providerType) 
            && "CLI".equalsIgnoreCase(apiFormat);
    }
    
    @Override
    public ChatClientWrapper createClient(LlmProviderConfigDTO config) {
        // 使用 RestTemplate/WebClient 发送 CLI 格式请求
        // POST /v1/messages
        // Body: { "model": "...", "system": [...], "messages": [...] }
    }
}
```

## API 请求格式对比

### OpenAI

**标准 Chat Completions API:**
```json
POST /v1/chat/completions
{
  "model": "gpt-4",
  "messages": [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Hello"}
  ]
}
```

**Codex Responses API (CLI 格式):**
```json
POST /v1/responses
{
  "model": "gpt-5.2",
  "instructions": "You are a helpful assistant.",
  "input": "Hello"
}
```

### Anthropic

**标准 Messages API:**
```json
POST /v1/messages
{
  "model": "claude-3-opus",
  "system": "You are a helpful assistant.",
  "messages": [{"role": "user", "content": "Hello"}]
}
```

**Claude Code CLI 格式:**
```json
POST /v1/messages
{
  "model": "claude-opus-4-5",
  "system": [{"type": "text", "text": "You are a helpful assistant."}],
  "messages": [{"role": "user", "content": "Hello"}]
}
```

## 实现优先级

1. **P0**: 添加 `apiFormat` 字段到数据模型
2. **P0**: 前端添加格式切换 UI
3. **P1**: 实现 `AnthropicCliChatClientStrategy`
4. **P1**: 实现 `OpenAiCliChatClientStrategy`
5. **P2**: 策略选择逻辑支持 `apiFormat` 参数

## 测试计划

1. 标准格式：Ollama, OpenAI 官方, Anthropic 官方
2. CLI 格式：88code Claude Code 端点, 88code Codex 端点
3. 格式切换：配置保存/加载正确性

## 风险与依赖

- CLI 格式依赖中转站的具体实现，可能需要根据实际 API 文档调整
- 流式响应格式可能也有差异，需要单独处理
