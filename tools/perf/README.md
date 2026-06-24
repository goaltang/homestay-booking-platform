# 性能压测工具集

> **核心原则：与开发库/生产库完全隔离**。本目录所有脚本都连接 Docker 独立 MySQL（端口 3307、库名 `homestay_perf`），永不触碰 `localhost:3306/homestay_db`。

## 文件清单

| 文件 | 作用 |
|------|------|
| `../../docker-compose.perf.yml` | 独立 MySQL 8.0 + Redis 7，端口 3307/6380 |
| `../../homestay-backend/src/main/resources/application-perf.properties` | 后端 perf profile，连 perf 库 |
| `data/init-perf-data.sql` | 灌入 1k 用户 + 5k 房型 + 30w 可用性 + 1w 订单 |
| `load-test.js` | k6 压测脚本，3 个场景（列表/详情/下单） |
| `run-perf-test.ps1` | 一键脚本：启环境 → 灌数据 → 起后端 → 压测 |

## 快速开始

### 0. 前置工具
```powershell
docker --version        # 任意 20+
mvn --version           # 任意 3.8+
k6 version              # winget install k6
```

### 1. 一键全流程
```powershell
.\tools\perf\run-perf-test.ps1
```
自动完成：启 MySQL+Redis → 灌数据 → 编后端 → 启后端 → 跑 k6 → 输出报告。

### 2. 分步执行
```powershell
# 仅启动环境（不灌数据、不跑压测）
.\tools\perf\run-perf-test.ps1 -OnlyUp

# 启动并跑压测（跳过数据初始化）
.\tools\perf\run-perf-test.ps1 -SkipData

# 仅销毁环境（跑完必做）
.\tools\perf\run-perf-test.ps1 -OnlyDown
```

## 端口与连接串

| 服务 | 端口 | 用户 | 密码 | 库名 |
|------|------|------|------|------|
| MySQL | **3307** | perf_user | perf_pwd | homestay_perf |
| Redis | **6380** | - | - | db 0 |
| 后端 | 8080 | - | - | - |

> 开发库是 3306/homestay_db，**压测库是 3307/homestay_perf**，从端口就杜绝误连。

## 压测场景说明

`load-test.js` 跑三个并发场景（贴近真实流量）：

| 场景 | 流量占比 | 目标 | p95 阈值 |
|------|----------|------|----------|
| `listScenario` | 80% | `GET /api/homestays?cityCode=xxx&page=0&size=20` | < 500ms |
| `detailScenario` | 15% | `GET /api/homestays/{id}` | < 800ms |
| `orderScenario` | 5% | `POST /api/orders` | < 1500ms |

最高并发：列表 200 VU、详情 80 VU、下单 50 VU。
默认 110 秒跑完。

## 报告输出

| 文件 | 内容 |
|------|------|
| `reports/summary.json` | 压测汇总（p95/p99/TPS/错误率） |
| `reports/k6-result.json` | k6 全量指标流 |
| `reports/backend.log` | 后端运行日志 |
| `reports/backend.err.log` | 后端错误日志 |

## Prometheus + Grafana 接入

后端已暴露 `/actuator/prometheus`，Prometheus 抓取配置：

```yaml
scrape_configs:
  - job_name: 'homestay-perf'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['127.0.0.1:8080']
        labels:
          env: perf
```

Grafana 推荐看板：
- JVM 内存 / GC / 线程
- HikariCP 连接池使用率
- HTTP 请求 p95/p99（按 URI 标签）
- Tomcat 线程池
- MySQL 慢查询（从 MySQL 容器看）

## 调优检查清单

跑完压测发现瓶颈时，按此顺序排查：

1. **JVM**：是否频繁 Full GC？→ 调大堆 / 换 G1 / ZGC
2. **DB 连接池**：HikariCP 等待 > 50ms？→ 调大 `maximum-pool-size`（已设为 50）
3. **慢 SQL**：`docker exec homestay-mysql-perf mysqldumpslow /var/lib/mysql/...-slow.log`
4. **缓存命中**：`/actuator/metrics/cache.gets` 看 hit rate
5. **N+1 查询**：开 `spring.jpa.show-sql=true` 重跑，看 SQL 数量
6. **接口代码**：`async-profiler` 抓 CPU 火焰图

## 安全红线

- ⛔ 禁止把 `load-test.js` 的 `BASE_URL` 改成 dev/prod 环境
- ⛔ 禁止在压测库跑非压测 SQL（如清空 dev 库的 SQL 误粘进来）
- ⛔ 跑完不销毁 → 端口/磁盘被占用，影响他人
- ✅ 压测脚本里所有数据库连接串都写死端口 `3307`、库名 `homestay_perf`
