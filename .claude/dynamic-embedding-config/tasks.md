# 动态向量化配置任务清单

## 阶段 1：配置清理与基础重构
- [ ] 清理 `xfg-dev-tech-app/src/main/resources/application-dev.yml` 中的 `spring.ai.openai.*` 配置项，保留 `spring.ai.ollama.base-url` 与 `spring.ai.rag.embed`。
- [ ] 精简 `xfg-dev-tech-app/src/main/java/com/xbk/xfg/dev/tech/config/AiConfig.java`：移除 `openAiApi()` Bean，按方案使用 Ollama embedding 构建 `pgVectorStore()`。
- [ ] 启动应用验证配置清理后仍可正常构建、启动、上传知识库（Ollama embedding）。

## 阶段 2：数据模型扩展
- [ ] 在 `xfg-dev-tech-api/src/main/java/com/xbk/xfg/dev/tech/api/dto/LlmProviderConfigDTO.java` 增加字段：`embeddingModel`、`embeddingDimension`、`activeForEmbedding`，补充注释/校验约束。
- [ ] 确认 Redis 存储结构新增 `llm:provider:active:embedding`，序列化/反序列化不受影响（兼容已有 Hash 存储）。
- [ ] 如有前端表单，补充 embedding 模型名、维度、激活标记字段。

## 阶段 3：核心工厂与策略实现
- [ ] 定义 `EmbeddingStrategy` 接口与实现目录 `xfg-dev-tech-domain/src/main/java/com/xbk/xfg/dev/tech/domain/strategy/embedding/`。
- [ ] 实现 `OllamaEmbeddingStrategy`（支持 providerType=OLLAMA）和 `OpenAiEmbeddingStrategy`（支持 OPENAI/GLM），可扩展其他厂商占位。
- [ ] 编写 `DynamicEmbeddingFactory`：缓存与读写锁、激活逻辑、维度检测、强制清空流程、Redis 状态持久化。
- [ ] 实现 `LazyEmbeddingModel` 适配器，调用时动态获取当前激活模型。
- [ ] 修改 `AiConfig.java` 注入 `DynamicEmbeddingFactory`，使用 `LazyEmbeddingModel` 构建 `PgVectorStore`。

## 阶段 4：API 与触发层
- [ ] 在 `ILlmConfigService` / `LlmConfigDomainService` / `LlmConfigController` 增加端点：`POST /api/v1/llm/configs/{id}/activate-embedding`（force 参数）、`GET /api/v1/llm/configs/active-embedding`。
- [ ] 更新配置更新 API 校验：`embeddingModel` 非空时 `embeddingDimension` 必填；已激活配置变更维度时返回需清空提示。
- [ ] 衔接知识库清空逻辑（PgVector 全量删除 + Redis ragTag 清空），确保异常处理与事务/回滚策略明确。

## 阶段 5：前端交互与安全确认
- [ ] 配置表单增加 embedding 模型名、维度输入与“激活为 Embedding”操作入口。
- [ ] 维度不兼容时弹出警告弹窗，展示当前/新模型、维度、知识库数量、向量数量，提示“永久删除”并要求二次确认。
- [ ] 确认按钮默认禁用，需勾选/确认文本后才可执行强制切换；成功/失败反馈明确。

## 阶段 6：测试与验证
- [ ] 单元测试：策略支持判定与模型构建；`DynamicEmbeddingFactory` 维度检测、缓存切换；`LazyEmbeddingModel` 代理行为。
- [ ] 集成测试：激活接口 force=false 维度不兼容提示；force=true 清空后激活；查询激活配置返回正确字段。
- [ ] 端到端测试：Ollama 全流程；切换至 OpenAI 触发清空；同维度模型切换不清空。
- [ ] 验证 UI 警告、二次确认不可绕过，操作反馈及时；记录测试命令与结果。

## 交付与风险管控
- [ ] 输出配置清理后的应用启动验证记录。
- [ ] 输出 API/领域层变更说明与 Redis Key 约定。
- [ ] 输出前端交互稿或截图，强调不可逆清空提示。
- [ ] 风险回归：维度检测正确性、清空失败回滚、缓存性能基线。
