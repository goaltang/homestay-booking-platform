# 公网展示部署（国内服务器，按 IP 访问）

面向“把项目挂到公网当作品展示”的场景：一台国内云服务器加 Docker Compose，**不买域名、不备案**，直接用 `http://服务器IP` 访问。

> 这是展示环境，不是生产环境：没有 HTTPS，账号密码以明文在网络上传输。只用演示账号，不要录入真实手机号、身份证或支付信息。

## 结论速查

| 项 | 建议 |
|---|---|
| 服务器 | 2 vCPU / 4 GB / 3 Mbps 起，Ubuntu 22.04 或 24.04 |
| 访问方式 | 用户端 `http://<IP>`（80 端口），管理端 `http://<IP>:8089` |
| 域名与备案 | 不需要。**不要把未备案的域名解析到国内服务器**，云厂商会拦截访问 |
| HTTPS | 无。纯 IP 无法申请常规证书；买域名并完成备案后再加 |
| 数据库 | 单机 MySQL 容器，数据卷持久化，每日 `mysqldump` |
| 搜索 | `ELASTICSEARCH_ENABLED=false` 走 JPA；ES 留给本地演示 |
| 监控 | 4G 机器不跑 Prometheus/Grafana；`/actuator` 不对外开放 |

## 1. 服务器准备

购买时注意：

- **系统选 Ubuntu 22.04/24.04**，不要选 CentOS 7（已停止维护）。
- 1C1G / 2C2G 不够：MySQL、JVM、RabbitMQ 空载就要 1.5–2 GB，构建镜像时容易内存不足。
- **安全组**（云厂商控制台里配置）放行：
  - `22/TCP`：SSH，建议只允许你自己的 IP；
  - `80/TCP`：用户端；
  - `8089/TCP`：管理端，**建议只允许你自己的 IP**，面试演示时再临时放开。
- 其余端口全部关闭。数据库、Redis、RabbitMQ 只在容器网络内可见，不需要放行。

## 2. 初始化服务器

```bash
ssh root@<IP>

# 1) 系统更新与常用工具（国内云主机默认已使用厂商的 apt 镜像源）
apt update && apt upgrade -y
apt install -y curl git ufw ca-certificates

# 2) 2 GB swap（构建镜像和 MySQL 内存抖动时的保险）
fallocate -l 2G /swapfile && chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 3) 防火墙（与安全组保持一致）
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 8089/tcp
ufw --force enable

# 4) Docker：官方脚本走阿里云镜像源安装
curl -fsSL https://get.docker.com | sh -s -- --mirror Aliyun
systemctl enable --now docker
```

### 配置 Docker 镜像加速（必须）

国内服务器基本无法直接拉取 Docker Hub 镜像（`mysql`、`redis`、`maven`、`node`、`nginx` 等都来自 Docker Hub）。先配置加速地址：

- 阿里云：控制台 → 容器镜像服务 → 镜像工具 → 镜像加速器，复制你的专属地址；
- 腾讯云：服务器在腾讯云内网时可用 `https://mirror.ccs.tencentyun.com`。

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["<你的镜像加速地址>"]
}
EOF
systemctl daemon-reload && systemctl restart docker

# 验证：能拉到镜像再继续
docker pull nginx:alpine
```

公共加速地址时常失效。如果拉取失败，先换成云厂商控制台里的专属地址。

## 3. 部署

```bash
mkdir -p /opt && cd /opt
git clone <你的仓库地址> homestay3
cd homestay3

# 生成密钥与密码
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 24   # DB_PASSWORD / DB_ROOT_PASSWORD / REDIS_PASSWORD / RABBITMQ_PASSWORD

cp .env.prod.example .env
chmod 600 .env
vim .env                  # 把 1.2.3.4 换成服务器公网 IP，并填写各类密钥

./deploy/deploy.sh
```

`deploy.sh` 会：拉取代码 → 构建三个镜像 → 启动 → 等待 `backend` 健康 → 清理旧镜像。

- 首次构建会在容器里下载 Maven 和 npm 依赖，国内网络下可能要 10–20 分钟，属于正常情况。
- 地图找房的地点联想由后端调用高德 Web 服务，需要在 `.env` 里填 `AMAP_API_KEY`（与前端 `VITE_AMAP_WEB_SERVICE_KEY` 是同一个 Key），未填时搜索框没有联想结果。
- 首次启动由 Flyway 自动建表（52 张业务表和视图），**没有默认管理员和演示房源**。

## 4. 初始化数据

**省事做法**：填好 `.env` 后直接运行 `./deploy/demo.sh`（可以代替上面的 `deploy.sh`）。它会启动服务、导入下文的完整演示数据、创建随机密码的管理员，并打印访问地址。脚本需要 `python3-venv` 来安装 `bcrypt`，先执行 `apt install -y python3-venv`。演示数据只导入空库，重复运行不会覆盖。

需要逐步控制时，按以下手动步骤：

```bash
# 为导入脚本准备 Python 环境
apt install -y python3-venv
python3 -m venv tools/database/.venv
tools/database/.venv/bin/python -m pip install -r tools/database/requirements.txt \
  -i https://mirrors.aliyun.com/pypi/simple/

# 连接信息（数据库只在容器网络内，通过 docker compose exec 进入）
# 用环境变量指定 compose 文件：导入脚本会拒绝含 -f 的 mysql 命令
export COMPOSE_FILE=docker-compose.prod.yml
export MYSQL_DATABASE=homestay_db
export MYSQL_USER=homestay
read -r -s -p 'DB_PASSWORD: ' MYSQL_PASSWORD; echo; export MYSQL_PASSWORD

# 创建管理员（交互输入密码，没有默认密码）
tools/database/.venv/bin/python tools/database/import_data.py \
  --mysql-command "docker compose exec -T -e MYSQL_PWD mysql mysql" \
  admin --username admin --email admin@homestay.local
```

演示数据（5 个房源、5 张图片、演示账号）只能导入到**干净的库**，演示账号密码统一为 `123456`：

```bash
tools/database/.venv/bin/python tools/database/import_data.py \
  --mysql-command "docker compose exec -T -e MYSQL_PWD mysql mysql" \
  demo
```

想展示更接近真实规模的数据（约 5000 个房源、8000 个订单），改用完整演示数据。它同样只能导入干净的库，二选一：

```bash
tools/database/.venv/bin/python tools/database/import_data.py \
  --mysql-command "docker compose exec -T -e MYSQL_PWD mysql mysql" \
  demo-full
```

导入前先停后端，避免并发写入：`docker compose -f docker-compose.prod.yml stop backend`，导入后再 `start backend`。展示时建议在页面上注明“演示环境，密码 123456”，并且**不要给演示账号管理员权限**。

### 公开演示：一键体验与每晚重置

在 `.env` 中填两个演示快照里的账号（`demo.sh` 生成的本地配置已自动填好）：

```bash
DEMO_GUEST_USERNAME=user_4424
DEMO_HOST_USERNAME=host_0097
```

然后重新构建前端和后端：`docker compose -f docker-compose.prod.yml up -d --build front backend`。效果：

- 登录页和未登录时的页面顶部出现「以房客体验」「以房东体验」，点击直接登录；
- 后端拒绝这两个账号修改密码和个人资料（返回 403），避免被访客改掉后其他人无法登录。

访客仍能下单、评价、发消息，数据会越积越乱，用 `deploy/demo-reset.sh` 每晚恢复为演示快照。它会**删除并重建 `homestay_db`**，只保留管理员账号（含当前密码），并清空 Redis 和消息队列；未配置 `DEMO_*` 的部署会拒绝执行：

```bash
# 手动执行一次
./deploy/demo-reset.sh --yes

# 每天 03:30 自动重置（备份在 04:00，二者错开）
crontab -e
30 3 * * * cd /opt/homestay3 && ./deploy/demo-reset.sh --yes >> /var/log/homestay-demo-reset.log 2>&1
```

重置期间后端停止约 2–4 分钟。`uploads/` 中访客上传的文件不会删除。

## 5. 验收清单

```bash
# 两个入口
curl -I http://<IP>
curl -I http://<IP>:8089

# 后端健康（只在容器内可见）
docker compose -f docker-compose.prod.yml exec backend curl -s localhost:8081/actuator/health

# 监控端点不对外（应返回 404）
curl -s -o /dev/null -w '%{http_code}\n' http://<IP>/actuator/health

# 中间件不应暴露（应连接超时或被拒绝）
nc -vz <IP> 3306
nc -vz <IP> 6379
nc -vz <IP> 15672
nc -vz <IP> 8081

# 确认产物里没有打进 localhost
curl -s http://<IP> | grep -o 'localhost:8081' | head
```

手工过一遍：首页房源列表 → 注册/登录 → 收藏 → 下单 → 支付页（沙箱）→ 通知 WebSocket → 后台上架房源 → 上传图片 → AI 客服对话。

## 6. 备份与日常运维

```bash
# 每日 4 点备份，保留 7 天（含 uploads，文件权限 600）
crontab -e
0 4 * * * cd /opt/homestay3 && ./deploy/backup.sh >> /var/log/homestay-backup.log 2>&1

# 更新代码
cd /opt/homestay3 && ./deploy/deploy.sh

# 查看日志
docker compose -f docker-compose.prod.yml logs -f --tail 100 backend
```

`backups/` 里有用户数据。展示环境里虽然只有演示数据，也不要把备份文件放到公开位置。

## 7. 常见问题

| 现象 | 原因与处理 |
|---|---|
| `docker pull` 超时或 `toomanyrequests` | 未配置或加速地址失效，见“配置 Docker 镜像加速” |
| 构建卡在 Maven / npm 下载 | 国内网络较慢，耐心等待；多次失败时检查服务器出网带宽 |
| 浏览器打不开页面 | 安全组或 `ufw` 没放行 80/8089；`docker compose -f docker-compose.prod.yml ps` 确认 front/admin 在运行 |
| 页面能开但接口 404/502 | backend 未 healthy，或前端镜像打进了旧 API 地址；检查 `ps` 后重建 front |
| 浏览器请求 `localhost:8081` | 镜像构建时覆盖了空的 API 地址。确认部署用的是 `docker-compose.prod.yml` |
| 同一浏览器里用户端和管理端互相“顶号” | 两个入口在同一个 IP 上，浏览器按主机名共享登录 Cookie（端口不区分）。演示时用两个浏览器或一个开无痕窗口 |
| 图片显示不出来 | `APP_SERVER_URL` 没改成服务器 IP（用户端不是 80 端口时要带端口）；或 `uploads` 目录未挂载 |
| MySQL 反复重启 | 内存不足。2G 内存机器把 `innodb-buffer-pool-size` 调到 128M、JVM `-Xmx320m` |
| 磁盘被日志占满 | 检查 `df -h`；compose 已限制单容器日志 10MB × 3 |

## 8. 与本地 compose 的差异

| 项 | `docker-compose.yml`（本地） | `docker-compose.prod.yml`（公网展示） |
|---|---|---|
| 端口 | 8081/8088/8089/3306/6379/5672/15672 全开 | 只开用户端 80 和管理端 8089 |
| 入口 | 直连各服务 | 前端镜像内 Nginx 反代 `/api`、`/uploads`、`/ws`；`/actuator` 返回 404 |
| 密码 | 允许默认值 | `.env` 强制注入，Redis/MQ 必须设密码 |
| 内存 | 不限制 | 每个服务限制内存，JVM 固定堆 |
| 重启 | 无 | `unless-stopped` |
| 日志 | Docker 默认（无限增长） | 10MB × 3 轮转 |
| ES/监控 | 可选 profile | 默认不启动 |

## 以后买了域名

完成 ICP 备案后再把域名解析到服务器。届时在前面加一层反向代理（如 Caddy 或 Nginx）申请 HTTPS 证书，并把 `APP_SERVER_URL`、`APP_CORS_ALLOWED_ORIGINS` 和支付宝回调地址改成域名。
