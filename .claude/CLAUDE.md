# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 **Spring AI 0.8.1** 和 **Spring Boot 3.2.3** 的 **RAG (检索增强生成)** 知识库系统，使用 Java 17 开发。

**核心功能：**
- 知识库文件上传与向量化存储（支持 PDF、Word、TXT 等）
- Git 仓库代码分析与向量化（异步任务 + 进度跟踪）
- 基于 PgVector 的向量相似性检索
- 支持 Ollama 本地模型和 OpenAI 兼容服务
- 动态 LLM 配置管理

## 技术栈约束

**Java 17 + Spring Boot 3.2.3 (Modern Mode)**

- **语法**: 使用 `var` 类型推断、`List.of()`、`Map.of()` 等现代特性
- **时间处理**: 使用 `java.time.*` (JSR-310)
- **依赖注入**: 使用 `@Resource` 或 `@Autowired`
- **工具库**: Lombok, Fastjson, Guava, Apache Commons Lang3
- **向量存储**: PostgreSQL + pgvector 扩展
- **缓存**: Redis (Redisson 客户端)
- **文档解析**: Apache Tika (Spring AI TikaDocumentReader)

## 模块结构

```
ai-rag-knowledge-study/
├── xfg-dev-tech-api/          # API 接口层
│   └── com.xbk.xfg.dev.tech.api
│       ├── IRAGService.java           # RAG 核心接口
│       ├── ILlmConfigService.java     # LLM 配置接口
│       ├── dto/                       # 数据传输对象
│       └── response/Response.java     # 统一响应格式
├── xfg-dev-tech-app/          # 应用核心层
│   └── com.xbk.xfg.dev.tech
│       ├── Application.java           # 主启动类
│       ├── config/
│       │   ├── AiConfig.java         # Spring AI 核心配置（Ollama/OpenAI API、向量存储）
│       │   └── RedisClientConfig.java # Redis 配置
│       └── service/
│           └── DynamicChatClientFactory.java  # 动态 LLM 客户端工厂
└── xfg-dev-tech-trigger/      # Web 触发层
    └── com.xbk.xfg.dev.tech.trigger.http
        └── RAGController.java         # RAG HTTP 接口实现
```

**依赖关系**: `xfg-dev-tech-app` 依赖 `xfg-dev-tech-trigger` 依赖 `xfg-dev-tech-api`

## 构建与运行命令

### 环境准备

```bash
# 确认 Java 版本（必须 Java 17）
java -version

# 如果需要切换 Java 版本（项目文档提到的自定义命令）
java17
```

### 构建命令

```bash
# 完整构建（跳过测试）
mvn clean compile -DskipTests

# 打包（生成 jar）
mvn clean package -DskipTests

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=RAGTest

# 运行单个测试方法
mvn test -Dtest=RAGTest#upload
```

### 启动应用

```bash
# 方式 1: Maven 插件启动（开发推荐）
mvn spring-boot:run

# 方式 2: 直接运行 jar（需先 package）
java -jar xfg-dev-tech-app/target/ai-rag-knowledge-app.jar

# 应用默认端口: 8090
# 启动后访问: http://localhost:8090
```

### 环境配置

应用使用 Spring Profiles，配置文件位于 `xfg-dev-tech-app/src/main/resources/`：

- `application.yml` - 主配置（默认激活 dev profile）
- `application-dev.yml` - 开发环境配置
- `application-test.yml` - 测试环境配置
- `application-prod.yml` - 生产环境配置

**外部依赖服务（必须启动）：**
- PostgreSQL (127.0.0.1:5432) - 需要安装 pgvector 扩展
- Redis (127.0.0.1:6379)
- Ollama (127.0.0.1:11434) - 本地 LLM 服务，需要拉取模型（如 `nomic-embed-text`）

可以使用 `docs/dev-ops/docker-compose-environment.yml` 启动环境：

```bash
cd docs/dev-ops
docker-compose -f docker-compose-environment.yml up -d
```

## 核心架构说明

### RAG 工作流程

**1. 数据准备（Ingestion）**

代码位置: `RAGController.uploadFile()` (xfg-dev-tech-trigger:180-182)

```
用户上传文件
  -> TikaDocumentReader 解析 (支持 PDF/Word/TXT)
  -> TokenTextSplitter 切块 (默认 800 tokens/chunk)
  -> 添加 metadata.knowledge 标签
  -> OllamaEmbeddingClient 向量化 (nomic-embed-text)
  -> PgVectorStore 存入 PostgreSQL
```

**2. Git 仓库分析（异步任务）**

代码位置: `RAGController.analyzeGitRepository()` (xfg-dev-tech-trigger:194-224)

```
提交 Git URL
  -> 返回 taskId
  -> 异步线程 (CompletableFuture)
      -> JGit 克隆代码到本地
      -> 过滤有效文件 (.java, .xml, .yml, .md 等)
      -> 逐个解析 + 向量化 + 入库
      -> 更新进度到 Redis (task:progress:{taskId})
  -> 前端轮询 /query_task_progress 获取进度
```

**关键设计：**
- 支持任务取消（检查 Redis key `task:stop:{taskId}`）
- 进度百分比 0-100，分阶段更新
- 文件过滤规则: 白名单模式（见 `RAGController.isValidFile()`）
- 临时文件在 `./git-cloned-repo/{taskId}` 目录

### Spring AI 配置核心

代码位置: `AiConfig.java` (xfg-dev-tech-app:config/AiConfig.java)

**关键 Bean：**

```java
// Ollama API 客户端（本地模型）
OllamaApi ollamaApi(String baseUrl)

// OpenAI API 客户端（兼容服务，如 88code.ai）
OpenAiApi openAiApi(String baseUrl, String apikey)

// 文本分割器（800 tokens/chunk, 400 tokens overlap）
TokenTextSplitter tokenTextSplitter()

// PgVector 向量存储（自动选择嵌入模型）
PgVectorStore pgVectorStore(String model, ...)
```

**嵌入模型切换逻辑:**
- 配置 `spring.ai.rag.embed=nomic-embed-text` -> 使用 Ollama 本地模型
- 配置 `spring.ai.rag.embed=text-embedding-ada-002` -> 使用 OpenAI 模型

### 数据库 Schema

**向量表（自动创建）:**
- 表名: `vector_store`
- 核心字段:
  - `content` (text) - 原始文本
  - `embedding` (vector) - 向量数据
  - `metadata` (jsonb) - 元数据，包含 `knowledge` 字段用于过滤

**元数据查询示例:**
```sql
DELETE FROM vector_store WHERE metadata->>'knowledge' = 'project-name';
```

## API 接口规范

**Base URL:** `http://localhost:8090/api/v1/rag/`

**关键接口：**

| 接口路径 | 方法 | 说明 |
|---------|------|------|
| `/query_rag_tag_list` | GET | 获取知识库标签列表（Redis key: `ragTag`） |
| `/delete_rag_tag` | POST | 删除知识库（含向量数据物理删除） |
| `/file/upload` | POST | 上传文件到知识库（multipart/form-data） |
| `/analyze_git_repository` | POST | 提交 Git 仓库分析任务（异步） |
| `/query_task_progress` | GET | 查询异步任务进度 |
| `/cancel_task` | POST | 取消异步任务 |

**响应格式:**
```json
{
  "code": "0000",  // 成功: "0000", 失败: 其他
  "info": "调用成功",
  "data": { ... }
}
```

## 开发注意事项

### 向量数据管理

- **删除知识库时**: 必须同时清理 PgVector 表和 Redis 标签列表（见 `RAGController.deleteRagTag()`）
- **元数据标签**: 所有文档块必须添加 `metadata.put("knowledge", ragTag)` 用于后续过滤
- **文本分割**: 使用 `TokenTextSplitter` 而非简单字符切割，避免截断 Token

### 异步任务开发

- **进度更新频率**: 建议每处理 5 个文件或进度变化 10% 时更新 Redis，避免频繁写入
- **取消检查点**: 在耗时操作前检查 `stopSignal.isExists()`
- **异常处理**: 单个文件失败不应影响整体任务（try-catch 包裹单次处理）
- **资源清理**: finally 块中删除临时文件和停止信号

### 配置文件

- **敏感信息**: API Key 应使用环境变量或加密配置管理（当前硬编码在 application-dev.yml:38）
- **Ollama 模型**: 确保本地已拉取模型 `ollama pull nomic-embed-text`

### 测试

- 测试类位置: `xfg-dev-tech-app/src/test/java/com/xbk/xfg/dev/tech/test/`
- 核心测试: `RAGTest.upload()` 演示完整上传流程
- 测试需要外部服务运行（PostgreSQL, Redis, Ollama）

## 常见问题排查

**问题 1: 编译失败 "无效的目标发行版: 17"**
```bash
# 确认 JAVA_HOME 指向 Java 17
java -version
# 重新编译
mvn clean compile -DskipTests
```

**问题 2: 向量化失败 "Connection refused"**
- 检查 Ollama 服务是否启动: `curl http://127.0.0.1:11434`
- 确认模型已下载: `ollama list | grep nomic-embed-text`

**问题 3: Git 克隆超时**
- 增加 JGit 超时配置（可在 `processGitRepository()` 中添加 `.setTimeout()`）
- 使用浅克隆: `.setDepth(1)`

**问题 4: 任务进度卡在某个百分比**
- 检查后台日志是否有异常
- 查看 Redis key `task:progress:{taskId}` 的状态
- 确认异步线程池未耗尽（默认使用 ForkJoinPool.commonPool）
