# Homestay 前端设计系统 v1.0

> 生成日期：2026-04-29  
> 适用范围：`homestay-front` 全部页面与组件  
> 技术栈：Vue 3 + TypeScript + Vite  
> 核心原则：温暖 inviting × 精致 refined × 无 AI 塑料感

---

## 一、设计方向（Design Direction）

### 1.1 产品定位

- **类型**：民宿预订平台（C端消费）
- **用户画像**：25-40 岁，休闲度假为主，注重品质与体验
- **使用场景**：通勤浏览、周末计划、旅行决策

### 1.2 美学方向（Aesthetic Direction）

```
┌─────────────────────────────────────────────────────────────┐
│  风格定位：Organic Warmth（有机温暖）                          │
│                                                             │
│  ├─ 温暖但不廉价（避免过度饱和的橙红色）                        │
│  ├─ 精致但不冰冷（避免纯灰白极简）                             │
│  ├─ 有呼吸感（大量留白 + 柔和阴影）                            │
│  └─ 有记忆点（独特的字体搭配 + 非对称布局）                     │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 反模式（Anti-Patterns）—— 绝对不能出现

| ❌ 禁止项                             | 原因                   |
| ------------------------------------- | ---------------------- |
| Inter / Roboto / Arial / 系统默认字体 | AI 塑料感的头号元凶    |
| 紫色渐变 + 纯白背景                   | 过度使用的 AI 设计套路 |
| 统一大圆角（16px+ 全局）              | 缺乏层次感，显得幼稚   |
| 居中布局为主                          | 缺乏动感和视觉张力     |
| 纯色白背景无纹理                      | 显得廉价，缺乏氛围     |
| Emoji 作为功能图标                    | 不可控、不专业         |

---

## 二、色彩系统（Color System）

使用 CSS 变量定义，支持明暗模式切换。

### 2.1 语义色板

```css
:root {
  /* 主色：陶土赤褐（Terracotta）—— 温暖、自然、高端 */
  --color-primary-50: #fdf2ec;
  --color-primary-100: #f9e0d2;
  --color-primary-200: #f3c1a5;
  --color-primary-300: #ea9a6e;
  --color-primary-400: #e07a47;
  --color-primary-500: #d45f2e; /* 主行动色 */
  --color-primary-600: #c44e26;
  --color-primary-700: #a33d22;
  --color-primary-800: #833320;
  --color-primary-900: #6a2d1e;

  /* 辅色：鼠尾草绿（Sage）—— 平衡、放松、自然 */
  --color-secondary-50: #f4f7f4;
  --color-secondary-100: #e3ebe3;
  --color-secondary-200: #c7d8c7;
  --color-secondary-300: #9ebf9e;
  --color-secondary-400: #76a176;
  --color-secondary-500: #558555;
  --color-secondary-600: #426a42;
  --color-secondary-700: #365436;
  --color-secondary-800: #2d432d;
  --color-secondary-900: #263826;

  /* 中性色：暖灰（Warm Gray）—— 避免冷灰的冰冷感 */
  --color-neutral-0: #ffffff;
  --color-neutral-50: #faf9f7;
  --color-neutral-100: #f2f0ec;
  --color-neutral-200: #e6e2db;
  --color-neutral-300: #d1ccc2;
  --color-neutral-400: #b0a99c;
  --color-neutral-500: #8a8276;
  --color-neutral-600: #6b6459;
  --color-neutral-700: #524c43;
  --color-neutral-800: #3a3530;
  --color-neutral-900: #1f1c19;

  /* 功能色 */
  --color-success: #2d7a46;
  --color-warning: #c9872c;
  --color-error: #b53a2a;
  --color-info: #3a6ea5;

  /* 背景氛围色 */
  --color-surface: var(--color-neutral-0);
  --color-surface-elevated: #ffffff;
  --color-background: var(--color-neutral-50);
  --color-background-warm: #f7f4f0;
}
```

### 2.2 使用规则

| Token                   | 用途                         | 示例               |
| ----------------------- | ---------------------------- | ------------------ |
| `--color-primary-500`   | 主行动按钮、关键链接、选中态 | "搜索" 按钮        |
| `--color-primary-600`   | 按钮 hover / active          | 按钮按下           |
| `--color-secondary-500` | 辅助操作、标签、成功态       | "地图找房" 入口    |
| `--color-neutral-900`   | 主标题、正文                 | 页面标题           |
| `--color-neutral-500`   | 次要文字、占位符             | 输入框 placeholder |
| `--color-neutral-200`   | 分隔线、边框                 | 搜索栏内部分隔     |
| `--color-neutral-50`    | 卡片背景、悬浮态背景         | 搜索项 hover       |

### 2.3 对比度要求（WCAG AA）

- 正文文字 ≥ 4.5:1
- 大文字（18px+）≥ 3:1
- 图标 / 交互元素 ≥ 3:1

---

## 三、字体系统（Typography）

### 3.1 字体选择

```css
:root {
  /* Display / 标题：Playfair Display — 优雅衬线，有 editorial 杂志感 */
  --font-display: "Playfair Display", "Noto Serif SC", Georgia, serif;

  /* Body / 正文：Plus Jakarta Sans — 现代无衬线，温暖几何感 */
  --font-body: "Plus Jakarta Sans", "Noto Sans SC", -apple-system, sans-serif;

  /* Mono / 数据：JetBrains Mono — 清晰等宽，用于价格、日期 */
  --font-mono: "JetBrains Mono", "SF Mono", monospace;
}
```

> **为什么不选 Inter？** Inter 是 AI 设计的最常用字体，缺乏辨识度。Plus Jakarta Sans 有类似的几何骨架但更温暖，Playfair Display 则带来民宿平台需要的"家"的质感。

### 3.2 字体加载策略

- 使用 `font-display: swap` 避免 FOIT
- 仅预加载 Display 字体的 Regular 和 Bold 字重
- 中文字体使用系统字体栈降级（`Noto Serif SC`, `Noto Sans SC`）

### 3.3 字号阶梯

| Token          | 桌面端 | 移动端 | 字重 | 行高 | 用途                       |
| -------------- | ------ | ------ | ---- | ---- | -------------------------- |
| `text-hero`    | 48px   | 32px   | 700  | 1.1  | 首页主标题                 |
| `text-h1`      | 36px   | 28px   | 700  | 1.2  | 页面标题                   |
| `text-h2`      | 28px   | 22px   | 600  | 1.3  | 区块标题                   |
| `text-h3`      | 22px   | 18px   | 600  | 1.3  | 卡片标题                   |
| `text-body-lg` | 18px   | 16px   | 400  | 1.6  | 引导文字                   |
| `text-body`    | 16px   | 15px   | 400  | 1.6  | 正文                       |
| `text-body-sm` | 14px   | 13px   | 400  | 1.5  | 次要文字                   |
| `text-caption` | 12px   | 11px   | 500  | 1.4  | 标签、辅助文字             |
| `text-price`   | 24px   | 20px   | 700  | 1.2  | 价格展示（使用 font-mono） |

### 3.4 排版规则

- **正文最小 16px**（移动端避免 iOS 自动缩放）
- **字间距**：body 使用默认 tracking，display 字体可略收紧（-0.02em）
- **段落宽度**：桌面最多 65 字符/行，移动端 35-50 字符/行

---

## 四、间距系统（Spacing）

基于 **4pt / 8dp 增量系统**：

```css
:root {
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-10: 40px;
  --space-12: 48px;
  --space-16: 64px;
  --space-20: 80px;
  --space-24: 96px;
}
```

### 使用场景

| 场景             | Token                                        | 说明             |
| ---------------- | -------------------------------------------- | ---------------- |
| 组件内部 padding | `--space-3` ~ `--space-5`                    | 按钮、输入框     |
| 卡片内部 padding | `--space-4` ~ `--space-6`                    | 房源卡片         |
| 组件之间 gap     | `--space-4` ~ `--space-6`                    | 表单项之间       |
| 区块间距         | `--space-10` ~ `--space-16`                  | 页面区块分隔     |
| 页面边距         | `--space-4` (mobile) / `--space-8` (desktop) | 容器左右 padding |

---

## 五、圆角与阴影（Radius & Shadow）

### 5.1 圆角阶梯

```css
:root {
  --radius-none: 0px;
  --radius-sm: 4px; /* 标签、小按钮 */
  --radius-md: 8px; /* 输入框、卡片 */
  --radius-lg: 12px; /* 大卡片、面板 */
  --radius-xl: 16px; /* 模态框、搜索面板 */
  --radius-2xl: 24px; /* 搜索栏外壳 */
  --radius-full: 9999px; /* 圆形按钮、头像 */
}
```

### 5.2 阴影阶梯

```css
:root {
  --shadow-sm: 0 1px 2px rgba(31, 28, 25, 0.05);
  --shadow-md: 0 4px 12px rgba(31, 28, 25, 0.08);
  --shadow-lg: 0 8px 24px rgba(31, 28, 25, 0.1);
  --shadow-xl: 0 16px 48px rgba(31, 28, 25, 0.12);
  --shadow-focus: 0 0 0 3px rgba(212, 95, 46, 0.2);
}
```

> 阴影使用 **暖黑色**（#1f1c19）而非纯黑，与整体暖色调一致。

---

## 六、动效系统（Motion）

### 6.1 时间规范

| 类型             | Duration       | Easing                              | 用途                |
| ---------------- | -------------- | ----------------------------------- | ------------------- |
| Micro（微交互）  | 150ms          | `ease-out`                          | hover、press、focus |
| Standard（标准） | 200-250ms      | `cubic-bezier(0.4, 0, 0.2, 1)`      | 状态切换、展开收起  |
| Complex（复杂）  | 300-400ms      | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 面板展开、页面过渡  |
| Stagger（交错）  | 30-50ms / item | `ease-out`                          | 列表入场、卡片网格  |

### 6.2 核心原则

- **只用 `transform` 和 `opacity`**，绝不动画 `width/height/top/left`
- **入场动画比退场慢**（exit ≈ 60-70% of enter duration）
- **每次视图最多动画 1-2 个关键元素**
- **尊重 `prefers-reduced-motion`**：减少/禁用动画
- **可中断**：用户点击可立即打断正在进行的动画

### 6.3 搜索栏专用动效

```css
/* 搜索项 hover */
.search-item {
  transition:
    background-color 150ms ease-out,
    transform 200ms cubic-bezier(0.34, 1.56, 0.64, 1);
}
.search-item:hover {
  transform: translateY(-2px);
}

/* 搜索面板展开 */
.search-panel {
  transition:
    opacity 200ms ease-out,
    transform 300ms cubic-bezier(0.34, 1.56, 0.64, 1);
  transform-origin: top center;
}
.search-panel-enter {
  opacity: 0;
  transform: scale(0.96) translateY(-8px);
}
.search-panel-enter-active {
  opacity: 1;
  transform: scale(1) translateY(0);
}

/* 搜索按钮 press */
.search-button:active {
  transform: scale(0.96);
  transition-duration: 100ms;
}
```

---

## 七、布局与响应式（Layout & Responsive）

### 7.1 断点系统

```css
:root {
  --breakpoint-sm: 640px; /* 大手机 */
  --breakpoint-md: 768px; /* 平板竖屏 */
  --breakpoint-lg: 1024px; /* 平板横屏 / 小桌面 */
  --breakpoint-xl: 1280px; /* 标准桌面 */
  --breakpoint-2xl: 1536px; /* 大桌面 */
}
```

### 7.2 容器规则

- 最大内容宽度：`1200px`（`max-w-6xl`）
- 移动端边距：`16px`（`--space-4`）
- 桌面端边距：`24px` ~ `32px`（`--space-6` ~ `--space-8`）
- **禁用页面缩放**：`width=device-width, initial-scale=1`

### 7.3 Mobile-First 原则

- 先设计移动端，再向上扩展
- 核心内容优先展示，次要内容可折叠
- 触摸目标最小 **44×44px**（iOS）/ **48×48dp**（Android）

---

## 八、组件规范（Component Guidelines）

### 8.1 按钮（Button）

| 变体      | 背景                    | 文字                  | Hover                                      | 圆角          | 阴影          |
| --------- | ----------------------- | --------------------- | ------------------------------------------ | ------------- | ------------- |
| Primary   | `--color-primary-500`   | white                 | `--color-primary-600` + `translateY(-1px)` | `--radius-md` | `--shadow-md` |
| Secondary | `--color-secondary-500` | white                 | `--color-secondary-600`                    | `--radius-md` | `--shadow-sm` |
| Ghost     | transparent             | `--color-neutral-700` | `--color-neutral-100`                      | `--radius-md` | none          |
| Text      | transparent             | `--color-primary-500` | underline                                  | none          | none          |

### 8.2 输入框（Input）

- 高度：最小 44px（移动端）/ 40px（桌面端）
- 边框：`1px solid --color-neutral-200`，focus 时 `--color-primary-500`
- 背景：`--color-surface`
- 圆角：`--radius-md`
- **必须有可见 label**，placeholder 仅作辅助
- Focus ring：`--shadow-focus`（3px 主色外发光）

### 8.3 卡片（Card）

- 背景：`--color-surface-elevated`
- 圆角：`--radius-lg`
- 阴影：`--shadow-md`
- Hover：`--shadow-lg` + `translateY(-4px)`，transition 200ms
- 内部 padding：`--space-5`

### 8.4 搜索栏专用规范（SearchBar）

这是本设计系统的第一个落地组件，后续其他页面搜索可复用。

#### 结构

```
┌────────────────────────────────────────────────────────────┐
│  [目的地] │ [关键词] │ [入住→退房] │ [房客] │ [🔍 搜索]  │
│  你要去哪？  房源名/特色   12月1日→3日    2位房客            │
└────────────────────────────────────────────────────────────┘
```

#### 视觉规范

- 外壳：白色背景 + `--shadow-lg` + `--radius-2xl`
- 内部分隔：`1px solid --color-neutral-200`，高度 32px
- 搜索项 padding：`--space-4` ~ `--space-5`
- 搜索项 hover：`--color-neutral-50` 背景，transition 150ms
- 搜索按钮：`--radius-full` 圆形，`--color-primary-500` 背景，hover 放大 1.04

#### 交互规范

- 点击搜索项 → 该项获得 `--shadow-focus` 聚焦环
- 目的地输入 → 展开建议面板（热门目的地 + 最近搜索）
- 日期选择 → 联动校验（退房必须晚于入住）
- 房客选择 → Popover 面板，+- 按钮控制人数
- 搜索按钮 → `disabled` 时显示 tooltip 提示

#### 移动端适配（< 768px）

```
┌────────────────────────────┐
│  [目的地]                    │
│  你要去哪里？                │
├────────────────────────────┤
│  [入住]        [退房]       │
│  12月1日      12月3日       │
├────────────────────────────┤
│  [房客]        [关键词]     │
│  2位房客      海景房         │
├────────────────────────────┤
│  [      🔍 搜索房源      ]  │
└────────────────────────────┘
```

- 垂直堆叠，每项占满宽度
- 日期并排（flex: 1 1 50%）
- 搜索按钮全宽，圆角 `--radius-lg`

---

## 九、无障碍规范（Accessibility）

### 9.1 必须遵守

- [ ] 所有交互元素有可见 focus ring（`--shadow-focus`）
- [ ] 图标按钮有 `aria-label`
- [ ] 表单字段有关联 `<label>` 或使用 `aria-labelledby`
- [ ] 颜色不是唯一信息载体（错误状态需配图标 + 文字）
- [ ] 支持键盘导航（Tab 顺序与视觉顺序一致）
- [ ] 支持 `prefers-reduced-motion`（减少动画）

### 9.2 搜索栏专项

- [ ] 级联选择器支持键盘导航（↑↓ 选择，Enter 确认，Esc 关闭）
- [ ] 日期选择器支持键盘输入（直接输入 YYYY-MM-DD）
- [ ] 搜索按钮 `disabled` 时，`aria-disabled="true"` + tooltip 说明原因
- [ ] 建议面板使用 `role="listbox"`，选项使用 `role="option"`

---

## 十、文件组织

```
homestay-front/
├── docs/
│   └── design-system/
│       ├── MASTER.md          ← 本文件（全局规范）
│       ├── pages/
│       │   └── search.md      ← 搜索页局部覆盖（如有）
│       └── tokens/
│           ├── colors.css     ← CSS 变量定义
│           ├── typography.css ← 字体变量
│           ├── spacing.css    ← 间距变量
│           └── motion.css     ← 动效变量
├── src/
│   ├── styles/
│   │   ├── design-system.css  ← 引入全部 token
│   │   └── global.css         ← 全局样式覆盖
│   └── components/
│       └── search/
│           ├── SearchBar.vue      ← 搜索栏外壳
│           ├── SearchField.vue    ← 单个搜索项
│           ├── DateRangeField.vue ← 日期范围
│           ├── GuestSelector.vue  ← 房客选择器
│           └── SearchPanel.vue    ← 展开搜索面板
```

---

## 十一、与其他模块的关系

| 模块                      | 引用方式                               | 说明                                           |
| ------------------------- | -------------------------------------- | ---------------------------------------------- |
| 新页面/组件               | 直接 import `design-system.css`        | 自动获得全部 token                             |
| 局部风格差异              | 创建 `pages/xxx.md` 覆盖               | 如地图搜索页可能更暗                           |
| 管理后台 `homestay-admin` | 可共用 `colors.css` + `typography.css` | 后台通常更简洁，可裁剪                         |
| Element Plus 覆盖         | 在 `global.css` 中映射 token           | `--el-color-primary: var(--color-primary-500)` |

---

> **设计不是一次性的。** 每次新增页面时，先读 MASTER.md，如需局部调整再创建 page override。保持全局一致性，允许页面级差异。
