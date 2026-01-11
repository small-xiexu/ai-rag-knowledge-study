# AI-RAG-Knowledge Docker 部署任务列表

> **创建日期**: 2026-01-10  
> **基于文档**: [plan.md](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/.claude/docker-deploy/plan.md) | [spec.md](file:///Users/xiexu/xiaofu/ai-rag-knowledge-study/.claude/docker-deploy/spec.md)

---

## Phase 0: 代码前置修改 ✅

### 添加 Actuator 依赖
- [x] 在 `xfg-dev-tech-app/pom.xml` 中添加 `spring-boot-starter-actuator` 依赖
- [x] 验证依赖添加正确 (已在 pom.xml:35-39 行添加)

---

## Phase 1: 环境准备

### 目录创建 ✅
- [x] 创建 `.claude/docker-deploy/frontend` 目录
- [x] 创建 `.claude/docker-deploy/certs` 目录
- [x] 创建 `.claude/docker-deploy/nginx/conf.d` 目录
- [x] 创建 `.claude/docker-deploy/scripts` 目录
- [x] 创建 `/Users/xiexu/logs` 日志目录并设置权限

### 数据库初始化 ✅
- [x] 创建 `ai-rag-knowledge` 数据库
- [x] 启用 `pgvector` 扩展
- [x] 验证数据库创建成功

### SSL 证书生成 ✅
- [x] 生成自签名 SSL 证书 (`localhost.crt`)
- [x] 生成私钥文件 (`localhost.key`)
- [x] 验证证书有效性 (有效期 365 天)

---

## Phase 2: 配置文件创建 ✅

### Dockerfile ✅
- [x] 创建 `.claude/docker-deploy/Dockerfile`
  - [x] 配置基础镜像 `openjdk:17-jdk-slim`
  - [x] 配置时区 PRC
  - [x] 配置 JVM 参数 `-Xms512m -Xmx512m`
  - [x] 配置 ENTRYPOINT 启动命令

### docker-compose.yml ✅
- [x] 创建 `.claude/docker-deploy/docker-compose.yml`
  - [x] 配置 Nginx 服务 (80/443 端口)
  - [x] 配置应用服务 (8090 端口)
  - [x] 配置外部网络 `binghe-network`
  - [x] 配置健康检查
  - [x] 配置日志轮转
  - [x] 配置卷挂载

### 环境变量 ✅
- [x] 创建 `.claude/docker-deploy/.env`
  - [x] 配置应用版本和端口
  - [x] 配置数据库连接信息
  - [x] 配置 Redis 连接信息
  - [x] 配置 Ollama 连接信息

### Nginx 配置 ✅
- [x] 创建 `.claude/docker-deploy/nginx/nginx.conf`
  - [x] 配置 worker 进程
  - [x] 配置 Gzip 压缩
  - [x] 配置日志格式

- [x] 创建 `.claude/docker-deploy/nginx/conf.d/default.conf`
  - [x] 配置 HTTP 重定向到 HTTPS
  - [x] 配置 SSL 证书
  - [x] 配置静态资源服务
  - [x] 配置 `/api/*` 反向代理
  - [x] 配置 `/actuator/*` 代理
  - [x] 配置 SSE 流式响应支持
  - [x] 配置静态资源缓存

---

## Phase 3: 前端资源准备 ✅

### 静态资源复制 ✅
- [x] 从 `xfg-dev-tech-app/src/main/resources/static/` 复制文件到 `frontend/`
  - [x] 复制 `ai-chat.html`
  - [x] 复制 `model-config.html`
  - [x] 复制 `knowledge.html`
  - [x] 复制 `upload.html`

---

## Phase 4: 镜像构建与部署

### 应用构建
- [x] 执行 `mvn clean package -DskipTests`
- [x] 验证 JAR 文件生成 (`ai-rag-knowledge-app.jar`)

### Docker 镜像构建
- [x] 构建应用镜像 `ai-rag-knowledge-app:1.0`
- [x] 验证镜像构建成功

### 服务启动
- [x] 执行 `docker-compose up -d`
- [x] 验证 Nginx 容器启动
- [x] 验证应用容器启动

---

## Phase 4.5: 配置优化 ✅

### 日志路径改进
- [x] 删除参考文件（别人的 docker-compose*.yml 和 Dockerfile）
- [x] 修改 `.env` 日志路径为相对路径 `LOG_PATH=./logs`
- [x] 修改 `docker-compose.yml` 使用环境变量 `${LOG_PATH:-./logs}`
- [x] 验证 .gitignore 已包含日志目录排除规则
- [x] 创建本地日志目录 `.claude/docker-deploy/logs/`
- [x] 验证日志挂载成功（log_info.log 和 log_error.log 已生成）

---

## Phase 5: 验证与测试

### 容器健康检查
- [x] 验证 Nginx 容器状态为 `healthy`
- [x] 验证应用容器状态为 `healthy`

### 服务连通性测试
- [x] 测试 HTTPS 访问 (`https://localhost`)
- [x] 测试 HTTP 自动重定向
- [x] 测试 API 代理 (`/api/*`)
- [x] 测试 Actuator 代理 (`/actuator/health`)
- [x] 测试后端直连 (`http://localhost:8090`)

### 中间件连通性验证
- [ ] 验证 PostgreSQL 连接
- [ ] 验证 Redis 连接
- [ ] 验证 Ollama 连接

### 功能测试
- [ ] 测试 AI 聊天页面
- [ ] 测试 SSE 流式响应
- [ ] 测试知识库功能
- [ ] 测试文件上传功能

---

## 脚本工具创建

### 部署脚本
- [x] 创建 `scripts/deploy.sh` 一键部署脚本
  - [x] 检查 Docker 运行状态
  - [x] 复制前端资源
  - [x] 检查并生成 SSL 证书
  - [x] 构建应用 JAR
  - [x] 构建 Docker 镜像
  - [x] 启动服务
  - [x] 健康检查验证

### 数据库初始化脚本
- [x] 创建 `scripts/init-db.sh`
  - [x] 检查 vector_db 容器状态
  - [x] 创建 ai-rag-knowledge 数据库
  - [x] 启用 pgvector 扩展

### SSL 证书生成脚本
- [x] 创建 `scripts/gen-ssl-cert.sh`
  - [x] 生成自签名证书
  - [x] 配置 SAN (Subject Alternative Name)

### 脚本权限
- [x] 设置所有脚本执行权限 (`chmod +x scripts/*.sh`)

---

## 文件清单

| 文件 | 状态 | 说明 |
|------|------|------|
| `xfg-dev-tech-app/pom.xml` | [x] 已完成 | 添加 Actuator 依赖 |
| `.claude/docker-deploy/Dockerfile` | [x] 已创建 | 应用镜像构建 |
| `.claude/docker-deploy/docker-compose.yml` | [x] 已创建 | 服务编排 |
| `.claude/docker-deploy/.env` | [x] 已创建 | 环境变量 |
| `.claude/docker-deploy/nginx/nginx.conf` | [x] 已创建 | Nginx 主配置 |
| `.claude/docker-deploy/nginx/conf.d/default.conf` | [x] 已创建 | 虚拟主机配置 |
| `.claude/docker-deploy/certs/localhost.crt` | [x] 已生成 | SSL 证书 |
| `.claude/docker-deploy/certs/localhost.key` | [x] 已生成 | SSL 私钥 |
| `.claude/docker-deploy/frontend/*` | [x] 已复制 | 前端静态资源 |
| `.claude/docker-deploy/scripts/deploy.sh` | [x] 已创建 | 一键部署脚本 |
| `.claude/docker-deploy/scripts/init-db.sh` | [x] 已创建 | 数据库初始化脚本 |
| `.claude/docker-deploy/scripts/gen-ssl-cert.sh` | [x] 已创建 | SSL 证书生成脚本 |

---

> **下一步**: 从 Phase 0 开始，逐项完成任务并标记为 `[x]`
