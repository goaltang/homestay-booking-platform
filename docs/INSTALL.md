# 安装教程 / Installation Guide

> 本教程面向人类开发者和 AI Agent。AI Agent 请优先阅读 [AI Agent 安装指引](#ai-agent-安装指引) 一节。

**[English version below](#english-version)**

---

## 中文

### 概览

本项目由 3 个可独立运行的子项目组成：

| 子项目 | 技术栈 | 默认端口 | 说明 |
|---|---|---|---|
| `homestay-backend` | Java 17 + Spring Boot 3.0.2 + Maven | 8081 | 后端 API 服务 |
| `homestay-front` | Vue 3 + TypeScript + Vite 5 | 5173 | 用户端 + 房东端 |
| `homestay-admin` | Vue 3 + TypeScript + Vite 6 | 5174 | 管理员端 |

依赖的基础设施：

| 服务 | 版本 | 必需 | 说明 |
|---|---|---|---|
| MySQL | 8.0+ | ✅ | 主数据库，Flyway 自动建表 |
| Redis | 6.0+ | ✅ | 缓存 + 分布式锁 |
| Elasticsearch + IK | 8.5.0（当前镜像） | 按需 | 默认关闭，搜索走 JPA；开启后需带 IK 插件的 ES 在线 |
| RabbitMQ | 3.13（management） | 按需 | 演示订单超时、批量发券与通知 MQ 场景 |

### 前置条件检查

在安装之前，确认以下工具已安装：

```bash
# Java 17+
java -version
# 预期输出包含: openjdk version "17" 或更高

# Maven 3.6+
mvn -version
# 预期输出包含: Apache Maven 3.6+

# Node.js 20（与 CI 一致）
node -v
# 预期输出: v20.x

# npm 9+
npm -v
# 预期输出: 9.x 或更高

# MySQL 8.0+
mysql --version
# 预期输出包含: Ver 8.0

# Redis
redis-server --version
# 预期输出包含: v=6.0 或更高

# Docker（ES / RabbitMQ 使用）
docker --version
docker compose version
```

### 步骤 1：克隆项目

```bash
git clone https://github.com/goaltang/homestay-booking-platform.git homestay3
cd homestay3
```

### 步骤 2：创建 MySQL 数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS homestay_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

验证：

```bash
mysql -u root -p -e "SHOW DATABASES LIKE 'homestay_db';"
# 预期输出包含: homestay_db
```

### 步骤 3：启动 Redis

```bash
# 如果 Redis 未运行
redis-server --daemonize yes

# 验证
redis-cli ping
# 预期输出: PONG
```

### 步骤 4：按需启动 Elasticsearch 与消息队列

```bash
# 在项目根目录；已有 .env 时保留并检查配置
cp -n .env.example .env
# 按实际环境填写 .env，供 Compose 解析服务配置

# 需要 ES 搜索时，先构建带 IK 分词插件的镜像（CI / Testcontainers 同样使用此镜像）
docker build -t homestay-es-ik:test -f homestay-backend/src/test/resources/testcontainers/Dockerfile.es-ik homestay-backend
docker compose up -d elasticsearch

# 等待 ES 就绪后验证版本
curl -fs http://localhost:9200
# 预期返回版本 8.5.0 的 JSON

# 演示 MQ 场景时启动 RabbitMQ
docker compose up -d rabbitmq
```

> 日常开发可跳过 ES。共享默认配置 `application.yml` 将 `elasticsearch.enabled` 设为 `false`，并联动关闭 ES Repository 与 ES 健康检查，搜索走 JPA。启用 ES 时，先启动带 IK 插件的服务，再用 `ELASTICSEARCH_ENABLED=true` 启动后端。RabbitMQ 管理台为 `http://localhost:15672`，账号以 `.env` 配置为准。

### 步骤 5：配置后端

```bash
cd homestay-backend

# 复制配置模板
cp src/main/resources/application.example.properties src/main/resources/application-local.properties
```

编辑 `src/main/resources/application-local.properties`，至少修改以下配置：

```properties
# 服务端口（模板为 8080，本地前端代理使用 8081）
server.port=8081

# MySQL（必填）
spring.datasource.username=root
spring.datasource.password=你的MySQL密码

# Redis（如果设置了密码）
spring.data.redis.password=你的Redis密码

# JWT 密钥（必填，使用随机长字符串）
jwt.secret=替换为一个至少50字符的随机字符串

# Elasticsearch（可选，默认关闭；需要 ES 时设置 ELASTICSEARCH_ENABLED=true）
spring.elasticsearch.uris=http://localhost:9200
```

本地配置不提交到 Git。启动时必须显式激活 `local` profile，才能读取 `application-local.properties`。支付宝、邮件、地图与 AI 客服配置按需要填写；AI 客服默认关闭。

### 步骤 6：启动后端

```bash
# 接上一步，在 homestay-backend 目录运行
mvn clean compile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

验证：

```bash
# 等待约 30 秒后
curl -s "http://localhost:8081/api/homestays?page=0&size=1"
# 预期: 返回 JSON 响应（可能为空列表）
```

后端启动时 Flyway 自动执行仓库内的数据库迁移，无需手动建表。

### 步骤 7：启动用户端（房客 + 房东）

新开终端，从项目根目录执行：

```bash
cd homestay-front

# 可选：配置高德地图 Key
cp .env.example .env.local
# 编辑 .env.local，填入 VITE_AMAP_API_KEY 等

npm install
npm run dev
```

验证：浏览器打开 `http://localhost:5173`，应看到首页。

> Vite 代理配置：`/api` → `http://127.0.0.1:8081`，`/uploads` → `http://127.0.0.1:8081`

### 步骤 8：启动管理员端

再开一个终端，从项目根目录执行：

```bash
cd homestay-admin
npm install
npm run dev
```

验证：浏览器打开 `http://localhost:5174`，应看到登录页。

> Vite 代理配置：`/api` → `http://localhost:8081`

### 步骤 9：管理员登录

后端首次启动时会自动创建默认管理员 **admin / admin888**（`ROLE_ADMIN`，仅当 `admin` 用户不存在时创建）。

直接用默认账号登录管理端 (`localhost:5174`)。默认账号仅用于开发演示，对外部署前应修改。

### 全套容器与可选组件

以下命令在项目根目录执行。先填写 `.env`；首次启用 ES 时还需按步骤 4 构建 IK 镜像。

```bash
# 默认启动 MySQL、Redis、RabbitMQ、后端和两个前端；不启动 ES 与监控
docker compose up -d --build

# 需要 ES 时，先启动并检查健康状态
docker compose up -d elasticsearch
curl -fs http://localhost:9200
# 确认 ES 已就绪，再启用后端 ES 功能
ELASTICSEARCH_ENABLED=true docker compose --profile search up -d

# 按需启动监控
docker compose --profile monitoring up -d prometheus grafana
# 用完停止监控，保留容器
docker compose stop prometheus grafana

# 关闭 ES：先关闭后端开关并重新创建后端，确认启动成功后再停止 ES
ELASTICSEARCH_ENABLED=false docker compose up -d backend
docker compose stop elasticsearch
```

`search` 与 `monitoring` profile 只控制容器是否启动；`ELASTICSEARCH_ENABLED` 控制后端 ES 功能。Compose 不再等待 ES 健康，启用前需自行确认 ES 已就绪。监控地址为 Prometheus `http://localhost:9090` 和 Grafana `http://localhost:3000`。已运行的可选容器不会因省略 profile 而自动停止。当前 Prometheus 抓取 `host.docker.internal:8081`，依赖 Docker Desktop 的宿主访问能力；本地后端需让容器能访问该端口。

本地 Maven 后端需停止后重新启动才能切换开关。在 `homestay-backend` 目录执行 `ELASTICSEARCH_ENABLED=true mvn spring-boot:run -Dspring-boot.run.profiles=local` 启用 ES；将值改为 `false` 则关闭。若已使用本地默认配置，可在项目根目录执行 `ELASTICSEARCH_ENABLED=true npm run dev:backend`。

关闭 ES 期间的房源变化不会同步到索引。重新开启后，管理员应调用 `POST /api/admin/search/index/rebuild` 重建索引，再使用 ES 搜索。

### 常见问题

| 问题 | 原因 | 解决方案 |
|---|---|---|
| 后端启动报 `Communications link failure` | MySQL 未启动或密码错误 | 检查 MySQL 状态和 `spring.datasource.*` 配置 |
| 后端启动报 `Unable to connect to Redis` | Redis 未启动 | `redis-server --daemonize yes` |
| 后端启动报 ES 连接失败 | 已启用 ES，但服务未启动或地址不对 | 启动带 IK 插件的 ES 并检查 `spring.elasticsearch.uris`；不需要 ES 时移除本地配置中的显式启用值，并设 `ELASTICSEARCH_ENABLED=false` |
| 前端 `/api` 请求 404 | 后端未启动或端口不对 | 确认后端在 8081 端口运行 |
| 前端端口被占用 | 5173/5174 已被使用 | Vite 会自动分配新端口，查看终端输出 |
| `mvn` 下载依赖超时 | 网络问题 | 配置 Maven 镜像（如阿里云） |
| `npm install` 超时 | 网络问题 | 配置 npm 镜像：`npm config set registry https://registry.npmmirror.com` |

---

## AI Agent 安装指引

本节为 AI Agent（如 Claude Code、Cursor、Copilot Agent、Hermes 等）提供结构化的安装指令。
Agent 应按顺序执行以下步骤，每步执行后验证输出。

### Agent 安装前提

- 工作目录：项目根目录 `homestay3/`
- 操作系统：Linux / macOS / WSL
- 需要 root 或 sudo 权限安装缺失依赖
- 所有命令在 bash/sh 中执行

### Agent 安装流程

```text
STEP 1: 检查依赖
  RUN: java -version 2>&1 | grep -q '17\|21' && echo OK || echo MISSING
  RUN: mvn -version 2>&1 | grep -q 'Apache Maven' && echo OK || echo MISSING
  RUN: node -v 2>&1 | grep -qE 'v(2[0-9]|[3-9][0-9])' && echo OK || echo MISSING
  RUN: mysql --version 2>&1 | grep -q '8\.' && echo OK || echo MISSING
  RUN: redis-server --version 2>&1 | grep -q 'v=' && echo OK || echo MISSING
  IF any MISSING → 安装对应依赖后重新检查

STEP 2: 创建数据库
  RUN: mysql -u root -p"${MYSQL_ROOT_PASSWORD:-root}" -e "CREATE DATABASE IF NOT EXISTS homestay_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  VERIFY: mysql -u root -p"${MYSQL_ROOT_PASSWORD:-root}" -e "SHOW DATABASES LIKE 'homestay_db';" | grep homestay_db

STEP 3: 确保 Redis 运行
  RUN: redis-cli ping 2>/dev/null || (redis-server --daemonize yes && sleep 2 && redis-cli ping)
  VERIFY: redis-cli ping → PONG

STEP 4: Elasticsearch 与 RabbitMQ（均按需）
  RUN: cp -n .env.example .env
  EDIT: .env → 实际本地环境配置；已有文件不要覆盖
  IF 需要 ES 搜索:
    RUN: docker build -t homestay-es-ik:test -f homestay-backend/src/test/resources/testcontainers/Dockerfile.es-ik homestay-backend
    RUN: docker compose up -d elasticsearch
    VERIFY: curl -fs http://localhost:9200 → 版本 8.5.0 的 JSON
  IF 演示 MQ 场景:
    RUN: docker compose up -d rabbitmq

STEP 5: 配置后端
  RUN: cd homestay-backend && cp -n src/main/resources/application.example.properties src/main/resources/application-local.properties
  EDIT: application-local.properties
    - spring.datasource.username → 实际 MySQL 用户名
    - spring.datasource.password → 实际 MySQL 密码
    - spring.data.redis.password → 实际 Redis 密码（无密码留空）
    - jwt.secret → 随机长字符串（≥50 字符）
    - server.port → 8081（模板为 8080）
    - spring.elasticsearch.uris → http://localhost:9200
  NOTE: ES 默认关闭，无需单独配置 Repository 开关；需要 ES 时在后端启动命令前加 ELASTICSEARCH_ENABLED=true
  NOTE: 本地配置必须通过 local profile 显式加载；不得依赖未提交的 application.properties

STEP 6: 启动后端
  RUN: 在 homestay-backend 目录执行 mvn spring-boot:run -Dspring-boot.run.profiles=local
  WAIT: 直到日志出现 "Started HomestayBackendApplication"（约 30-60s）
  VERIFY: curl -s "http://localhost:8081/api/homestays?page=0&size=1" | head -c 200
  EXPECT: JSON 响应（{"success":true,...} 或类似）

STEP 7: 启动用户端
  NOTE: 新开终端，从项目根目录执行
  RUN: cd homestay-front && npm install --prefer-offline 2>&1 | tail -1 && npm run dev &
  WAIT: 直到日志出现 "Local: http://localhost:5173"
  VERIFY: curl -s http://localhost:5173 | grep -q '<title>' && echo OK

STEP 8: 启动管理员端
  NOTE: 新开终端，从项目根目录执行
  RUN: cd homestay-admin && npm install --prefer-offline 2>&1 | tail -1 && npm run dev &
  WAIT: 直到日志出现 "Local: http://localhost:5174"
  VERIFY: curl -s http://localhost:5174 | grep -q '<title>' && echo OK

STEP 9: 管理员登录
  NOTE: 后端在 admin 不存在时创建开发账号 admin / admin888，角色为 ROLE_ADMIN
  VERIFY: 用默认开发账号登录 http://localhost:5174
```

### Agent 注意事项

1. **数据库迁移自动执行**：后端启动时 Flyway 运行仓库内迁移，不需要手动建表或导入 SQL。
2. **ES 按需开启**：默认关闭且不初始化 ES Repository，搜索走 JPA；需要 ES 时先启动带 IK 插件的容器，再设 `ELASTICSEARCH_ENABLED=true` 启动后端。
3. **前端代理**：两个前端项目的 Vite 配置已内置 `/api` 代理到 `localhost:8081`，不需要额外配置 CORS。
4. **端口冲突**：如果 8081/5173/5174 被占用，修改对应配置文件中的端口。
5. **显式加载本地配置**：填写真实环境参数到 `application-local.properties`，使用 `-Dspring-boot.run.profiles=local` 启动；不要依赖未提交的默认开发配置。
6. **npm 镜像**：在中国大陆环境下，先执行 `npm config set registry https://registry.npmmirror.com`。
7. **Maven 镜像**：在中国大陆环境下，在 `~/.m2/settings.xml` 中配置阿里云镜像。

### Agent 验证清单

安装完成后，逐项确认：

- [ ] `curl -s "http://localhost:8081/api/homestays?page=0&size=1"` 返回 JSON
- [ ] `curl -s http://localhost:5173` 返回 HTML
- [ ] `curl -s http://localhost:5174` 返回 HTML
- [ ] 用户端注册 → 登录 → 浏览房源 流程正常
- [ ] 管理员端登录正常（默认开发账号 `admin / admin888`）

---

## English Version

### Overview

This project consists of 3 independently runnable sub-projects:

| Sub-project | Stack | Default Port | Description |
|---|---|---|---|
| `homestay-backend` | Java 17 + Spring Boot 3.0.2 + Maven | 8081 | Backend API server |
| `homestay-front` | Vue 3 + TypeScript + Vite 5 | 5173 | Guest + Host app |
| `homestay-admin` | Vue 3 + TypeScript + Vite 6 | 5174 | Admin app |

Required infrastructure:

| Service | Version | Required | Notes |
|---|---|---|---|
| MySQL | 8.0+ | ✅ | Primary database, Flyway auto-creates tables |
| Redis | 6.0+ | ✅ | Cache + distributed locks |
| Elasticsearch + IK | 8.5.0 (current image) | As needed | Disabled by default with JPA search; enable only when ES with IK is online |
| RabbitMQ | 3.13 (management) | As needed | Demonstrates timeout, batch coupon, and notification messaging |

### Prerequisites

```bash
java -version    # 17+
mvn -version     # 3.6+
node -v          # 20 (matches CI)
npm -v           # 9+
mysql --version  # 8.0+
redis-server --version  # 6.0+
docker --version        # for ES / RabbitMQ
docker compose version
```

### Step 1: Clone

```bash
git clone https://github.com/goaltang/homestay-booking-platform.git homestay3
cd homestay3
```

### Step 2: Create MySQL Database

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS homestay_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### Step 3: Start Redis

```bash
redis-server --daemonize yes
redis-cli ping  # expect: PONG
```

### Step 4: Start Elasticsearch and Messaging as Needed

```bash
# At the project root; keep any existing .env
cp -n .env.example .env
# Fill .env with your local settings for Compose

# Only when ES search is needed
docker build -t homestay-es-ik:test -f homestay-backend/src/test/resources/testcontainers/Dockerfile.es-ik homestay-backend
docker compose up -d elasticsearch
# Once ES is ready, verify the 8.5.0 JSON response
curl -fs http://localhost:9200

# For the three messaging demos
docker compose up -d rabbitmq
```

> Skip ES for daily development. The tracked `application.yml` defaults `elasticsearch.enabled` to `false` and disables ES repositories and ES health checks with it, so search uses JPA. To enable ES, start the service with IK first, then launch the backend with `ELASTICSEARCH_ENABLED=true`. RabbitMQ management is at `http://localhost:15672`, using the credentials in `.env`.

### Step 5: Configure Backend

```bash
cd homestay-backend
cp src/main/resources/application.example.properties src/main/resources/application-local.properties
```

Edit `application-local.properties`:

```properties
# Template uses 8080; frontend proxies target 8081
server.port=8081
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.data.redis.password=YOUR_REDIS_PASSWORD
jwt.secret=A_RANDOM_STRING_AT_LEAST_50_CHARS
# Optional ES; disabled by default. Set ELASTICSEARCH_ENABLED=true when needed.
spring.elasticsearch.uris=http://localhost:9200
```

> Activate the `local` profile explicitly to load this file. Do not rely on an untracked `application.properties`. Configure payment, email, maps, and AI support as needed; AI support is disabled by default.

### Step 6: Start Backend

```bash
# Continue in the homestay-backend directory from step 5
mvn clean compile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Verify: `curl -s "http://localhost:8081/api/homestays?page=0&size=1"` returns JSON.

Flyway automatically runs the repository migrations on startup; no manual table creation is needed.

### Step 7: Start Guest App

Open a new terminal at the project root:

```bash
cd homestay-front
cp .env.example .env.local  # optional: AMap keys
npm install
npm run dev
```

Open `http://localhost:5173`.

### Step 8: Start Admin App

Open another terminal at the project root:

```bash
cd homestay-admin
npm install
npm run dev
```

Open `http://localhost:5174`.

### Step 9: Admin Login

On first startup, the backend auto-creates the default admin **admin / admin888** (`ROLE_ADMIN`, only if the `admin` user doesn't exist).

Log in at `http://localhost:5174` with the default development account. Change it before exposing a deployment.

### Full Container Stack and Optional Services

Run these commands at the project root after filling `.env`. Build the IK image in step 4 before using ES for the first time.

```bash
# Default: MySQL, Redis, RabbitMQ, backend, and both frontends; no ES or monitoring
docker compose up -d --build

# Start ES and verify that it is ready before enabling the backend feature
docker compose up -d elasticsearch
curl -fs http://localhost:9200
ELASTICSEARCH_ENABLED=true docker compose --profile search up -d

# Start monitoring when needed, then stop it after use
docker compose --profile monitoring up -d prometheus grafana
docker compose stop prometheus grafana

# Disable ES in the backend first; wait for successful startup, then stop ES
ELASTICSEARCH_ENABLED=false docker compose up -d backend
docker compose stop elasticsearch
```

The `search` and `monitoring` profiles control containers. `ELASTICSEARCH_ENABLED` controls backend ES features. Compose no longer waits for ES health, so verify readiness before enabling it. Prometheus is at `http://localhost:9090` and Grafana at `http://localhost:3000`. Omitting a profile does not stop optional containers that are already running. Prometheus currently scrapes `host.docker.internal:8081`, which requires Docker Desktop host access; a local backend must be reachable from the container on that port.

For a local Maven backend, stop it and restart with `ELASTICSEARCH_ENABLED=true mvn spring-boot:run -Dspring-boot.run.profiles=local` in `homestay-backend`; use `false` to disable ES. With your local default configuration already set up, you can use `ELASTICSEARCH_ENABLED=true npm run dev:backend` at the project root.

Property changes while ES is disabled are not synchronized to its index. After re-enabling ES, an administrator should call `POST /api/admin/search/index/rebuild` before using ES search.

### Troubleshooting

| Issue | Cause | Fix |
|---|---|---|
| `Communications link failure` on startup | MySQL not running or wrong password | Check MySQL status and `spring.datasource.*` |
| `Unable to connect to Redis` | Redis not running | `redis-server --daemonize yes` |
| ES connection error on startup | ES enabled but offline or using a wrong URI | Start ES with IK and check `spring.elasticsearch.uris`; to disable ES, remove explicit local enable values and set `ELASTICSEARCH_ENABLED=false` |
| Frontend `/api` returns 404 | Backend not running | Ensure backend is on port 8081 |
| Port already in use | 5173/5174 occupied | Vite auto-assigns a new port — check terminal |
| `mvn` dependency download timeout | Network issue | Configure Maven mirror (e.g., Aliyun) |
| `npm install` timeout | Network issue | `npm config set registry https://registry.npmmirror.com` |

### AI Agent Quick Reference

For AI agents automating the installation, follow the structured flow in the [AI Agent 安装指引](#ai-agent-安装指引) section above. Key points:

1. **Flyway handles schema migrations** — no manual table creation is needed.
2. **ES is optional** — the default disables ES repositories and uses JPA search. To enable it, start ES with IK first and launch the backend with `ELASTICSEARCH_ENABLED=true`.
3. **Vite proxies are pre-configured** — no CORS setup needed.
4. **Local configuration**: set your actual environment values and port 8081 in `application-local.properties`; launch with `-Dspring-boot.run.profiles=local`.
5. **Verification**: `curl "http://localhost:8081/api/homestays?page=0&size=1"` should return JSON after backend starts.
