---
title: Homestay Backend 后端服务
date: 2026-04-27
tags:
  - homestay
  - overview
---

# Homestay Backend 后端服务

> [!info] 后端服务
> 基于 Spring Boot 3.0.2 + Java 17 的民宿管理系统后端服务，提供 RESTful API、业务逻辑处理、数据持久化和安全认证能力。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Spring Boot** | 3.0.2 | 核心框架 |
| **Java** | 17 | 开发语言 |
| **Maven** | 3.8+ | 构建工具 |
| **Spring Data JPA** | - | ORM 框架 |
| **Spring Security** | - | 安全认证 |
| **MySQL** | - | 关系数据库 |
| **Redis** | - | 缓存 / 会话 / 分布式锁 |
| **Flyway** | - | 数据库迁移 |
| **JWT** | 0.12.3 | Token 认证 |
| **Lombok** | 1.18.24 | 代码简化 |
| **MapStruct** | 1.5.5 | DTO 映射 |

## 项目结构

```
homestay-backend/
├── src/main/java/com/homestay3/homestaybackend/
│   ├── config/           # 配置类（缓存、文件存储、HTTP 客户端等）
│   ├── controller/       # 控制器层（REST API 接口）
│   ├── service/          # 业务逻辑层
│   │   ├── impl/        # 服务实现
│   │   └── gateway/     # 支付网关（支付宝）
│   ├── repository/       # 数据访问层（Spring Data JPA）
│   ├── entity/           # JPA 实体类
│   ├── dto/              # 数据传输对象
│   ├── mapper/           # MapStruct 映射器
│   ├── model/            # 模型与枚举
│   ├── security/         # JWT 认证与安全
│   ├── exception/        # 全局异常处理
│   └── util/             # 工具类
├── src/main/resources/
│   ├── application.yml   # 主配置文件
│   └── db/migration/     # Flyway 数据库迁移脚本
└── uploads/              # 文件上传目录
```

## 核心模块

### 业务模块
- **房源管理** — 房源 CRUD、分组、图片、审核
- **订单管理** — 预订、状态流转、退款、争议
- **用户管理** — 注册登录、身份认证、收藏
- **评价管理** — 评价发布、审核、违规处理
- **设施管理** — 设施分类与房源关联
- **支付系统** — 支付宝集成、退款、对账
- **地图服务** — 高德地理编码、POI 搜索、周边房源

### 系统模块
- **安全认证** — Spring Security + JWT，角色权限控制
- **通知系统** — WebSocket 实时推送
- **系统配置** — 动态配置、公告、日志
- **统计分析** — 运营数据汇总

## 启动方式

```bash
# 1. 进入后端目录
cd homestay-backend

# 2. 安装依赖并编译
mvn clean install -DskipTests

# 3. 启动开发服务器
mvn spring-boot:run

# 或打包后运行
mvn clean package -DskipTests
java -jar target/homestay-backend-*.jar
```

默认服务地址: http://localhost:8080

## 相关笔记

- [[Homestay 项目索引]]
- [[后端-架构总览]]
- [[后端-Controller 接口清单]]
- [[后端-安全认证]]
- [[后端-支付系统]]
- [[后端-实体关系图]]
