# ============================================================================
# RecoverAI web dashboard — multi-stage production container (standalone build)
# ============================================================================
FROM node:20-alpine AS deps
WORKDIR /app
COPY apps/web/package.json apps/web/package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY apps/web/ .
ENV NEXT_TELEMETRY_DISABLED=1 API_PROXY_TARGET=http://api:8080
RUN npm run build

FROM node:20-alpine AS runtime
RUN addgroup -S recoverai && adduser -S -G recoverai recoverai
USER recoverai
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME="0.0.0.0" API_PROXY_TARGET="http://api:8080"
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=3s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:3000/login || exit 1
ENTRYPOINT ["node", "server.js"]
