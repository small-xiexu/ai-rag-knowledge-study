#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_SRC="$PROJECT_ROOT/xfg-dev-tech-app/src/main/resources/static"
FRONTEND_DST="$DEPLOY_DIR/frontend"
LOG_DIR="/Users/xiexu/logs"

echo "🚀 AI-RAG-Knowledge Docker 部署脚本"
echo "=================================="

command -v docker >/dev/null 2>&1 || { echo "❌ 未找到 docker 命令"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "❌ 未找到 mvn，请安装 Maven"; exit 1; }

echo "📋 检查 Docker 运行状态..."
docker info >/dev/null 2>&1 || { echo "❌ Docker 未运行"; exit 1; }

if [[ -f "$DEPLOY_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$DEPLOY_DIR/.env"
  set +a
fi

echo "📁 准备目录..."
mkdir -p "$FRONTEND_DST" "$LOG_DIR"
chmod 755 "$LOG_DIR" || true

echo "🔐 检查 SSL 证书..."
"$SCRIPT_DIR/gen-ssl-cert.sh"

echo "📦 同步前端静态资源..."
rsync -av --delete "$FRONTEND_SRC"/ "$FRONTEND_DST"/

echo "🔨 构建 Spring Boot 应用 (跳过测试)..."
cd "$PROJECT_ROOT"
mvn clean package -DskipTests

echo "🐳 构建 Docker 镜像..."
docker build -t "ai-rag-knowledge-app:${APP_VERSION:-1.0}" \
  -f "$DEPLOY_DIR/Dockerfile" \
  "$PROJECT_ROOT/xfg-dev-tech-app/"

echo "🚀 启动服务..."
cd "$DEPLOY_DIR"
docker-compose up -d

echo "⏳ 等待服务启动..."
sleep 10

echo "✅ 健康检查..."
if curl -k -s https://localhost/actuator/health | grep -q "UP"; then
  echo "🎉 部署成功！"
  echo "  - 前端: https://localhost"
  echo "  - 后端: http://localhost:8090"
else
  echo "⚠️  健康检查未通过，请检查日志: docker-compose logs"
fi
