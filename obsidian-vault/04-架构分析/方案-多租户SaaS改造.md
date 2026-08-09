---
title: 方案：多租户 SaaS 改造
date: 2026-06-24
tags:
  - multi-tenant
  - saas
  - architecture
  - backend
---

# 方案：多租户 SaaS 改造

> [!info] 定位
> 将现有单租户民宿平台改造为多租户 SaaS，一套部署服务多个客户（区域民宿运营商），
> 每个租户数据隔离、品牌独立、配置独立，按房源数/月费收费。

> [!warning] 实施状态
> - 状态：**设计中，未实施**
> - 本文档为改造方案与工作量评估，供决策与排期参考
> - 涉及：后端为主 + 前端 + ES + Redis + 定时任务

---

## 一、为什么走多租户 SaaS

| 对比项 | 自营 C 端平台 | 多租户 SaaS |
|--------|-------------|------------|
| 获客方式 | 烧钱拉房东和房客 | 客户是"想开平台的人"，少量销售即可 |
| 竞争格局 | 与美团/携程正面竞争 | 错位竞争，服务区域/垂直场景 |
| 收入模型 | 依赖流量规模，不确定 | 按房源数/月费收费，可预测 |
| 现有优势 | 不明显 | 25 模块 + 完整文档 = 现成产品，单人可交付 |
| 现金流 | 前期亏损 | 首付 + 月费，现金流健康 |

> 本项目 25 个业务模块 + 10W 行代码 + 50+ 篇文档本身就是最强销售物料，To B 现金流更稳。

---

## 二、隔离方案选型

| 方案 | 做法 | 隔离性 | 成本 | 适合场景 |
|------|------|--------|------|---------|
| **共享库 + 字段隔离** | 每张表加 `tenant_id`，查询自动带过滤 | 中 | 低 | 中小租户、起步阶段（**推荐 M2**） |
| 共享库 + 独立 Schema | 每个租户一个 MySQL schema | 高 | 中 | 对隔离要求高 |
| 独立数据库 | 每个租户一套完整数据库 | 最高 | 高 | 大客户、合规要求（**M1 等价**） |

本项目使用 JPA，**共享库 + 字段隔离**最省事——加 `@TenantId` 注解或 Hibernate Filter，几乎不用改业务代码。但 `@Query` 需人工核对（详见第五节）。

---

## 三、现状评估（影响改造的关键事实）

基于代码扫描的实际数据：

| 维度 | 现状 | 对改造的影响 |
|------|------|-------------|
| 实体数量 | 50 个，全部单租户 | 需区分"租户隔离"和"全局共享"两类 |
| Repository | 49 个 | 查询隔离的主战场 |
| `@Query` 注解 | **100+ 个**（JPQL 为主） | 最大工作量来源，需逐个加 tenant 过滤 |
| 原生 SQL | 仅 1 处 `nativeQuery`（`AmenityRepository`） | 好消息，几乎全是 JPQL，改造成本低 |
| 认证 | JWT + `CustomUserDetails`（只带 userId/username/authorities） | 需扩展，加 tenantId claim |
| 数据迁移 | Flyway 已到 V49 | 加一个 V50 即可 |
| ES 搜索 | `HomestayIndexingService` + `HomestayDocument`（index: `homestay_index`） | ES 文档需加 tenantId 字段并过滤 |
| 定时任务 | 11 个 `@Scheduled`，分布在 9 个文件 | 需改为按租户遍历执行 |
| Redis 缓存 | Spring Cache + 手动缓存 | 缓存 key 需加 `tenant:{id}:` 前缀 |
| 支付配置 | `PaymentConfig` 单一配置 | 需改为租户级，每个租户自己的商户号 |

---

## 四、实体分类：哪些表需要 tenant_id

### 4.1 核心业务表（必须隔离，约 35-40 张）

| 类别 | 涉及实体 |
|------|---------|
| 用户与房东 | `User` |
| 房源 | `Homestay`, `HomestayImage`, `HomestayGroup`, `HomestayAvailability`, `HomestayAuditLog` |
| 订单与财务 | `Order`, `OrderPriceSnapshot`, `PaymentRecord`, `RefundRecord`, `Earning` |
| 入住退房 | `CheckInRecord`, `CheckOutRecord` |
| 评价 | `Review`, `ReviewImage` |
| 争议违规 | `DisputeRecord`, `ViolationReport`, `ViolationAction` |
| 消息通知 | `Conversation`, `Message`, `Notification`, `NotificationPreference`, `NotificationBroadcastJob` |
| 营销促销 | `CouponTemplate`, `UserCoupon`, `CouponBatchIssueTask`, `CouponBatchIssueItem`, `CouponAnalytics`, `PromotionCampaign`, `PromotionRule`, `PromotionUsage`, `ReferralRecord` |
| 定价 | `PricingRule` |
| 用户行为 | `UserFavorite`, `UserBehaviorEvent`, `UserPreferenceProfile` |
| 实验 | `AbExperiment`, `AbVariant`, `AbAssignment`, `AbEvent` |
| 运营 | `Banner`, `Announcement` |

### 4.2 字典/全局表（可共享，不加 tenant_id）

| 实体 | 说明 |
|------|------|
| `Amenity`, `AmenityCategory` | 设施库，全租户共用更合理 |
| `HomestayType`, `TypeCategory` | 房源类型字典 |
| `HolidayCalendar` | 节假日日历，全国统一 |
| `SystemConfig` | 平台级配置（或改为租户级，视产品定位） |

### 4.3 系统级表（视产品定位）

| 实体 | 处理方式 |
|------|---------|
| `Admin` | 平台运营级 admin 跨租户；租户级 admin 加 tenant_id 隔离 |
| `OperationLog`, `LoginLog` | 跟随 Admin 策略 |

---

## 五、架构设计（四大改造块）

### 5.1 第一块：基础设施（Tenant 上下文 + 认证扩展）

新增组件：

| 文件 | 职责 |
|------|------|
| `entity/Tenant.java` | 租户表（id, name, code, domain, 套餐, 状态, 到期时间） |
| `security/TenantContext.java` | ThreadLocal 存当前请求的 tenantId |
| `config/TenantInterceptor.java` | 从 Header / 子域名解析 tenantId 放入 TenantContext |

认证链路扩展：

| 文件 | 改动 |
|------|------|
| `security/CustomUserDetails.java` | 新增 `tenantId` 字段 |
| `security/JwtTokenProvider.java` | 生成 token 时写入 `tenantId` claim（现有 claims: subject/authorities/userId） |
| `security/JwtAuthenticationFilter.java` | 解析 token 后将 tenantId 放入 `TenantContext` |

### 5.2 第二块：数据库 + 实体（tenant_id 列 + 基类）

**Flyway V50**：给 35-40 张业务表加 `tenant_id BIGINT` 列 + 索引。

实体改造——抽取基类：

```java
@MappedSuperclass
@Getter @Setter
public abstract class TenantAware {
    @Column(name = "tenant_id")
    private Long tenantId;

    @PrePersist
    protected void fillTenant() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.get();
        }
    }
}
```

让 35-40 个业务实体继承 `TenantAware`，写入时自动填充，不用手动 set。

### 5.3 第三块：查询隔离（最耗时）

这是**最大的坑**。Hibernate 6 的 `@TenantId` 注解对派生查询能自动注入过滤，但**对 `@Query` JPQL 不一定自动生效**。

| 查询类型 | 占比 | 改造方式 | 工作量 |
|---------|------|---------|--------|
| 派生查询 `findByOwner` | 约 40% | `@TenantId` 自动处理 | 几乎零改动 |
| JPQL `@Query` | 约 60%（100+个） | 需手动加 `AND e.tenantId = :tenantId` | **逐个改，最耗时** |
| 原生 SQL | 1 处 | 手动加 | 5 分钟 |

改造示例：

```java
// 改前
@Query("SELECT h FROM Homestay h WHERE h.status = :status")
// 改后
@Query("SELECT h FROM Homestay h WHERE h.status = :status AND h.tenantId = :tenantId")
```

> [!tip] 折中方案
> 上 Hibernate `@Filter` + `@FilterDef`，在 `EntityManager` 层全局开启 tenant 过滤，
> 能减少手动改 @Query 的数量，但 JOIN 和子查询仍需人工核对。

涉及 `@Query` 最多的 Repository（重点排查）：

| Repository | `@Query` 数量 |
|-----------|--------------|
| `HomestayRepository` | 20+ |
| `EarningRepository` | 10+ |
| `OrderRepository` | 10+ |
| `HomestayAvailabilityRepository` | 10+ |
| `NotificationRepository` | 6+ |
| `HomestayAuditLogRepository` | 4+ |
| 其余 | 分散 |

### 5.4 第四块：ES + Redis + 定时任务 + 支付

| 模块 | 改造点 |
|------|--------|
| ES 索引 | `HomestayDocument` 加 `tenantId` 字段；`HomestayIndexingService` 写入时填充；搜索时按 tenantId 过滤 |
| Redis 缓存 | 缓存 key 加 `tenant:{id}:` 前缀，防止串数据 |
| 定时任务 | 11 个 `@Scheduled` 改为按租户遍历执行 |

定时任务清单（需改造）：

| 文件 | 任务 |
|------|------|
| `OrderTimeoutService` | 订单超时取消（3 个定时方法） |
| `OrderAutoStatusService` | 订单自动入住/退房 |
| `CouponCleanupJob` | 优惠券过期清理 |
| `CouponExpiryNotificationService` | 优惠券到期提醒 |
| `CampaignAutoStatusService` | 活动自动上下线 |
| `CheckInReminderService` | 入住提醒 |
| `NotificationCleanupService` | 通知清理 |
| `UserProfileAggregationJob` | 用户画像聚合 |
| `HolidayDataCoverageMonitor` | 节假日数据监控（可保持全局） |

支付配置改造：

| 文件 | 改动 |
|------|------|
| `config/PaymentConfig.java` | 从单一配置改为租户级配置（每个租户自己的支付宝商户号） |
| `service/gateway/` | 支付网关按 tenantId 加载对应配置 |

---

## 六、实施路线（渐进式三里程碑）

> [!important] 核心策略
> 不要一把梭。先 M1 私有化部署验证市场，再 M2 共享部署降本，最后 M3 完整 SaaS。

### M1：私有化部署版（可立即售卖）

| 项 | 说明 |
|----|------|
| 隔离方式 | 单实例单租户，部署多套实例 |
| 代码改动 | 加 `TenantAware` 基类 + 配置外置，1-2 天 |
| 隔离安全性 | 最彻底，不会出现租户间数据泄漏 |
| 商业价值 | 立刻能报价收款，验证市场 |
| 适合客户 | 地方民宿运营商、区域平台 |

### M2：共享库多租户

| 项 | 说明 |
|----|------|
| 隔离方式 | 共享库 + `tenant_id` 字段隔离 |
| 代码改动 | V50 迁移 + 35-40 实体加字段 + 100+ @Query 加过滤 + ES/Redis/定时任务改造 |
| 隔离安全性 | 中等，依赖查询过滤正确性 |
| 商业价值 | 同一套部署服务多客户，降低运维成本 |
| 前置条件 | M1 验证后有 3-5 个客户 |

### M3：完整 SaaS

| 项 | 说明 |
|----|------|
| 新增能力 | 租户自助注册 + 套餐限额 + 计费 + 独立域名 |
| 商业价值 | 规模化运营，自助开通 |
| 前置条件 | 技术团队 2-3 人 |

---

## 七、工作量估算（M2 共享多租户）

| 阶段 | 内容 | 预估工时 | 难度 |
|------|------|---------|------|
| ① 基础设施 | Tenant 表 + Context + 过滤器 + JWT 扩展 | 1-2 天 | 低 |
| ② 数据库+实体 | V50 迁移 + 35-40 个实体加字段 | 2-3 天 | 低-中 |
| ③ 查询隔离 | 100+ 个 @Query 加 tenant 过滤 | 3-5 天 | **中-高（最磨人）** |
| ④ ES+Redis+任务 | ES 过滤 + 缓存 key + 定时任务 + 支付配置 | 2-3 天 | 中 |
| ⑤ 测试 | 全链路验证 + 数据隔离测试 | 2-3 天 | 中 |
| **合计** | | **10-16 天**（单人） | |

> [!note] M1 工作量
> M1 私有化版仅需 1-2 天：加 `TenantAware` 基类 + 配置外置，不做查询过滤，靠部署隔离。

---

## 八、风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| `@Query` 漏加 tenant 过滤 | 租户间数据泄漏 | ① 编写隔离测试覆盖所有 Repository ② 代码审查红线 |
| 定时任务跨租户串数据 | 订单/通知错乱 | 任务入口统一遍历租户，单租户事务隔离 |
| ES 搜索跨租户 | 搜索结果包含其他租户房源 | ES 查询强制带 tenantId filter |
| 缓存 key 冲突 | 串数据 | Redis key 统一加 `tenant:{id}:` 前缀 |
| 单人改造 100+ @Query 回归风险高 | 线上故障 | **优先走 M1，M2 等团队扩充再上** |
| 支付配置串租户 | 资金事故 | 支付配置强绑定 tenantId，启动时校验 |

---

## 九、关键文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `entity/Tenant.java` | 租户实体 |
| `entity/base/TenantAware.java` | 租户隔离基类（@MappedSuperclass） |
| `security/TenantContext.java` | ThreadLocal 租户上下文 |
| `config/TenantInterceptor.java` | 请求拦截器，解析 tenantId |
| `repository/TenantRepository.java` | 租户 Repository |
| `db/migration/V50__add_tenant_id_columns.sql` | 多租户迁移脚本 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `security/CustomUserDetails.java` | 加 tenantId 字段 |
| `security/JwtTokenProvider.java` | token 加 tenantId claim |
| `security/JwtAuthenticationFilter.java` | 解析 tenantId 入 TenantContext |
| `config/SecurityConfig.java` | 注册 TenantInterceptor |
| 35-40 个业务实体 | 继承 TenantAware |
| 49 个 Repository | 100+ @Query 加 tenant 过滤 |
| `model/search/HomestayDocument.java` | 加 tenantId 字段 |
| `service/search/HomestayIndexingService` | 写入/查询带 tenantId |
| 11 个定时任务 | 按租户遍历执行 |
| `config/PaymentConfig.java` | 改为租户级配置 |
| 缓存相关 Service | key 加 tenant 前缀 |

---

## 十、实施 Checklist（M2）

- [ ] 新增 `Tenant` 实体 + `TenantRepository`
- [ ] 新增 `TenantAware` 基类 + `TenantContext` + `TenantInterceptor`
- [ ] JWT 扩展 tenantId claim（生成 + 解析）
- [ ] Flyway V50：35-40 张表加 tenant_id 列 + 索引
- [ ] 35-40 个业务实体继承 TenantAware
- [ ] 49 个 Repository 的 100+ @Query 逐个加 tenant 过滤
- [ ] HomestayDocument 加 tenantId，ES 写入/查询改造
- [ ] Redis 缓存 key 加 tenant 前缀
- [ ] 11 个定时任务改为按租户遍历
- [ ] PaymentConfig 改为租户级
- [ ] 前端请求统一带 tenant 标识（Header 或子域名解析）
- [ ] 登录页按租户显示不同品牌
- [ ] 数据隔离测试覆盖所有 Repository

---

## 相关文档

- [[Homestay Backend 后端服务]]
- [[后端-架构总览]]
- [[后端-安全认证]]
- [[后端-实体关系图]]
- [[功能模块-认证权限]]
- [[功能模块-房源搜索]]
- [[功能模块-定价引擎]]
- [[Homestay 项目索引]]
