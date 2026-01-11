# 05 - Nginx 配置

> 本章详细介绍 Nginx 的配置，包括反向代理、HTTPS、静态资源服务等。

---

## 📋 本章目标

完成本章后，你将理解：
- ✅ Nginx 在项目中的作用
- ✅ 如何配置 HTTPS 与自签名证书
- ✅ 反向代理的工作原理
- ✅ 静态资源服务与缓存配置

---

## 🌐 Nginx 在项目中的角色

```
                        Nginx 职责
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  1️⃣ 静态资源服务                                         │
│     /index.html, /ai-chat.html, /css/*, /js/*           │
│                                                          │
│  2️⃣ HTTPS 终端                                           │
│     接收 HTTPS 请求，处理 SSL/TLS                         │
│                                                          │
│  3️⃣ 反向代理                                             │
│     /api/* → 后端应用 (8090)                             │
│     /actuator/* → 健康检查端点                            │
│                                                          │
│  4️⃣ HTTP 重定向                                          │
│     http://localhost → https://localhost                 │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 📁 配置文件结构

```
.claude/docker-deploy/
├── nginx/
│   ├── nginx.conf           # 主配置文件
│   └── conf.d/
│       └── default.conf     # 虚拟主机配置
└── certs/
    ├── localhost.crt        # SSL 证书
    └── localhost.key        # SSL 私钥
```

---

## 📄 主配置文件详解

### nginx.conf 完整内容

```nginx
user  nginx;
worker_processes  auto;

error_log  /var/log/nginx/error.log notice;
pid        /var/run/nginx.pid;

events {
    worker_connections  1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                      '$status $body_bytes_sent "$http_referer" '
                      '"$http_user_agent" "$http_x_forwarded_for"';

    access_log  /var/log/nginx/access.log  main;

    sendfile        on;
    keepalive_timeout  65;

    # Gzip 压缩
    gzip  on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_types text/plain text/css text/xml text/javascript
               application/x-javascript application/xml
               application/javascript application/json;

    include /etc/nginx/conf.d/*.conf;
}
```

---

### nginx.conf 逐行详解

#### 1️⃣ 全局配置块

```nginx
user  nginx;
```
**运行用户**：指定 Nginx worker 进程以哪个用户身份运行。
- `nginx` 是容器内预创建的专用用户
- 出于安全考虑，不使用 `root` 用户

---

```nginx
worker_processes  auto;
```
**工作进程数**：处理请求的进程数量。
- `auto` = 自动检测 CPU 核心数，有几个核就启动几个 worker
- 例如：4 核 CPU 会启动 4 个 worker 进程

```
打个比方：

餐厅有 4 个厨师（4 核 CPU），就设置 4 个工作窗口（worker）。
每个厨师负责一个窗口，互不干扰，效率最高。
```

---

```nginx
error_log  /var/log/nginx/error.log notice;
```
**错误日志**：记录 Nginx 运行时的错误和警告。
- `/var/log/nginx/error.log` = 日志文件路径
- `notice` = 日志级别（从低到高：debug < info < notice < warn < error < crit）

> [!IMPORTANT]
> **这是容器内路径，不是宿主机路径！**
> 
> | 位置 | 路径 |
> |------|------|
> | **Nginx 容器内部** | `/var/log/nginx/error.log`<br>`/var/log/nginx/access.log` |
> | **你的 Mac（宿主机）** | 这些日志默认**不在宿主机上**（除非用 volumes 挂载出来） |
> 
> **查看日志的方法**：
> ```bash
> # 方式一：进入容器查看
> docker exec nginx cat /var/log/nginx/error.log
> 
> # 方式二：通过 docker logs（Nginx 默认输出到 stdout）
> docker logs -f nginx
> ```

---

```nginx
pid        /var/run/nginx.pid;
```
**进程 ID 文件**：存储 Nginx 主进程的 PID。
- 用于 `nginx -s reload` 等信号操作时找到进程

---

#### 2️⃣ events 块 - 连接处理配置

```nginx
events {
    worker_connections  1024;
}
```
**每个 worker 的最大连接数**：
- 单个 worker 进程同时能处理的连接数
- 总并发连接数 = `worker_processes` × `worker_connections`
- 例如：4 个 worker × 1024 = 最多 4096 个并发连接

> [!NOTE]
> 对于普通网站，1024 已经足够。高并发场景可以调高到 4096 或更高。

---

#### 3️⃣ http 块 - HTTP 服务配置

```nginx
http {
```
**HTTP 配置块开始**：所有 HTTP 相关的配置都在这里面。

---

```nginx
    include       /etc/nginx/mime.types;
```
**引入 MIME 类型映射**：告诉浏览器不同文件扩展名对应的 Content-Type。
- `.html` → `text/html`
- `.css` → `text/css`
- `.js` → `application/javascript`
- `.png` → `image/png`

---

```nginx
    default_type  application/octet-stream;
```
**默认 MIME 类型**：如果文件类型无法识别，就用这个。
- `application/octet-stream` = 二进制流，浏览器会提示下载

---

```nginx
    log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                      '$status $body_bytes_sent "$http_referer" '
                      '"$http_user_agent" "$http_x_forwarded_for"';
```
**日志格式定义**：定义一个名为 `main` 的日志格式。

| 变量 | 说明 | 示例 |
|------|------|------|
| `$remote_addr` | 客户端 IP | `192.168.1.100` |
| `$remote_user` | 认证用户名 | `-`（通常为空） |
| `$time_local` | 访问时间 | `11/Jan/2026:12:00:00 +0800` |
| `$request` | 请求行 | `GET /api/chat HTTP/1.1` |
| `$status` | HTTP 状态码 | `200` |
| `$body_bytes_sent` | 响应体大小 | `1234` |
| `$http_referer` | 来源页面 | `https://localhost/` |
| `$http_user_agent` | 浏览器信息 | `Mozilla/5.0...` |
| `$http_x_forwarded_for` | 代理链 IP | `10.0.0.1, 10.0.0.2` |

**日志示例**：
```
192.168.1.100 - - [11/Jan/2026:12:00:00 +0800] "GET /api/chat HTTP/1.1" 200 1234 "https://localhost/" "Mozilla/5.0..." "-"
```

---

```nginx
    access_log  /var/log/nginx/access.log  main;
```
**访问日志**：记录每个请求的信息。
- 路径：`/var/log/nginx/access.log`
- 格式：使用上面定义的 `main` 格式

---

```nginx
    sendfile        on;
```
**高效文件传输**：启用零拷贝技术，直接从磁盘发送文件到网络，不经过用户空间。
- 大幅提高静态文件传输效率
- 几乎所有场景都应该开启

---

```nginx
    keepalive_timeout  65;
```
**长连接超时**：HTTP Keep-Alive 连接保持时间（秒）。
- 客户端可以复用 TCP 连接发送多个请求
- 65 秒内没有新请求，连接关闭

```
打个比方：

你打电话给客服（建立连接），问完第一个问题后，
客服会等你 65 秒，看你还有没有其他问题。
如果 65 秒内你没再说话，就挂电话了。
```

---

#### 4️⃣ Gzip 压缩配置

```nginx
    gzip  on;
```
**启用 Gzip 压缩**：压缩响应内容，减少传输体积。

---

```nginx
    gzip_vary on;
```
**添加 Vary 头**：告诉缓存服务器根据 Accept-Encoding 区分缓存。

---

```nginx
    gzip_min_length 1024;
```
**最小压缩大小**：小于 1024 字节的响应不压缩（压缩太小的文件反而会变大）。

---

```nginx
    gzip_proxied any;
```
**代理请求压缩**：对所有代理请求的响应都启用压缩。

---

```nginx
    gzip_types text/plain text/css text/xml text/javascript
               application/x-javascript application/xml
               application/javascript application/json;
```
**压缩的文件类型**：只压缩这些 MIME 类型的响应。
- 文本类型（HTML、CSS、JS、JSON）压缩效果好
- 图片、视频已经是压缩格式，不需要再压缩

> [!TIP]
> Gzip 压缩可以将文本文件压缩到原来的 20%~30%，大幅节省带宽。

---

#### 5️⃣ 引入其他配置

```nginx
    include /etc/nginx/conf.d/*.conf;
```
**加载额外配置**：引入 `conf.d/` 目录下所有 `.conf` 文件。
- 这就是为什么 `default.conf` 会被加载
- 模块化配置，便于管理

```
打个比方：

nginx.conf 是"总经理"，只管公司整体战略。
conf.d/*.conf 是各个"部门经理"，负责具体业务（虚拟主机配置）。
```

---

```nginx
}
```
**http 块结束**。

---

### 关键配置速查表

| 配置 | 说明 | 推荐值 |
|------|------|--------|
| `worker_processes` | 工作进程数 | `auto`（自动检测 CPU 核数） |
| `worker_connections` | 每 worker 最大连接数 | `1024`（普通场景） |
| `sendfile` | 高效文件传输 | `on` |
| `keepalive_timeout` | 长连接超时秒数 | `65` |
| `gzip` | 压缩传输 | `on` |
| `gzip_min_length` | 最小压缩大小 | `1024`（小于此不压缩） |

---

## 🔒 HTTPS 配置详解

### default.conf

```nginx
# HTTP 服务 - 重定向到 HTTPS
server {
    listen       80;
    server_name  localhost;

    # 健康检查路径（不重定向）
    location /health {
        return 200 'OK';
        add_header Content-Type text/plain;
    }

    # 其他请求重定向到 HTTPS
    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS 服务
server {
    listen       443 ssl;
    server_name  localhost;

    # SSL 证书配置
    ssl_certificate     /etc/nginx/certs/localhost.crt;
    ssl_certificate_key /etc/nginx/certs/localhost.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # ... 其他配置
}
```

**SSL 配置说明**：

| 配置 | 说明 |
|------|------|
| `ssl_protocols TLSv1.2 TLSv1.3` | 只允许安全的 TLS 版本 |
| `ssl_ciphers HIGH:!aNULL:!MD5` | 使用高强度加密套件 |
| `ssl_prefer_server_ciphers on` | 优先使用服务器配置的加密套件 |

---

## 🔑 自签名证书生成

项目提供了证书生成脚本 `scripts/gen-ssl-cert.sh`：

```bash
#!/bin/bash
# 生成自签名 SSL 证书

CERT_DIR=".claude/docker-deploy/certs"
mkdir -p "$CERT_DIR"

# 检查是否已存在
if [[ -f "$CERT_DIR/localhost.crt" ]]; then
    echo "证书已存在，跳过生成"
    exit 0
fi

# 生成证书
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "$CERT_DIR/localhost.key" \
    -out "$CERT_DIR/localhost.crt" \
    -subj "/CN=localhost/O=Development/C=CN" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "✅ 证书生成完成"
```

**手动生成**：

```bash
cd /Users/xiexu/xiaofu/ai-rag-knowledge-study/.claude/docker-deploy

mkdir -p certs

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout certs/localhost.key \
    -out certs/localhost.crt \
    -subj "/CN=localhost/O=Development/C=CN" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
```

**参数说明**：

| 参数 | 说明 |
|------|------|
| `-x509` | 生成自签名证书 |
| `-nodes` | 不加密私钥 |
| `-days 365` | 有效期 365 天 |
| `-newkey rsa:2048` | 生成 2048 位 RSA 密钥 |
| `subjectAltName` | 添加 SAN 扩展，Chrome 要求 |

> ⚠️ **注意**：自签名证书仅用于本地开发，浏览器会提示"不安全"，点击"高级"→"继续访问"即可。

---

## 🔀 反向代理配置

### API 代理

```nginx
location /api/ {
    proxy_pass http://ai-rag-knowledge-app:8090/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # SSE 支持（流式响应）
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 300s;
    proxy_connect_timeout 75s;
}
```

**配置说明**：

| 配置 | 说明 |
|------|------|
| `proxy_pass` | 代理目标地址，使用容器名称 |
| `proxy_set_header Host` | 传递原始 Host 头 |
| `X-Real-IP` | 传递客户端真实 IP |
| `X-Forwarded-For` | 完整的代理链 IP 列表 |
| `X-Forwarded-Proto` | 原始请求协议（http/https） |
| `proxy_buffering off` | **关键**：禁用缓冲，支持 SSE 流式响应 |
| `proxy_read_timeout 300s` | AI 生成可能需要较长时间 |

### SSE (Server-Sent Events) 支持

本项目使用 SSE 实现 AI 流式响应，必须禁用 Nginx 缓冲：

```nginx
# SSE 必需配置
proxy_buffering off;      # 禁用响应缓冲
proxy_cache off;          # 禁用缓存
chunked_transfer_encoding on;  # 分块传输（通常默认开启）
```

### 健康检查代理

```nginx
location /actuator/ {
    proxy_pass http://ai-rag-knowledge-app:8090/actuator/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

---

## 📁 静态资源服务

### 基础配置

```nginx
location / {
    root   /usr/share/nginx/html;
    index  ai-chat.html index.html index.htm;
    try_files $uri $uri/ /ai-chat.html;
}
```

**配置说明**：

| 配置 | 说明 |
|------|------|
| `root` | 静态文件根目录 |
| `index` | 默认首页文件 |
| `try_files` | SPA 单页应用路由支持 |

### 缓存配置

```nginx
location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
    root   /usr/share/nginx/html;
    expires 7d;
    add_header Cache-Control "public, immutable";
}
```

**配置说明**：

| 配置 | 说明 |
|------|------|
| `~*` | 正则匹配（不区分大小写） |
| `expires 7d` | 浏览器缓存 7 天 |
| `Cache-Control "public, immutable"` | 资源不变，可缓存 |

---

## 🔧 常见配置修改

### 修改默认首页

```nginx
# 将 ai-chat.html 改为其他页面
location / {
    root   /usr/share/nginx/html;
    index  knowledge.html;  # 修改这里
    try_files $uri $uri/ /knowledge.html;  # 同步修改
}
```

### 添加跨域支持（如果需要）

```nginx
location /api/ {
    # ... 原有配置 ...

    # 添加 CORS 头
    add_header Access-Control-Allow-Origin *;
    add_header Access-Control-Allow-Methods "GET, POST, OPTIONS";
    add_header Access-Control-Allow-Headers "Content-Type, Authorization";

    # 处理 OPTIONS 预检请求
    if ($request_method = 'OPTIONS') {
        return 204;
    }
}
```

### 调整上传文件大小限制

```nginx
http {
    # 在 http 块中添加
    client_max_body_size 100M;  # 允许上传 100MB 文件
}
```

或在特定 location：

```nginx
location /api/upload {
    client_max_body_size 100M;
    proxy_pass http://ai-rag-knowledge-app:8090/api/upload;
}
```

---

## 🐛 调试技巧

### 查看 Nginx 日志

```bash
# 容器内日志
docker exec nginx cat /var/log/nginx/access.log
docker exec nginx cat /var/log/nginx/error.log

# 持续跟踪
docker logs -f nginx
```

### 测试配置语法

```bash
# 在容器内测试配置
docker exec nginx nginx -t

# 预期输出：
# nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
# nginx: configuration file /etc/nginx/nginx.conf test is successful
```

### 重新加载配置（无需重启）

```bash
docker exec nginx nginx -s reload
```

---

## ✅ 本章检查清单

在进入下一章之前，请确保理解：

- [ ] Nginx 主配置和虚拟主机配置的区别
- [ ] HTTPS 证书如何配置
- [ ] 反向代理的 `proxy_pass` 如何工作
- [ ] 为什么需要 `proxy_buffering off`

---

## 📚 文档导航

| 上一篇 | 下一篇 |
|--------|--------|
| [05-应用部署](./05-应用部署.md) | [07-验证与排查](./07-验证与排查.md) |

[返回目录](./README.md)
