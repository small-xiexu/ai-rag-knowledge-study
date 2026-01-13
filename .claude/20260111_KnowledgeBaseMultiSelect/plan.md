# 知识库多选功能实施计划

基于已审批的 [spec.md](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/.claude/20260111_KnowledgeBaseMultiSelect/spec.md)，本计划将分阶段实施知识库多选功能。

---

## Proposed Changes

### 阶段一：后端改造

#### [MODIFY] [IAiService.java](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-api/src/main/java/com/xbk/xfg/dev/tech/api/IAiService.java)

修改接口定义：`String ragTag` → `List<String> ragTags`

---

#### [MODIFY] [AiController.java](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-trigger/src/main/java/com/xbk/xfg/dev/tech/trigger/http/AiController.java)

修改 `generateStreamRag` 接口参数：

```diff
- @RequestParam("ragTag") String ragTag,
+ @RequestParam(value = "ragTags", required = false) List<String> ragTags,
```

---

#### [MODIFY] [AiDomainService.java](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-domain/src/main/java/com/xbk/xfg/dev/tech/domain/service/AiDomainService.java)

1. 修改方法签名：`List<String> ragTags`
2. 实现 OR 过滤表达式：`knowledge == 'a' || knowledge == 'b'`
3. 处理空列表情况

---

### 阶段二：前端改造

#### [MODIFY] [ai-chat.html](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-app/src/main/resources/static/ai-chat.html)

1. **UI 组件**：将单选下拉框替换为标签式多选组件
   - 已选标签展示区 + 删除按钮
   - "+ 添加"按钮 + 知识库选择弹窗
   - "全选/全不选"按钮
   - 已选数量提示 (x/10)

2. **交互逻辑**：
   - 最多选择 10 个
   - 达到上限时禁用添加

3. **API 调用**：
   - 修改 `sendMessage()` 收集多个 ragTags
   - URL 拼接：`ragTags=a&ragTags=b`

---

## Verification Plan

### 手动验证

1. 启动应用：`mvn spring-boot:run -pl xfg-dev-tech-app`
2. 访问 `http://localhost:8090/ai-chat.html`
3. 验证项：
   - 标签式多选组件渲染
   - 选择/删除知识库
   - 全选/全不选功能
   - 10 个上限限制
   - 多知识库 RAG 对话

### 接口验证

```bash
curl -N "http://localhost:8090/api/v1/ai/generate_stream_rag?ragTags=doc1&ragTags=doc2&message=hello"
```
