#!/usr/bin/env bash
# ============================================================================
# RecoverAI — full stack bring-up for a fresh sandbox/VM (idempotent).
#
#   scripts/dev-up.sh
#
# Rebuilds everything that is NOT persisted (Maven artifacts, node_modules,
# .next, Python venv, PostgreSQL data) and leaves the repo ready for:
#   make dev   (or the start_process instructions in README)
#
# Then start the three services:
#   apps/api       java -jar target/recoverai-api-0.1.0.jar ...
#   apps/ai-service .venv/bin/uvicorn app.main:app --port 8100
#   apps/web       npm run start -- -p 3000
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
export PATH="/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH"

echo "── [1/6] System toolchain (JDK 21, Maven, PostgreSQL)"
./scripts/bootstrap.sh >/dev/null

echo "── [2/6] Backend: resolve deps + package jar"
cd "$ROOT/apps/api"
mvn -q -B -DskipTests dependency:go-offline
mvn -q -B package -DskipTests

echo "── [3/6] AI service: Python venv + deps"
cd "$ROOT/apps/ai-service"
python3 -m venv .venv
.venv/bin/pip install -q -r requirements.txt

echo "── [4/6] Web: npm deps + production build"
cd "$ROOT/apps/web"
npm install --no-audit --no-fund >/dev/null 2>&1
npm run build >/dev/null 2>&1

echo "── [5/6] Reset + seed database"
sudo -u postgres psql -c "DROP DATABASE IF EXISTS recoverai;" >/dev/null 2>&1 || true
sudo -u postgres psql -c "CREATE DATABASE recoverai OWNER recoverai;"
cd "$ROOT/apps/api"
timeout 300 java -Dloader.main=com.recoverai.tools.SeederMain \
  -cp target/recoverai-api-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/recoverai \
  --spring.datasource.username=recoverai \
  --spring.datasource.password=recoverai_dev \
  --server.port=8089 2>&1 | grep -E "SEED_DATA_READY|SEED_SKIPPED|ERROR" | head -3

echo "── [6/6] Smoke checks"
curl -s -o /dev/null -w "actuator health (after start): %{http_code}\n" --max-time 3 http://localhost:8080/actuator/health || true
echo "Bring-up complete. Start services, then:"
echo "  API  : cd apps/api && java -jar target/recoverai-api-0.1.0.jar (with env flags)"
echo "  AI   : cd apps/ai-service && .venv/bin/uvicorn app.main:app --port 8100"
echo "  Web  : cd apps/web && npm run start -- -p 3000"
