#!/usr/bin/env bash
set -euo pipefail

echo "🗄️  初始化数据库..."

command -v docker >/dev/null 2>&1 || { echo "❌ 未找到 docker 命令"; exit 1; }

if ! docker ps --format '{{.Names}}' | grep -q '^vector_db$'; then
  echo "❌ vector_db 容器未运行，请先启动中间件环境"
  exit 1
fi

echo "📦 检查数据库是否存在..."
EXISTS=$(docker exec vector_db psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='ai-rag-knowledge';" || true)
if [[ "$EXISTS" != "1" ]]; then
  docker exec vector_db psql -U postgres -c "CREATE DATABASE \"ai-rag-knowledge\";"
else
  echo "ℹ️  数据库已存在，跳过创建"
fi

echo "🔧 启用 pgvector 扩展..."
docker exec vector_db psql -U postgres -d ai-rag-knowledge -c "CREATE EXTENSION IF NOT EXISTS vector;"

echo "✅ 数据库初始化完成"
