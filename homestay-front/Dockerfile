# ============================================
# homestay-front 生产镜像（node build → nginx）
# ============================================

FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
