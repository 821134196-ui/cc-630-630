#!/usr/bin/env bash
# ============================================================
# 企业会议纪要保密级别与定向分发系统 —— 一条本地命令启动
# 用法：./run.sh        启动（首次自动构建前端，随后以 Quarkus 开发模式运行）
# 启动后访问： http://localhost:8080
# 数据位置：  ./data（SQLite 数据库 minutes.db 与 attachments/ 附件目录）
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

# 优先使用系统 Java/Maven；若无则使用用户目录下的 JDK17 + Maven
if ! command -v java >/dev/null 2>&1 && [ -d "$HOME/opt/jdk-17.0.20.1+1" ]; then
  export JAVA_HOME="$HOME/opt/jdk-17.0.20.1+1"
  export PATH="$JAVA_HOME/bin:$HOME/opt/apache-maven-3.9.9/bin:$PATH"
fi

echo "==> [1/3] 检查 Java / Maven"
java -version
mvn -v | head -1

echo "==> [2/3] 构建 Svelte 前端（输出到 Quarkus 静态资源目录）"
if [ ! -d web/node_modules ]; then
  (cd web && npm install --no-audit --no-fund)
fi
(cd web && npm run build)

echo "==> [3/3] 启动 Quarkus（SQLite 与附件保存在 ./data，热重载已开启）"
mkdir -p data
exec mvn quarkus:dev \
  -Dquarkus.analytics.disabled=true \
  -Dquarkus.http.host=0.0.0.0
