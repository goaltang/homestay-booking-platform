---
title: Git 工作流-个人项目最佳实践
date: 2026-04-29
tags:
  - homestay
  - git
  - workflow
---

# Git 工作流-个人项目最佳实践

> 针对 Homestay 单人开发场景的 Git 使用规范与救急指南。避免 stash 滥用、commit 混乱、代码丢失等问题。

## 一、核心原则

| 原则 | 说明 |
|------|------|
| **临时分支代替 stash** | 改到一半要切走时，开 `wip/xxx` 分支 commit，不要 stash |
| **commit 可以随意，但 push 前整理** | 个人项目 commit 信息不讲究，但合并到 master 前建议 squash |
| **master 始终保持可运行** | 半成品代码留在 wip/feat 分支，master 随时能打包部署 |
| **reflog 是最后保险** | 任何误操作先想 reflog，基本都能救 |

## 二、分支模型（极简版）

```text
master/main          ← 始终可运行、可部署
  │
  ├─ wip/今天的事     ← 临时工作区，随时 commit，做完删
  │
  ├─ feat/新功能      ← 较大的新功能，做完 merge 回 master
  │
  └─ fix/线上问题      ← 修 bug，做完 merge 回 master
```

**不需要 Git Flow，不需要 develop 分支。** 一个人干活，越简单越好。

## 三、日常操作流

### 3.1 开干新功能

```bash
git checkout -b feat/定价引擎
# 写代码...
git add .
git commit -m "WIP: 定价引擎基础结构"
# 继续写...
git add .
git commit -m "WIP: 定价规则 CRUD"
```

### 3.2 临时被叫去修别的

```bash
# 当前在 feat/定价引擎，改到一半
git add .                          # 先把当前改动收起来
git commit -m "WIP: 定价引擎半成品，别删"
git checkout master
git checkout -b fix/支付bug
# 修 bug...
git add . && git commit -m "fix: 修复支付回调异常"
git checkout master
git merge fix/支付bug
git branch -d fix/支付bug

# 回来继续写定价引擎
git checkout feat/定价引擎
```

### 3.3 功能做完，整理历史

```bash
git checkout feat/定价引擎
# 把多个 WIP commit 合成一个干净的 commit
git rebase -i master
# 在编辑器里把 pick 改成 squash（除了第一个）

# 合回 master
git checkout master
git merge feat/定价引擎
git branch -d feat/定价引擎
```

## 四、别名配置（必设）

```bash
git config --global alias.st status
git config --global alias.co checkout
git config --global alias.br branch
git config --global alias.ci commit
git config --global alias.lg "log --oneline --graph --all"
git config --global alias.last "log -1 HEAD"
```

## 五、救急场景速查

| 场景 | 命令 | 说明 |
|------|------|------|
| 改乱了想放弃所有修改 | `git restore .` | 回到上次 commit 状态 |
| 某个文件改错了 | `git restore 文件路径` | 只恢复这一个文件 |
| commit 完发现漏了文件 | `git add 文件 && git commit --amend --no-edit` | 塞进上一个 commit |
| commit 信息写错了 | `git commit --amend -m "新信息"` | 改信息 |
| 误操作 reset --hard | `git reflog` → `git reset --hard HEAD@{1}` | 时光机回退 |
| stash 放太久冲突爆炸 | `git stash branch stash-恢复` | 从 stash 开新分支处理 |
| 想看文件两天前长什么样 | `git show 83fbeb4:文件路径` | 指定 commit 查看 |

## 六、本项目踩坑记录

### 6.1 stash 滥用事件

| 时间 | 事件 | 教训 |
|------|------|------|
| 2026-04-27 | 在 `83fbeb4` stash 了大量改动 | stash 基于老 commit，后续又走了 26 个 commit |
| 2026-04-29 | 恢复 stash，28 个文件冲突 | **个人开发不要用 stash 放超过一天** |
| 处理结果 | 分析发现 HEAD 版本更新更完整 | 直接丢弃 stash，用 `git stash drop` |

### 6.2 推荐做法对比

| 做法 | 优点 | 缺点 |
|------|------|------|
| `git stash` | 快捷 | 无历史、易忘、恢复时冲突爆炸 |
| `git worktree` | 完全隔离 | 目录切换麻烦 |
| **wip 分支 + commit（推荐）** | 有历史、随时查看、不会丢 | 多一个分支 |

## 七、验证清单

- [ ] 不使用 stash 存放超过半天的改动
- [ ] master 分支始终能编译/构建通过
- [ ] 设置了常用 alias
- [ ] 知道 `git reflog` 能救回误删的代码

## 相关笔记

- [[Git 手册-常用命令与救急技巧]]
- [[Homestay 项目索引]]
