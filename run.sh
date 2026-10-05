#!/usr/bin/env bash
# =============================================================================
# 企业会议纪要保密级别与定向分发系统 —— 一条本地命令启动
#
# 用法：
#   ./run.sh            构建前端+后端并启动（首次会下载依赖，耗时较长）
#   ./run.sh --clean    清空历史数据后重新启动（重新播种演示账号）
#
# 启动后访问： http://localhost:8080
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$ROOT/frontend"
BACKEND="$ROOT/backend"
PORT="${PORT:-8080}"

# 1) 若系统 PATH 没有 Java/Maven，尝试加载用户空间安装的工具链
if ! command -v java >/dev/null 2>&1 || ! command -v mvn >/dev/null 2>&1; then
  if [ -f "$HOME/tools/env.sh" ]; then
    # shellcheck disable=SC1090
    source "$HOME/tools/env.sh"
  fi
fi
command -v java >/dev/null 2>&1 || { echo "未找到 java，请安装 JDK 21+"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "未找到 mvn，请安装 Maven 3.9+"; exit 1; }
command -v npm  >/dev/null 2>&1 || { echo "未找到 npm，请安装 Node.js 18+"; exit 1; }

if [ "${1:-}" = "--clean" ]; then
  echo ">> 清空历史数据（data/）"
  rm -rf "$BACKEND/data"
fi

# 2) 构建 Svelte 前端（产物输出到后端静态资源目录，最终单端口由 Quarkus 托管）
echo ">> 构建前端（Svelte + Vite）"
cd "$FRONTEND"
if [ ! -d node_modules ]; then
  npm install
fi
npm run build

# 3) 构建后端（Quarkus fast jar；已通过的安全测试可用 mvn test 单独运行）
echo ">> 构建后端（Quarkus 打包）"
cd "$BACKEND"
mkdir -p data
mvn -B -q package -DskipTests

JAR="$BACKEND/target/quarkus-app/quarkus-run.jar"
[ -f "$JAR" ] || { echo "构建产物缺失：$JAR"; exit 1; }

# 4) 启动
echo ">> 启动服务： http://localhost:$PORT"
echo "   演示账号：chenjing(记录员) / lifang(负责人) / wangwei,sunli,zhaolei(员工) / admin(管理员)"
cd "$BACKEND"
exec java -Dfile.encoding=UTF-8 -jar "$JAR"
