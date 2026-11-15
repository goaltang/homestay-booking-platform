# ============================================
# homestay-front 生产镜像（node build → nginx）
# 构建：docker build -t homestay-front .
# 同源部署：镜像内清空 API 地址，浏览器请求 /api 由 Nginx 反代到 backend
# 独立域名部署：--build-arg VITE_API_BASE_URL=https://api.example.com
# ============================================

FROM node:20-alpine AS build
ARG VITE_API_BASE_URL=""
# 公开演示站的一键体验账号（留空则不显示体验入口）
ARG VITE_DEMO_GUEST_USERNAME=""
ARG VITE_DEMO_HOST_USERNAME=""
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund
COPY . .
# .env.production 优先级高于仓库里的 .env，避免把 localhost:8081 打进产物
RUN printf 'VITE_API_BASE_URL=%s\nVITE_DEMO_GUEST_USERNAME=%s\nVITE_DEMO_HOST_USERNAME=%s\n' \
      "$VITE_API_BASE_URL" "$VITE_DEMO_GUEST_USERNAME" "$VITE_DEMO_HOST_USERNAME" > .env.production
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
