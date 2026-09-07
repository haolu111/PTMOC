#!/usr/bin/env bash
# Start PTMOC JDK8 demo HTTP server (real crypto, same API as Spring Boot).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# 自动加载本地环境变量（一次配置，以后不用每次 export）
# 优先读取项目根目录 .env；已存在的环境变量不会被覆盖
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
  echo "Loaded env from $ROOT/.env"
fi

JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

GSON_JAR="${GSON_JAR:-$HOME/.m2/repository/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar}"
if [[ ! -f "$GSON_JAR" ]]; then
  GSON_JAR="$HOME/.m2/repository/com/google/code/gson/gson/2.8.5/gson-2.8.5.jar"
fi
if [[ ! -f "$GSON_JAR" ]]; then
  echo "ERROR: gson jar not found. Set GSON_JAR=/path/to/gson.jar"
  exit 1
fi

mkdir -p bin
echo "Compiling PTMOC core + demo server..."
javac -encoding UTF-8 -cp "src:$GSON_JAR" -d bin \
  src/com/ptmoc/model/Entity.java \
  src/com/ptmoc/model/KeyPair.java \
  src/com/ptmoc/model/Polynomial.java \
  src/com/ptmoc/model/PublicParameters.java \
  src/com/ptmoc/util/CryptoUtil.java \
  src/com/ptmoc/util/MathUtil.java \
  src/com/ptmoc/core/BaseModule.java \
  src/com/ptmoc/core/CryptoModule.java \
  src/com/ptmoc/core/EvalModule.java \
  src/com/ptmoc/server/*.java

PORT="${1:-8080}"
if [[ -z "${AMAP_WEB_KEY:-}" ]]; then
  echo "WARN: AMAP_WEB_KEY 未设置。请在项目根目录创建 .env（可复制 .env.example）"
else
  echo "AMAP_WEB_KEY: configured"
fi
echo "Starting DemoHttpServer on port $PORT ..."
exec java -cp "bin:$GSON_JAR" com.ptmoc.server.DemoHttpServer "$PORT"
