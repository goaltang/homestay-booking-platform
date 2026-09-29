# 首页重设计 · 交接说明（事实与位置，不含设计结论）

> 目标执行者：Opus 5（或任意有设计判断力的编码 AI）
> 仓库：`/mnt/d/homestay3/homestay-front`（Vue3 + TS + Vite + Element Plus + Pinia）
> 本包只回答三件事：**改哪里**、**环境怎么跑**、**哪些坑别人已经踩过**。
> **设计怎么做由你决定**——本包不提供目标版式、不规定区块增删、不给逐文件改动清单。

---

## 1. 任务

重设计 C 端首页：`homestay-front/src/views/Home.vue`（608 行，首页入口，区块大量拆到子组件）。

你可以自行决定：信息架构、区块顺序与取舍、视觉方向、组件拆分方式、移动端交互形态。改动范围限 `homestay-front/src/**`（后端、`homestay-admin` 默认不动）。

---

## 2. 仓库地图（只给位置，不给结论）

| 文件 | 是什么 |
| --- | --- |
| `src/views/Home.vue` | 首页入口；template 1–147、script 149–392、style 394–608 |
| `src/components/home/` | 首屏与营销区块：`HeroBanner`(含 `#search` 插槽)、`AnnouncementBar`、`TrustSection`、`MapCTA`、`HostCTA`、`RecentViewed`；另有 `PromoBanner.vue`／`TrustBadges.vue`（`grep` 显示当前无引用） |
| `src/components/SearchBar.vue` | 首页搜索组件（较复杂，含日期/房客面板） |
| `src/components/homestay/` | `HomestaySection`（区块外壳）、`HomestayCard`（卡片）、`HomestaySkeleton`（骨架）等 |
| `src/styles/tokens/{colors,typography,spacing,motion}.css` | **项目现有设计令牌**；`src/main.ts:11` → `src/styles/design-system.css` → `@import` 这四个文件 |
| `docs/design-system/MASTER.md` | 项目成文的 C 端设计规范（「Organic Warmth」），含配色语义、反模式清单、断点表、无障碍清单 |
| `obsidian-vault/` | 项目文档库（含前端相关设计与功能文档） |

**详细行号级地图**：`01-现状代码地图.md`（12 个区块的行号区间、各子组件 props/emit、现有 style 关键点、26 条实测问题）。
**历史结论**：`02-历史诊断摘录.md`（既有设计系统 token 明细、16 条历史问题、**已完成勿重做**清单、已否决方案、未找到记录的项）。

这两份是**查阅用**，按章节查即可，不必整篇读。

---

## 3. 边界（这些是约束，不是设计意见）

1. 技术栈不变：Vue3 组合式 API + `<script setup>` + TS；Element Plus 为自动导入（组件不需要手写 import）。
2. **配色复用项目已有 token**（主色陶土 `#d45f2e`、辅色鼠尾草 `#558555`、暖中性灰；见 `src/styles/tokens/colors.css`）。项目已有成文设计系统，重设计在它之上演进，不要另起一套配色。
3. 规范里明令禁止的做法请遵守：不用 Inter/Roboto/Arial/系统默认字体做正文、不用「紫色渐变 + 纯白背景」、不做全局统一大圆角、不以居中布局为主、不用 Emoji 当功能图标（依据 `docs/design-system/MASTER.md:30-39`）。
4. 已有改动不要重做：`02-历史诊断摘录.md` 第五节列了「已完成/已否决」项（例如若干移动端溢出修复已在 commit `750abea` 完成）。
5. 改完必须过 `npm run build`（含 `vue-tsc` 类型检查）；提交前 `npm run lint:check`。

---

## 4. 环境事实（重要，能省你很多时间）

- **后端起不来**：`8081`（Spring Boot）未运行，`9200`（ES）、`5672`（RabbitMQ）也不通，WSL 内没有 docker。MySQL `3306`、Redis `6379` 是 Windows 宿主在跑（WSL 镜像网络可直连），但只连数据库不足以让后端起来。
  → **不要试图起后端**，直接用下面的离线截图器。
- **dev server 已启动**：`homestay-front` 在 `http://localhost:5173`（若已停：`npm run dev`）。
- **离线截图器**：`tools/shot_home.py`，拦截全部 `/api/**` 返回假数据（主页数据、房源列表、推荐、类型、公告、图片），**不需要后端**即可出图：

  ```bash
  cd /mnt/d/homestay3/docs/首页重设计-交接包/tools
  python3 shot_home.py --tag before   # 4 个视口：1440 / 1280 / 820 / 390
  python3 shot_home.py --tag after --viewport mobile   # 只跑某一个视口
  ```

  它会输出全页图 + 首屏图到上一级 `shots/`，并打印：
  - `[溢出检查]`：每个视口的 `scrollWidth / clientWidth`（横向溢出会标记出来）
  - 未 mock 的接口、console error / warning
- **现状基线截图**（改前）：`shots/baseline-*.png`（桌面 1440 / 笔记本 1280 / 平板 820 / 手机 390，各有首屏与全页）。
- 图片素材：`tools/placeholders/*.jpg` 是离线占位图，仅用于截图，不要进业务代码。

---

## 5. 已知缺陷（别人踩过的坑，是否处理由你判断）

`01-现状代码地图.md` §4（26 条）与 `02-历史诊断摘录.md` §三（16 条）是实测清单，其中至少这几条会直接影响你的设计落地效果：

- 图片 URL 被拼成绝对地址 `http://localhost:8081/...`（`.env:1` + `src/utils/image.ts:108`）→ 手机/局域网访问时房源图全部裂开，**建议先确认这条**，否则截图与真机表现不可信。
- 首页容器宽度实际用了 `1280px`，而全站 token 是 `1200px`（`src/views/Home.vue:417` vs `src/styles/tokens/spacing.css:20`）。
- 首页目前只有 1 条媒体查询（`Home.vue:575`），而全仓实际用了 9 种断点值（768/480/992/576/900/720/600/1180/1400），与 token 里声明的 640/768/1024/1280 不一致。
- 加载骨架与真实卡片的列数规则不一致（`HomestaySection.vue:136-140` vs `HomestaySkeleton.vue:34,84,90`），会出现加载完成瞬间跳变。
- Hero 高度写死 480px / 380px（`HeroBanner.vue:121-130,232`）。
- 首页统计有写死的兜底数字（`Home.vue:174-180`、`HeroBanner.vue:80-82`），接口失败也会展示「10000 房源 / 50 城市 / 98% 好评」。
- 字体通过 CSS `@import` 引用 `fonts.googleapis.com`（`src/styles/tokens/typography.css:8`），中国大陆网络下会静默降级。

---

## 6. 需要用户拍板的事（别自己定，先问）

这几条会改变产品形态或带来外部依赖，动手前先问用户：

1. 字体的落地方式（项目规范要求 Playfair Display / Plus Jakarta Sans，但 Google Fonts 国内不可用：自托管子集 / 中文系统栈降级 / 换国内 CDN）。
2. 首页是否保留「猜你喜欢 / 推荐 / 热门」这套个性化推荐入口（涉及登录态与接口）。
3. 移动端是否引入底部固定导航栏。
4. 统计数字在接口失败时的表达（隐藏 / 占位符）。
5. 首页公告栏的去留。
6. 是否需要兼容深色模式（规范声称支持，实际只有空 `@media` 占位：`SearchBar.vue:849-852`）。

---

## 7. 建议的交付方式（你怎么想就怎么做，这只是流程建议）

1. 先看 `shots/baseline-*.png`，跑一次 `tools/shot_home.py --tag before` 确认基线。
2. 给出**你自己的**设计判断：现状问题、你选择的方向与取舍理由、哪些做了哪些明确不做。如果需要用户拍板，把问题一起列出来。
3. 再动手改；每改 1–2 个文件跑一次 `npm run build` + `shot_home.py --tag after`，用 `shots/after-*.png` 与基线对比。
4. 报告：改了哪些文件、你的设计决策、`after-*.png` 的对照结论、剩余风险。

---

## 8. 本包内容清单

| 文件 | 用途 |
| --- | --- |
| `README-交接说明.md` | 本文件：任务 + 仓库地图 + 边界 + 环境 + 已知缺陷 + 待拍板 |
| `01-现状代码地图.md` | 行号级现状地图（查阅用） |
| `02-历史诊断摘录.md` | 既有设计系统 + 历史结论 + 勿重做清单（查阅用） |
| `shots/baseline-*.png` | 改前基线截图（4 视口） |
| `tools/shot_home.py` | 离线截图器（mock API），改动前后对比用 |
| `参考-可选-主代理方案/` | **主代理做过的一个样例，非目标**：可无视；其中实测发现可引用 |
| `给Opus5的提示词.md` | 可直接贴给执行者的开场提示词 |
