# syntax=docker/dockerfile:1

# ---- build stage ----------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# Vite의 VITE_* 변수는 빌드 시점에 번들에 인라인된다. 런타임 환경변수로는 바꿀 수 없으므로
# 반드시 ARG로 받아 build 전에 넣어야 한다. 이걸 놓치면 컨테이너에서만 API 호출이
# 기본값(localhost:8080)으로 나가고, 화면은 뜨는데 데이터만 안 나와 원인을 찾기 어렵다.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---- runtime stage --------------------------------------------------------
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
# localhost 가 아니라 127.0.0.1 을 쓴다. 컨테이너 안에서 localhost 는 [::1](IPv6)로 먼저 풀리는데
# nginx 기본 설정은 0.0.0.0:80(IPv4)만 듣기 때문에 wget 이 connection refused 로 실패한다.
# 사이트는 정상인데 컨테이너만 unhealthy 로 남는 상태가 되어 원인을 찾기 어렵다(실제로 겪었다).
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ > /dev/null 2>&1 || exit 1
