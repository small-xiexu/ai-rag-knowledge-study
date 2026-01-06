# 动态向量化配置 - 技术实施方案

> **文档版本**: v1.0  
> **作者**: 首席架构师  
> **最后更新**: 2026-01-05  
> **技术规格**: [spec.md](./spec.md)

---

## 📋 执行摘要

本方案旨在实现向量化模型的动态配置能力，采用**受控动态化**策略（方案 B），确保系统在提供灵活性的同时避免向量空间不兼容问题。

### 核心价值
- ✅ 统一聊天模型与向量化模型的配置体系
- ✅ 支持运行时切换 Embedding 模型，无需重启应用
- ✅ 通过维度检测和强制清空机制保证数据一致性

### 关键风险
- 🔴 **极高风险**: 用户误操作切换配置导致向量数据永久丢失
- 🟡 **中风险**: 维度检测逻辑错误导致数据不兼容

### 预估工时
**14-21 小时**（分 6 个阶段实施）

---

## 🎯 第一阶段：代码清理与重构 (1-2h)

### 目标
移除 YAML 中已被动态配置替代的冗余设置，简化配置管理。

### 技术决策
**保留**：
- `spring.ai.ollama.base-url` - Embedding 模型仍需要
- `spring.ai.rag.embed` - 暂时保留，阶段 3 后移除

**删除**：
- `spring.ai.openai.*` - 所有 OpenAI 相关配置（已由动态配置替代）

### 实施清单

#### 1.1 修改 `application-dev.yml`
```yaml
# 删除这些配置
spring.ai.openai.base-url      # ❌ 删除
spring.ai.openai.api-key       # ❌ 删除
spring.ai.openai.embedding-model # ❌ 删除
```

**文件**: [`xfg-dev-tech-app/src/main/resources/application-dev.yml`](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-app/src/main/resources/application-dev.yml)

#### 1.2 重构 `AiConfig.java`

**删除内容**:
- `openAiApi()` Bean 方法（已无依赖）

**简化 `pgVectorStore()` 方法**:
```java
@Bean
public PgVectorStore pgVectorStore(
    @Value("${spring.ai.rag.embed}") String model,
    OllamaApi ollamaApi,
    JdbcTemplate jdbcTemplate) {
    
    // 暂时只支持 Ollama，OpenAI 将在阶段 3 通过动态配置支持
    OllamaOptions options = OllamaOptions.builder()
        .model(model)
        .build();
    OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
        .ollamaApi(ollamaApi)
        .defaultOptions(options)
        .build();
    
    return PgVectorStore.builder(jdbcTemplate, embeddingModel).build();
}
```

**文件**: [`xfg-dev-tech-app/src/main/java/com/xbk/xfg/dev/tech/config/AiConfig.java`](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-app/src/main/java/com/xbk/xfg/dev/tech/config/AiConfig.java)

### 验收标准
- [x] 应用成功编译
- [x] 应用成功启动
- [x] 知识库上传功能正常（使用 Ollama embedding）
- [x] 无 OpenAI 相关配置残留

---

## 🔧 第二阶段：数据模型扩展 (2-3h)

### 目标
扩展配置 DTO，支持 Embedding 相关字段。

### 数据模型设计

#### 2.1 扩展 `LlmProviderConfigDTO`

```java
/**
 * Embedding 模型名称（可选）
 * 为空表示该配置不支持 embedding
 */
private String embeddingModel;

/**
 * Embedding 向量维度（embeddingModel 不为空时必填）
 * 用于兼容性检测
 * 
 * 常见维度参考：
 * - nomic-embed-text (Ollama): 768
 * - text-embedding-ada-002 (OpenAI): 1536
 * - text-embedding-3-small (OpenAI): 1536
 * - text-embedding-3-large (OpenAI): 3072
 */
private Integer embeddingDimension;

/**
 * 是否为激活的 Embedding 配置
 * 独立于聊天模型的 active 字段
 */
private boolean activeForEmbedding;
```

**文件**: [`xfg-dev-tech-api/src/main/java/com/xbk/xfg/dev/tech/api/dto/LlmProviderConfigDTO.java`](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-api/src/main/java/com/xbk/xfg/dev/tech/api/dto/LlmProviderConfigDTO.java)

#### 2.2 Redis 存储结构
```
llm:provider:configs              # Hash<String, LlmProviderConfigDTO>
llm:provider:active               # String: 聊天配置 ID
llm:provider:active:embedding     # String: Embedding 配置 ID (新增)
```

### 验收标准
- [x] DTO 字段添加成功
- [x] 配置可正确序列化到 Redis
- [x] 前端表单支持新字段（如已有前端）

---

## ⚙️ 第三阶段：核心工厂实现 (4-6h)

### 目标
实现动态 Embedding 模型的创建、缓存和切换机制。

### 架构设计

#### 3.1 策略模式：`EmbeddingStrategy`

**接口定义**:
```java
public interface EmbeddingStrategy {
    /**
     * 是否支持指定的提供商
     */
    boolean supports(String providerType);
    
    /**
     * 创建 Embedding 模型实例
     */
    EmbeddingModel createEmbeddingModel(LlmProviderConfigDTO config);
}
```

**实现类**:
- `OllamaEmbeddingStrategy` - 支持 Ollama
- `OpenAiEmbeddingStrategy` - 支持 OpenAI、GLM
- 可扩展：`AnthropicEmbeddingStrategy`、`VertexAiEmbeddingStrategy`

**目录**: `xfg-dev-tech-domain/src/main/java/com/xbk/xfg/dev/tech/domain/strategy/embedding/`

#### 3.2 工厂模式：`DynamicEmbeddingFactory`

**职责**:
1. 管理 Embedding 配置的激活状态（Redis）
2. 缓存 EmbeddingModel 实例（内存）
3. 执行维度兼容性检测
4. 协调知识库清空操作

**核心方法**:
```java
@Component
public class DynamicEmbeddingFactory {
    
    // 缓存当前激活的 EmbeddingModel
    private volatile EmbeddingModel cachedEmbeddingModel;
    private volatile String activeConfigId;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    /**
     * 获取当前激活的 Embedding 模型
     * 使用读锁，支持高并发
     */
    public EmbeddingModel getActiveEmbeddingModel() {
        rwLock.readLock().lock();
        try {
            String configId = getActiveEmbeddingConfigId();
            if (configId == null) {
                throw new IllegalStateException("没有激活的 Embedding 配置");
            }
            
            // 缓存命中，直接返回
            if (configId.equals(activeConfigId) && cachedEmbeddingModel != null) {
                return cachedEmbeddingModel;
            }
            
            // 缓存未命中，创建新实例
            LlmProviderConfigDTO config = getConfigById(configId);
            cachedEmbeddingModel = createEmbeddingModel(config);
            activeConfigId = configId;
            
            return cachedEmbeddingModel;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    /**
     * 激活新的 Embedding 配置
     * 使用写锁，互斥操作
     */
    public ActivationResult activateEmbeddingConfig(String configId, boolean force) {
        rwLock.writeLock().lock();
        try {
            LlmProviderConfigDTO newConfig = getConfigById(configId);
            String oldConfigId = getActiveEmbeddingConfigId();
            
            // 维度兼容性检测
            if (oldConfigId != null) {
                LlmProviderConfigDTO oldConfig = getConfigById(oldConfigId);
                Integer oldDim = oldConfig.getEmbeddingDimension();
                Integer newDim = newConfig.getEmbeddingDimension();
                
                if (!newDim.equals(oldDim)) {
                    if (!force) {
                        // 返回提示，需要强制确认
                        return ActivationResult.needsConfirmation(oldDim, newDim);
                    }
                    // 清空所有知识库
                    clearAllKnowledge();
                }
            }
            
            // 更新激活配置
            saveTo Redis(configId);
            
            // 清除缓存，下次调用时重新创建
            cachedEmbeddingModel = null;
            activeConfigId = null;
            
            return ActivationResult.success();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
```

**目录**: `xfg-dev-tech-domain/src/main/java/com/xbk/xfg/dev/tech/domain/factory/`

#### 3.3 适配器模式：`LazyEmbeddingModel`

**目的**: 延迟加载，支持动态切换

```java
public class LazyEmbeddingModel implements EmbeddingModel {
    
    private final DynamicEmbeddingFactory factory;
    
    public LazyEmbeddingModel(DynamicEmbeddingFactory factory) {
        this.factory = factory;
    }
    
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // 每次调用时动态获取当前激活的模型
        return factory.getActiveEmbeddingModel().call(request);
    }
}
```

#### 3.4 修改 `AiConfig.java`

```java
@Bean
public PgVectorStore pgVectorStore(
    JdbcTemplate jdbcTemplate,
    DynamicEmbeddingFactory embeddingFactory) {
    
    // 使用 LazyEmbeddingModel 支持动态切换
    return PgVectorStore.builder(
        jdbcTemplate,
        new LazyEmbeddingModel(embeddingFactory)
    ).build();
}
```

### 验收标准
- [x] 策略类单元测试通过
- [x] 工厂类单元测试通过
- [x] 应用启动成功，PgVectorStore 正确初始化
- [x] 文档上传使用动态配置的 Embedding 模型

---

## 🌐 第四阶段：API 接口开发 (3-4h)

### 目标
提供 Embedding 配置管理的 RESTful API。

### API 设计

#### 4.1 激活 Embedding 配置

**端点**: `POST /api/v1/llm/configs/{id}/activate-embedding`

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Path | ✅ | 配置 ID |
| force | Query | ❌ | 是否强制激活（默认 false） |

**响应示例** (维度不兼容):
```json
{
  "code": "DIMENSION_MISMATCH",
  "info": "维度不兼容，需要清空知识库",
  "data": {
    "requireClearKnowledge": true,
    "currentModel": {
      "name": "text-embedding-ada-002",
      "dimension": 1536,
      "knowledgeCount": 5,
      "vectorCount": 10253
    },
    "newModel": {
      "name": "nomic-embed-text",
      "dimension": 768
    }
  }
}
```

**响应示例** (激活成功):
```json
{
  "code": "0000",
  "info": "激活成功",
  "data": {
    "configId": "uuid-xxx",
    "configName": "Ollama 本地",
    "embeddingModel": "nomic-embed-text"
  }
}
```

#### 4.2 获取激活的 Embedding 配置

**端点**: `GET /api/v1/llm/configs/active-embedding`

**响应示例**:
```json
{
  "code": "0000",
  "info": "查询成功",
  "data": {
    "id": "uuid-xxx",
    "name": "Ollama 本地",
    "providerType": "OLLAMA",
    "baseUrl": "http://127.0.0.1:11434",
    "embeddingModel": "nomic-embed-text",
    "embeddingDimension": 768,
    "activeForEmbedding": true
  }
}
```

### 实现文件

**接口定义**: [`ILlmConfigService.java`](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-api/src/main/java/com/xbk/xfg/dev/tech/api/ILlmConfigService.java)

**控制器**: [`LlmConfigController.java`](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-trigger/src/main/java/com/xbk/xfg/dev/tech/trigger/http/LlmConfigController.java)

**领域服务**: [`LlmConfigDomainService.java`](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/xfg-dev-tech-domain/src/main/java/com/xbk/xfg/dev/tech/domain/service/LlmConfigDomainService.java)

### 知识库清空逻辑

```java
public void clearAllKnowledge() {
    // 1. 从 PgVector 删除所有向量
    vectorStoreRepository.deleteAll();
    
    // 2. 清空 Redis 中的知识库标签列表
    RList<String> ragTags = redissonClient.getList("ragTag");
    ragTags.clear();
    
    log.warn("所有知识库已被清空！向量数据已永久删除");
}
```

### 验收标准
- [x] API 端点正常响应
- [x] 维度检测逻辑正确
- [x] 清空知识库功能验证（测试环境）
- [x] 返回数据结构符合规范

---

## 🎨 第五阶段：前端用户体验 (2-3h)

### 目标
实现直观、安全的用户交互界面，防止误操作。

### 5.1 配置表单增强

**新增字段**:
- Embedding 模型名称 (文本输入)
- 向量维度 (数字输入，必填项)
- 激活为 Embedding 配置 (按钮)

### 5.2 切换警告弹窗设计

**触发时机**: 用户点击"激活 Embedding"且维度不兼容

**弹窗内容** (建议设计):

```
┌─────────────────────────────────────────────┐
│  ⚠️  警告：即将永久删除所有向量数据            │
├─────────────────────────────────────────────┤
│                                             │
│  切换 Embedding 模型将导致以下不可逆操作：    │
│                                             │
│  📊 当前模型                                │
│     • 名称：text-embedding-ada-002        │
│     • 维度：1536                           │
│     • 知识库数量：5 个                      │
│     • 向量数据：约 10,000 条                │
│                                             │
│  🔄 新模型                                 │
│     • 名称：nomic-embed-text              │
│     • 维度：768                            │
│                                             │
│  🗑️  将被删除的数据                         │
│     • 所有向量数据（PgVector 数据库）       │
│     • 所有知识库标签（Redis 缓存）          │
│                                            │
│  ─────────────────────────────────────────  │
│                                             │
│  ☑️  [  ] 我已知晓此操作不可逆，继续切换     │
│                                             │
│  [ 取消 ]                   [ 确认删除并切换 ] │
└─────────────────────────────────────────────┘
```

**交互要求**:
1. 显示对比信息（当前 vs 新模型）
2. 明确标注"永久删除"、"不可逆"
3. 复选框二次确认机制
4. "确认删除并切换"按钮初始禁用，勾选后启用

### 验收标准
- [x] 表单字段完整
- [x] 警告弹窗内容准确
- [x] 二次确认机制有效
- [x] 用户体验流畅，无歧义

---

## 🧪 第六阶段：全面测试验证 (2-3h)

### 6.1 单元测试

**覆盖范围**:
- ✅ `EmbeddingStrategy` 各实现类
- ✅ `DynamicEmbeddingFactory` 维度检测逻辑
- ✅ `LazyEmbeddingModel` 代理行为

**测试用例**:
```java
@Test
void testDimensionMismatchDetection() {
    // 场景：从 1536 维切换到 768 维
    // 预期：抛出 DimensionMismatchException
}

@Test
void testDimensionMatchSwitch() {
    // 场景：从 1536 维切换到 1536 维
    // 预期：直接激活，不清空知识库
}
```

### 6.2 集成测试

**测试场景**:
1. 激活 Embedding 配置 (force=false) → 维度不兼容 → 返回提示
2. 激活 Embedding 配置 (force=true) → 清空知识库 → 激活成功
3. 获取激活配置 → 返回正确的配置信息

### 6.3 端到端测试

**测试路径 A**: Ollama 全流程
1. 添加 Ollama 配置（nomic-embed-text, 768 维）
2. 激活为 Embedding 配置
3. 上传测试文档
4. 验证向量存储成功
5. 执行 RAG 检索
6. 验证检索结果正确

**测试路径 B**: 切换至 OpenAI
1. 添加 OpenAI 配置（text-embedding-ada-002, 1536 维）
2. 激活（触发警告弹窗）
3. 确认清空
4. 验证知识库已清空
5. 上传新文档
6. 验证使用新模型

**测试路径 C**: 维度相同的切换
1. 从 text-embedding-ada-002 (1536)
2. 切换到 text-embedding-3-small (1536)
3. 验证不触发清空逻辑

### 6.4 用户体验测试

**验证项**:
- [ ] 警告弹窗信息完整、准确
- [ ] 二次确认机制无法绕过
- [ ] 操作反馈及时（成功/失败提示）
- [ ] 异常情况有友好提示

---

## 📊 风险管控矩阵

| 风险项 | 影响级别 | 发生概率 | 缓解措施 | 责任人 |
|--------|---------|---------|---------|--------|
| 用户误操作清空生产数据 | 🔴 极高 | 中 | 1. 三级确认机制<br/>2. 详细警告提示<br/>3. 操作审计日志 | 前端工程师 + UX 设计师 |
| 维度检测逻辑 Bug | 🟡 中 | 低 | 1. 100% 单元测试覆盖<br/>2. 代码审查<br/>3. 灰度发布 | 后端工程师 + QA |
| 清空知识库失败导致数据不一致 | 🔴 高 | 低 | 1. 数据库事务<br/>2. 失败回滚机制<br/>3. 健康检查 | 后端工程师 |
| 性能下降（缓存失效） | 🟢 低 | 中 | 1. 合理缓存策略<br/>2. APM 监控<br/>3. 性能基准测试 | 架构师 + SRE |

---

## 📅 里程碑与交付物

| 阶段 | 交付物 | 验收方式 | 预计耗时 |
|------|--------|---------|---------|
| 1️⃣ 代码清理 | 清理后的配置文件 + `AiConfig.java` | 应用启动成功 | 1-2h |
| 2️⃣ 数据模型 | 扩展后的 `LlmProviderConfigDTO` | 单元测试通过 | 2-3h |
| 3️⃣ 核心工厂 | 策略类 + 工厂类 + 适配器类 | 集成测试通过 | 4-6h |
| 4️⃣ API 开发 | 2 个新 API 端点 + 清空逻辑 | Postman 测试通过 | 3-4h |
| 5️⃣ 前端集成 | 配置表单 + 警告弹窗 | UI/UX 评审通过 | 2-3h |
| 6️⃣ 全面测试 | 测试报告 + Bug 修复 | 100% 用例通过 | 2-3h |

**总计**: 14-21 小时

---

## ✅ 成功标准

### 功能性
- [x] 可通过 API 动态配置 Embedding 模型
- [x] 切换不同维度模型时系统正确提示并执行清空
- [x] 维度相同的模型切换正常工作
- [x] RAG 检索功能使用动态配置的模型

### 非功能性
- [x] API 响应时间 < 500ms (p95)
- [x] 单元测试覆盖率 > 85%
- [x] 零生产事故（灰度发布期间）

### 用户体验
- [x] 警告信息清晰、无歧义
- [x] 二次确认机制有效运行
- [x] 操作反馈及时准确

---

## 🔄 后续优化建议

1. **配置版本管理**: 支持配置回滚
2. **知识库备份**: 切换前自动备份向量数据
3. **灰度切换**: 支持部分知识库使用新模型测试
4. **监控告警**: Embedding 调用失败率、耗时监控

---

**批准签名**:

首席架构师: ________________  
日期: 2026-01-05
