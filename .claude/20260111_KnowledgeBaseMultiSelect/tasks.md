# 知识库多选功能 - 任务清单

基于 [spec.md](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/.claude/20260111_KnowledgeBaseMultiSelect/spec.md) 和 [plan.md](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/.claude/20260111_KnowledgeBaseMultiSelect/plan.md)。

---

## 阶段一：后端改造 ✅

### 1.1 API 接口层
- [x] 修改 `IAiService.java`：`generateStreamRag` 方法参数 `String ragTag` → `List<String> ragTags`
- [x] 修改 `AiController.java`：`@RequestParam("ragTag")` → `@RequestParam(value = "ragTags", required = false) List<String>`

### 1.2 领域服务层
- [x] 修改 `AiDomainService.java#generateStreamRag` 方法签名
- [x] 实现 OR 过滤表达式构建：`ragTags.stream().map(...).collect(Collectors.joining(" || "))`
- [x] 处理 `ragTags` 为空或 null 的情况（走普通对话逻辑）

### 1.3 后端验证
- [x] Maven 编译通过

---

## 阶段二：前端改造 ✅

### 2.1 UI 组件开发
- [x] 移除原单选下拉框 `<select id="ragTagSelect">`
- [x] 新增标签式多选容器：已选标签区 + 添加按钮
- [x] 实现知识库选择功能
- [x] 实现标签删除功能（✕ 按钮）
- [x] 实现"全选/全不选"按钮
- [x] 添加已选数量提示 (x/10)

### 2.2 交互逻辑
- [x] 实现最大选择数（10 个）校验
- [x] 达到上限时禁用添加按钮并提示

### 2.3 API 调用改造
- [x] 修改 `loadRagTagList()` 函数，存储知识库列表供多选使用
- [x] 新增 `getSelectedRagTags()` 函数
- [x] 修改 `startStreamRequest()` 中的 URL 拼接逻辑

---

## 阶段三：集成测试 ✅

### 3.1 代码层验证（已完成）
- [x] Maven 编译通过（所有模块 BUILD SUCCESS）
- [x] 应用启动成功（Tomcat 8090 端口正常运行）
- [x] 后端 API 参数解析正确（`List<String> ragTags` 参数接受多值）
- [x] 前端多选组件代码就绪（`selectedRagTags`、`addRagTagBtn`、`renderSelectedRagTags` 等）
- [x] 前端 API 调用逻辑正确（`ragTags=a&ragTags=b` 格式拼接）

### 3.2 功能测试（需手动验证）
**测试环境准备：**
1. 配置 LLM 模型（在 http://localhost:8090 模型配置页面添加并激活）
2. 配置 Embedding 模型（用于知识库向量化）
3. 上传至少 2 个知识库数据（用于多选测试）

**测试用例：**
- [ ] 前端多选组件渲染正常（标签式显示、添加按钮可见）
- [ ] 选择知识库功能正常（可添加多个知识库标签）
- [ ] 删除知识库功能正常（点击标签 ✕ 按钮可移除）
- [ ] 全选/全不选按钮功能正常
- [ ] 10 个上限限制生效（达到上限时添加按钮禁用并提示）
- [ ] 多知识库 RAG 对话结果正确（后端日志显示 OR 过滤表达式）

**验证方法：**
```bash
# 1. 访问前端页面
open http://localhost:8090/ai-chat.html

# 2. 查看后端日志（验证 OR 过滤表达式）
tail -f /tmp/spring-boot-run.log | grep "多知识库检索"
```
