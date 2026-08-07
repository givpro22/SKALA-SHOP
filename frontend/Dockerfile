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
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -qO- http://localhost/ > /dev/null 2>&1 || exit 1
