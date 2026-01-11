#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CERT_DIR="$DEPLOY_DIR/certs"
CRT_PATH="$CERT_DIR/localhost.crt"
KEY_PATH="$CERT_DIR/localhost.key"

echo "🔐 生成 SSL 证书..."

command -v openssl >/dev/null 2>&1 || { echo "❌ 未找到 openssl 命令"; exit 1; }
mkdir -p "$CERT_DIR"

if [[ -f "$CRT_PATH" && -f "$KEY_PATH" ]]; then
  echo "ℹ️  已存在证书与私钥，跳过生成"
  exit 0
fi

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$KEY_PATH" \
  -out "$CRT_PATH" \
  -subj "/CN=localhost/O=Development/C=CN" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "✅ 证书生成完成："
openssl x509 -in "$CRT_PATH" -noout -text | head -n 8
