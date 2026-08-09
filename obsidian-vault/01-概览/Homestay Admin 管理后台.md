---
title: Homestay Admin 管理后台
date: 2026-04-27
tags:
  - homestay
  - overview
---

# Homestay Admin 管理后台

> [!info] 管理后台
> 基于 Vue 3 + TypeScript + Vite 构建的现代化管理后台

## 技术栈

- **框架**: Vue 3 (Composition API + `<script setup>`)
- **语言**: TypeScript
- **构建**: Vite
- **状态管理**: Pinia
- **路由**: Vue Router 4
- **自动化**: unplugin-auto-import + unplugin-vue-components

## 目录结构

```
homestay-admin/
├── src/
│   ├── api/              # API 接口定义
│   ├── assets/           # 静态资源
│   ├── components/       # 可复用组件
│   ├── config/           # 配置文件
│   ├── docs/             # 文档
│   ├── layout/           # 布局组件
│   ├── router/           # 路由配置
│   ├── stores/           # Pinia 状态管理
│   ├── types/            # TypeScript 类型定义
│   ├── utils/            # 工具函数
│   ├── views/            # 页面视图
│   ├── App.vue           # 根组件
│   └── main.ts           # 入口文件
├── public/               # 公共资源
├── auto-imports.d.ts     # 自动导入类型
├── components.d.ts       # 组件类型声明
├── vite.config.ts        # Vite 配置
└── tsconfig.json         # TypeScript 配置
```

## 核心功能

- ✅ 组件自动注册
- ✅ API 自动导入
- ✅ TypeScript 完整支持
- ✅ 快速热重载 (HMR)
- ✅ 生产优化构建

## 相关笔记

- [[Homestay 项目索引]]
- [[项目技术栈]]
- [[开发环境配置]]
