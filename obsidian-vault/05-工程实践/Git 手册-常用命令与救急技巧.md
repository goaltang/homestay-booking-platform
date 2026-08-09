---
title: Git 手册-常用命令与救急技巧
date: 2026-04-29
tags:
  - git
  - reference
---

# Git 手册-常用命令与救急技巧

> Git 操作速查手册。按场景分类，遇到问题时直接定位。

---

## 一、日常提交

| 命令 | 作用 |
|------|------|
| `git add 文件` | 将文件改动加入暂存区 |
| `git add .` | 加入当前目录所有改动 |
| `git add -p` | 交互式选择部分改动加入暂存区 |
| `git commit -m "描述"` | 提交暂存区的改动 |
| `git commit --amend` | 修改上一次提交（改信息或加文件） |
| `git commit --amend --no-edit` | 不改信息，只把新 add 的文件塞进上次提交 |
| `git status` / `git st` | 查看工作区状态 |
| `git diff` | 查看工作区与暂存区的差异 |
| `git diff --staged` | 查看暂存区与上次提交的差异 |

---

## 二、分支操作

| 命令 | 作用 |
|------|------|
| `git branch` | 列出本地分支 |
| `git branch -a` | 列出所有分支（含远程） |
| `git branch 新分支名` | 创建分支 |
| `git checkout 分支名` | 切换分支 |
| `git checkout -b 新分支名` | 创建并切换分支 |
| `git merge 分支名` | 将指定分支合并到当前分支 |
| `git branch -d 分支名` | 删除已合并的分支 |
| `git branch -D 分支名` | 强制删除分支 |

---

## 三、历史查看

| 命令 | 作用 |
|------|------|
| `git log --oneline` | 简洁历史（一行一个 commit） |
| `git log --oneline --graph --all` | 图形化查看所有分支历史 |
| `git log -p -- 文件路径` | 查看某个文件的修改历史 |
| `git show commit哈希` | 查看某个 commit 的详细改动 |
| `git show commit哈希:文件路径` | 查看某个 commit 时文件的内容 |
| `git blame 文件路径` | 查看每行代码最后是谁改的 |

---

## 四、撤销与回退

| 场景 | 命令 |
|------|------|
| 放弃工作区某文件的修改 | `git restore 文件路径` |
| 放弃工作区所有修改 | `git restore .` |
| 把暂存区的文件撤出来（unstage） | `git restore --staged 文件路径` |
| 撤销最近一次 commit（保留改动） | `git reset HEAD~1 --soft` |
| 撤销最近一次 commit（丢弃改动） | `git reset HEAD~1 --hard` |
| 撤销已 push 的 commit（安全方式） | `git revert HEAD` |
| 回到某个历史版本（危险） | `git reset --hard commit哈希` |

---

## 五、stash（临时保存）

| 命令 | 作用 |
|------|------|
| `git stash` | 临时保存当前所有改动 |
| `git stash push -m "备注"` | 带备注保存 |
| `git stash push 文件路径` | 只保存某个文件的改动 |
| `git stash -u` | 同时保存未跟踪的新文件 |
| `git stash list` | 查看所有 stash |
| `git stash show -p` | 查看最近 stash 的详细改动 |
| `git stash apply` | 恢复最近 stash（不删除） |
| `git stash pop` | 恢复最近 stash（并删除） |
| `git stash drop` | 删除最近 stash |
| `git stash branch 分支名` | 从 stash 创建新分支 |

> ⚠️ **建议**：个人开发尽量不要让 stash 存放超过一天，改用临时分支更安全。

---

## 六、远程操作

| 命令 | 作用 |
|------|------|
| `git clone 地址` | 克隆远程仓库 |
| `git remote -v` | 查看远程仓库地址 |
| `git fetch` | 拉取远程分支信息（不合并） |
| `git pull` | 拉取并合并远程代码 |
| `git push` | 推送当前分支到远程 |
| `git push -u origin 分支名` | 推送并关联远程分支 |
| `git push origin --delete 分支名` | 删除远程分支 |

---

## 七、历史整理（rebase）

| 命令 | 作用 |
|------|------|
| `git rebase -i HEAD~3` | 交互式整理最近 3 个 commit |
| `git rebase master` | 将当前分支变基到 master 最新位置 |
| `git rebase --continue` | 解决冲突后继续 rebase |
| `git rebase --abort` | 放弃 rebase |
| `git cherry-pick commit哈希` | 把某个 commit 搬到当前分支 |

**交互式 rebase 常用指令**：

| 指令 | 作用 |
|------|------|
| `pick` | 保留该 commit |
| `reword` | 保留但修改提交信息 |
| `squash` | 合并到上一个 commit |
| `drop` | 删除该 commit |

---

## 八、救急神器

### 8.1 reflog（操作日志）

```bash
git reflog                    # 查看所有操作历史
git reset --hard HEAD@{3}     # 回到第 3 步前的状态
git reset --hard 哈希值        # 回到指定状态
```

**能救回的场景**：误 reset --hard、误 rebase、误 stash drop、误 branch -D。

### 8.2 bisect（二分查找 bug）

```bash
git bisect start
git bisect bad                # 标记当前版本有 bug
git bisect good commit哈希     # 标记某个旧版本正常
# Git 会自动 checkout 中间版本，你测试后标记 good/bad
# 重复直到定位到引入 bug 的 commit
git bisect reset              # 结束查找
```

### 8.3 worktree（多工作区）

```bash
git worktree add ../目录名 分支名    # 在当前目录旁创建新工作区
git worktree list                     # 查看所有工作区
git worktree remove ../目录名         # 删除工作区
```

适合临时被叫去修 bug，又不想动当前工作区的情况。

---

## 九、实用别名推荐

```bash
# 基础缩写
git config --global alias.st status
git config --global alias.co checkout
git config --global alias.br branch
git config --global alias.ci commit

# 历史查看
git config --global alias.lg "log --oneline --graph --all"
git config --global alias.last "log -1 HEAD"
git config --global alias.unstage "restore --staged"

# 快捷操作
git config --global alias.undo "reset HEAD~1 --soft"
git config --global alias.amend "commit --amend --no-edit"
```

---

## 十、常见错误处理

| 错误提示 | 原因 | 解决 |
|---------|------|------|
| `error: Your local changes...` | pull 时本地有未提交改动 | 先 stash 或 commit 再 pull |
| `CONFLICT (content): Merge conflict` | 合并冲突 | 手动解决冲突文件 → `git add .` → `git commit` |
| `fatal: not a git repository` | 不在 git 仓库目录内 | `cd` 到项目根目录 |
| `fatal: refusing to merge unrelated histories` | 两个仓库没有共同历史 | `git pull origin master --allow-unrelated-histories` |
| `error: failed to push some refs` | 远程有本地没有的更新 | 先 `git pull` 再 `git push` |

---

## 相关笔记

- [[Git 工作流-个人项目最佳实践]]
- [[Homestay 项目索引]]
