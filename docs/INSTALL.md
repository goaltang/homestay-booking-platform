# 安装教程 / Installation Guide

> 本教程面向人类开发者和 AI Agent。AI Agent 请优先阅读 [AI Agent 安装指引](#ai-agent-安装指引) 一节。

**[English version below](#english-version)**

---

## 中文

### 概览

本项目由 3 个可独立运行的子项目组成：

| 子项目 | 技术栈 | 默认端口 | 说明 |
|---|---|---|---|
| `homestay-backend` | Java 17 + Spring Boot 3.0.2 + Maven | 8080 | 后端 API 服务 |
| `homestay-front` | Vue 3 + TypeScript + Vite 5 | 5173 | 用户端 + 房东端 |
| `homestay-admin` | Vue 3 + TypeScript + Vite 6 | 5174 | 管理员端 |

依赖的基础设施：

| 服务 | 版本 | 必需 | 说明 |
|---|---|---|---|
| MySQL | 8.0+ | ✅ | 主数据库，Flyway 自动建表 |
| Redis | 6.0+ | ✅ | 缓存 + 分布式锁 |
| Elasticsearch | 8.5+ | ❌ | 全文搜索，可降级为 JPA 搜索 |

### 前置条件检查

在安装之前，确认以下工具已安装：

```bash
# Java 17+
java -version
# 预期输出包含: openjdk version "17" 或更高

# Maven 3.6+
mvn -version
# 预期输出包含: Apache Maven 3.6+

# Node.js 18+
node -v
# 预期输出: v18.x 或更高

# npm 9+
npm -v
# 预期输出: 9.x 或更高

# MySQL 8.0+
mysql --version
# 预期输出包含: Ver 8.0

# Redis
redis-server --version
# 预期输出包含: v=6.0 或更高

# Docker（仅 ES 需要）
docker --version
docker-compose --version
```

### 步骤 1：克隆项目

```bash
git clone https://github.com/goaltang/homestay3.git
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

### 步骤 4：启动 Elasticsearch（可选）

```bash
# 在项目根目录
docker-compose up -d elasticsearch

# 等待 ES 启动（约 30 秒）
sleep 30

# 验证
curl -s http://localhost:9200 | grep '"number"'
# 预期输出包含: "number" : "8.5.3"
```

> 如果不需要搜索功能，跳过此步骤，并在后端配置中设置 `elasticsearch.enabled=false`。

### 步骤 5：配置后端

```bash
cd homestay-backend

# 复制配置模板
cp src/main/resources/application.example.properties src/main/resources/application-local.properties
```

编辑 `src/main/resources/application-local.properties`，至少修改以下配置：

```properties
# MySQL（必填）
spring.datasource.username=root
spring.datasource.password=你的MySQL密码

# Redis（如果设置了密码）
spring.data.redis.password=你的Redis密码

# JWT 密钥（必填，使用随机长字符串）
jwt.secret=替换为一个至少50字符的随机字符串

# Elasticsearch（如果未启动 ES，设为 false）
elasticsearch.enabled=false
```

也可以直接修改 `application.properties`（默认配置已包含可用的开发值）。

### 步骤 6：启动后端

```bash
cd homestay-backend
mvn clean compile
mvn spring-boot:run
```

验证：

```bash
# 等待约 30 秒后
curl -s http://localhost:8080/api/homestays?page=0&size=1
# 预期: 返回 JSON 响应（可能为空列表）
```

后端启动时 Flyway 会自动执行 V1 ~ V49 共 41 个迁移脚本，自动创建全部数据库表和种子数据。

### 步骤 7：启动用户端（房客 + 房东）

```bash
cd homestay-front

# 可选：配置高德地图 Key
cp .env.example .env.local
# 编辑 .env.local，填入 VITE_AMAP_API_KEY 等

npm install
npm run dev
```

验证：浏览器打开 `http://localhost:5173`，应看到首页。

> Vite 代理配置：`/api` → `http://127.0.0.1:8080`，`/uploads` → `http://127.0.0.1:8080`

### 步骤 8：启动管理员端

```bash
cd homestay-admin
npm install
npm run dev
```

验证：浏览器打开 `http://localhost:5174`，应看到登录页。

> Vite 代理配置：`/api` → `http://localhost:8080`

### 步骤 9：创建管理员账号

项目不预置管理员账号。先在用户端 (`localhost:5173`) 注册一个普通账号，然后在数据库中提升角色：

```bash
mysql -u root -p homestay_db -e "UPDATE users SET role = 'ADMIN' WHERE email = '你注册的邮箱';"
```

验证：

```bash
mysql -u root -p homestay_db -e "SELECT id, email, role FROM users WHERE role = 'ADMIN';"
# 预期输出包含你的管理员账号
```

然后用该账号登录管理员端 (`localhost:5174`)。

### 常见问题

| 问题 | 原因 | 解决方案 |
|---|---|---|
| 后端启动报 `Communications link failure` | MySQL 未启动或密码错误 | 检查 MySQL 状态和 `spring.datasource.*` 配置 |
| 后端启动报 `Unable to connect to Redis` | Redis 未启动 | `redis-server --daemonize yes` |
| 后端启动报 ES 连接失败 | Elasticsearch 未启动 | 启动 ES 或设置 `elasticsearch.enabled=false` |
| 前端 `/api` 请求 404 | 后端未启动或端口不对 | 确认后端在 8080 端口运行 |
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
  RUN: node -v 2>&1 | grep -qE 'v(1[89]|[2-9][0-9])' && echo OK || echo MISSING
  RUN: mysql --version 2>&1 | grep -q '8\.' && echo OK || echo MISSING
  RUN: redis-server --version 2>&1 | grep -q 'v=' && echo OK || echo MISSING
  IF any MISSING → 安装对应依赖后重新检查

STEP 2: 创建数据库
  RUN: mysql -u root -p"${MYSQL_ROOT_PASSWORD:-root}" -e "CREATE DATABASE IF NOT EXISTS homestay_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  VERIFY: mysql -u root -p"${MYSQL_ROOT_PASSWORD:-root}" -e "SHOW DATABASES LIKE 'homestay_db';" | grep homestay_db

STEP 3: 确保 Redis 运行
  RUN: redis-cli ping 2>/dev/null || (redis-server --daemonize yes && sleep 2 && redis-cli ping)
  VERIFY: redis-cli ping → PONG

STEP 4: Elasticsearch（可选）
  IF 需要搜索功能:
    RUN: docker-compose up -d elasticsearch
    WAIT: 30s
    VERIFY: curl -s http://localhost:9200 | grep '"number"'
  ELSE:
    在后端配置中设置 elasticsearch.enabled=false

STEP 5: 配置后端
  RUN: cd homestay-backend && cp -n src/main/resources/application.example.properties src/main/resources/application-local.properties
  EDIT: application-local.properties
    - spring.datasource.username → 实际 MySQL 用户名
    - spring.datasource.password → 实际 MySQL 密码
    - spring.data.redis.password → 实际 Redis 密码（无密码留空）
    - jwt.secret → 随机长字符串（≥50 字符）
    - elasticsearch.enabled → true/false（取决于 STEP 4）
  NOTE: 如果直接修改 application.properties，默认值已可用于开发
    - MySQL: root / 111111
    - Redis: localhost:6379 / 000000
    - ES: localhost:9200, enabled=true

STEP 6: 启动后端
  RUN: cd homestay-backend && mvn clean compile -q && mvn spring-boot:run &
  WAIT: 直到日志出现 "Started HomestayBackendApplication"（约 30-60s）
  VERIFY: curl -s http://localhost:8080/api/homestays?page=0&size=1 | head -c 200
  EXPECT: JSON 响应（{"success":true,...} 或类似）

STEP 7: 启动用户端
  RUN: cd homestay-front && npm install --prefer-offline 2>&1 | tail -1 && npm run dev &
  WAIT: 直到日志出现 "Local: http://localhost:5173"
  VERIFY: curl -s http://localhost:5173 | grep -q '<title>' && echo OK

STEP 8: 启动管理员端
  RUN: cd homestay-admin && npm install --prefer-offline 2>&1 | tail -1 && npm run dev &
  WAIT: 直到日志出现 "Local: http://localhost:5174"
  VERIFY: curl -s http://localhost:5174 | grep -q '<title>' && echo OK

STEP 9: 创建管理员（可选）
  NOTE: 需要先在用户端注册一个账号，或直接用 SQL 插入
  RUN: mysql -u root -p homestay_db -e "UPDATE users SET role = 'ADMIN' WHERE email = '<用户邮箱>';"
```

### Agent 注意事项

1. **数据库迁移自动执行**：后端启动时 Flyway 自动运行 V1~V49 迁移，不需要手动建表或导入 SQL。
2. **ES 可选**：如果环境中没有 Docker，设置 `elasticsearch.enabled=false` 即可跳过，搜索功能降级为 JPA。
3. **前端代理**：两个前端项目的 Vite 配置已内置 `/api` 代理到 `localhost:8080`，不需要额外配置 CORS。
4. **端口冲突**：如果 8080/5173/5174 被占用，修改对应配置文件中的端口。
5. **不要修改 `application.properties` 中的默认开发值**（MySQL 密码 `111111`、Redis 密码 `000000`），除非环境确实不同。这些是开发环境约定值。
6. **npm 镜像**：在中国大陆环境下，先执行 `npm config set registry https://registry.npmmirror.com`。
7. **Maven 镜像**：在中国大陆环境下，在 `~/.m2/settings.xml` 中配置阿里云镜像。

### Agent 验证清单

安装完成后，逐项确认：

- [ ] `curl -s http://localhost:8080/api/homestays?page=0&size=1` 返回 JSON
- [ ] `curl -s http://localhost:5173` 返回 HTML
- [ ] `curl -s http://localhost:5174` 返回 HTML
- [ ] 用户端注册 → 登录 → 浏览房源 流程正常
- [ ] 管理员端登录正常（需先提升角色）

---

## English Version

### Overview

This project consists of 3 independently runnable sub-projects:

| Sub-project | Stack | Default Port | Description |
|---|---|---|---|
| `homestay-backend` | Java 17 + Spring Boot 3.0.2 + Maven | 8080 | Backend API server |
| `homestay-front` | Vue 3 + TypeScript + Vite 5 | 5173 | Guest + Host app |
| `homestay-admin` | Vue 3 + TypeScript + Vite 6 | 5174 | Admin app |

Required infrastructure:

| Service | Version | Required | Notes |
|---|---|---|---|
| MySQL | 8.0+ | ✅ | Primary database, Flyway auto-creates tables |
| Redis | 6.0+ | ✅ | Cache + distributed locks |
| Elasticsearch | 8.5+ | ❌ | Full-text search, degrades to JPA search |

### Prerequisites

```bash
java -version    # 17+
mvn -version     # 3.6+
node -v          # 18+
npm -v           # 9+
mysql --version  # 8.0+
redis-server --version  # 6.0+
docker --version        # only for ES
```

### Step 1: Clone

```bash
git clone https://github.com/goaltang/homestay3.git
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

### Step 4: Start Elasticsearch (Optional)

```bash
docker-compose up -d elasticsearch
# wait ~30s
curl -s http://localhost:9200 | grep '"number"'
# expect: "number" : "8.5.3"
```

> Skip this and set `elasticsearch.enabled=false` if you don't need search.

### Step 5: Configure Backend

```bash
cd homestay-backend
cp src/main/resources/application.example.properties src/main/resources/application-local.properties
```

Edit `application-local.properties`:

```properties
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.data.redis.password=YOUR_REDIS_PASSWORD
jwt.secret=A_RANDOM_STRING_AT_LEAST_50_CHARS
elasticsearch.enabled=false  # if no ES
```

> The default `application.properties` already has working dev values (MySQL: root/111111, Redis: 000000).

### Step 6: Start Backend

```bash
cd homestay-backend
mvn clean compile
mvn spring-boot:run
```

Verify: `curl -s http://localhost:8080/api/homestays?page=0&size=1` returns JSON.

Flyway automatically runs all 41 migrations (V1–V49) on startup.

### Step 7: Start Guest App

```bash
cd homestay-front
cp .env.example .env.local  # optional: AMap keys
npm install
npm run dev
```

Open `http://localhost:5173`.

### Step 8: Start Admin App

```bash
cd homestay-admin
npm install
npm run dev
```

Open `http://localhost:5174`.

### Step 9: Create Admin Account

Register a normal account on the guest app, then promote it:

```bash
mysql -u root -p homestay_db -e "UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';"
```

Log in at `http://localhost:5174` with the promoted account.

### Troubleshooting

| Issue | Cause | Fix |
|---|---|---|
| `Communications link failure` on startup | MySQL not running or wrong password | Check MySQL status and `spring.datasource.*` |
| `Unable to connect to Redis` | Redis not running | `redis-server --daemonize yes` |
| ES connection error on startup | Elasticsearch not running | Start ES or set `elasticsearch.enabled=false` |
| Frontend `/api` returns 404 | Backend not running | Ensure backend is on port 8080 |
| Port already in use | 5173/5174 occupied | Vite auto-assigns a new port — check terminal |
| `mvn` dependency download timeout | Network issue | Configure Maven mirror (e.g., Aliyun) |
| `npm install` timeout | Network issue | `npm config set registry https://registry.npmmirror.com` |

### AI Agent Quick Reference

For AI agents automating the installation, follow the structured flow in the [AI Agent 安装指引](#ai-agent-安装指引) section above. Key points:

1. **Flyway handles all DB migrations** (V1–V49, 41 scripts) — never run SQL manually.
2. **ES is optional** — set `elasticsearch.enabled=false` to skip.
3. **Vite proxies are pre-configured** — no CORS setup needed.
4. **Default dev credentials**: MySQL `root/111111`, Redis `000000` — change only if your environment differs.
5. **Verification**: `curl localhost:8080/api/homestays?page=0&size=1` should return JSON after backend starts.
