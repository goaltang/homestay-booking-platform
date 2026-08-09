---
title: 前端 useCrud 使用指南
date: 2026-04-28
tags:
  - frontend
  - vue
  - crud
  - composable
---

# 前端 useCrud 使用指南

> [!info] 定位
> `useCrud` 是一个 Vue 3 组合式函数（Composable），用于封装管理后台中**最常见的列表+增删改查逻辑**。
>
> 目标：把每个 CRUD 页面中重复的 `loading`、`tableData`、`dialogVisible`、`handleAdd`、`handleEdit`、`handleDelete`、`handleSubmit` 等逻辑抽出来，让页面专注于**模板和特殊业务**。

---

## 一、快速开始

### 1.1 最简单的 CRUD（只需 20 行 script）

```vue
<script setup lang="ts">
import { useCrud } from '@/composables/useCrud'
import { getItems, createItem, updateItem, deleteItem } from '@/api/item'

const {
  loading, tableData, query, pagination,
  dialogVisible, editMode, formRef, form, currentId,
  getList, handleAdd, handleEdit, handleDelete, handleSubmit,
  handlePageChange, handleSizeChange,
} = useCrud({
  listApi: getItems,
  createApi: createItem,
  updateApi: updateItem,
  deleteApi: deleteItem,
  defaultQuery: { page: 0, size: 20 },
  defaultForm: { name: '', status: 'ACTIVE' },
  rules: {
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  },
})

// 初始化加载
getList()
</script>
```

模板部分和原来一样写，只是 `script` 里的 boilerplate 没了。

---

## 二、API 参考

### 2.1 输入参数

```typescript
interface UseCrudOptions<T, Q> {
  /** 列表查询 API（必填） */
  listApi: (query: Q & { page?: number; size?: number }) => Promise<any>

  /** 新增 API（可选，没有则隐藏新增按钮逻辑） */
  createApi?: (data: Partial<T>) => Promise<any>

  /** 更新 API（可选，签名：(id, data) => Promise） */
  updateApi?: (id: number | string, data: Partial<T>) => Promise<any>

  /** 删除 API（可选） */
  deleteApi?: (id: number | string) => Promise<any>

  /** 默认查询参数 */
  defaultQuery?: Q

  /** 默认表单数据（打开新增弹窗时重置用） */
  defaultForm?: Partial<T>

  /** 表单校验规则 */
  rules?: FormRules

  /** 是否启用分页（默认 true） */
  pagination?: boolean

  /** 提交成功后的回调 */
  afterSubmit?: () => void

  /** 删除前确认文案生成函数 */
  deleteConfirmText?: (row: T) => string

  /** 主键字段名（默认 'id'） */
  idKey?: keyof T
}
```

### 2.2 返回值

| 名称 | 类型 | 说明 |
|------|------|------|
| `loading` | `Ref<boolean>` | 列表加载状态 |
| `tableData` | `Ref<T[]>` | 表格数据 |
| `query` | `Reactive<Q>` | 查询参数（响应式） |
| `pagination` | `Reactive<{page, size, total}>` | 分页状态 |
| `dialogVisible` | `Ref<boolean>` | 弹窗显示状态 |
| `editMode` | `Ref<boolean>` | 是否编辑模式 |
| `formRef` | `Ref<FormInstance>` | 表单 ref（用于校验） |
| `form` | `Reactive<Partial<T>>` | 表单数据 |
| `currentId` | `Ref<number \| string \| null>` | 当前编辑记录的主键 |
| `getList` | `() => Promise<void>` | 获取列表 |
| `handleAdd` | `() => void` | 打开新增弹窗 |
| `handleEdit` | `(row: T) => void` | 打开编辑弹窗（直接复制 row） |
| `handleDelete` | `(row: T) => Promise<void>` | 删除（含确认框） |
| `handleSubmit` | `() => Promise<void>` | 表单提交（自动判断新增/编辑） |
| `handlePageChange` | `(page: number) => void` | 页码变化 |
| `handleSizeChange` | `(size: number) => void` | 每页条数变化 |
| `resetForm` | `() => void` | 重置表单 |

---

## 三、典型场景示例

### 场景 A：标准 CRUD（PricingRuleManage）

API 直接返回标准分页格式 `{ content: [...], totalElements: 10 }`，无需包装。

```typescript
const { loading, tableData, query, pagination, dialogVisible, editMode, formRef, form, currentId, getList, handleAdd, handleEdit, handleDelete, handleSubmit, handlePageChange } = useCrud({
  listApi: getPricingRules,
  createApi: createPricingRule,
  updateApi: updatePricingRule,
  deleteApi: deletePricingRule,
  defaultQuery: { page: 0, size: 20, ruleType: '' },
  defaultForm: { name: '', scopeType: 'GLOBAL', ruleType: 'WEEKEND', ... },
  rules: { name: [{ required: true, message: '请输入规则名称' }] },
})
```

### 场景 B：响应带 `{ success, data, total }` 包装（Announcement / Config）

后端返回格式不标准，包一层适配：

```typescript
const wrappedListApi = async (params) => {
  const res = await getAdminAnnouncementsApi(params)
  if (!res.success) throw new Error(res.message)
  return {
    content: res.data || [],
    totalElements: res.total || 0,
  }
}

const wrappedCreateApi = async (data) => {
  const res = await createAnnouncementApi(data, userId, username)
  if (!res.success) throw new Error(res.message)
  return res
}

useCrud({
  listApi: wrappedListApi,
  createApi: wrappedCreateApi,
  // ...
})
```

**核心原则**：`useCrud` 只认标准格式 `{ content: [], totalElements: n }`，不认识的格式在组件内包一层转一下。

### 场景 C：编辑前需要加载详情（Announcement）

默认 `handleEdit` 是直接复制表格行数据打开弹窗。如果需要先调详情接口：

```typescript
const { handleEdit: _rawEdit, editMode, form, dialogVisible, ... } = useCrud({ /* ... */ })

const handleEditWithDetail = async (row) => {
  const res = await getAnnouncementById(row.id)
  if (!res.success) return ElMessage.error('获取详情失败')
  
  editMode.value = true
  Object.assign(form, res.data)
  dialogVisible.value = true
}
```

模板里用 `@click="handleEditWithDetail(scope.row)"` 代替原来的 `handleEdit`。

### 场景 D：前端筛选不分页（Config）

```typescript
const allConfigs = ref([])

const wrappedListApi = async () => {
  const res = await getAllConfigsApi()
  allConfigs.value = res.data || []
  return { content: res.data || [], totalElements: res.data?.length || 0 }
}

const { tableData, query, getList, ... } = useCrud({
  listApi: wrappedListApi,
  pagination: false,  // ← 关闭分页
  // ...
})

// 前端筛选逻辑
const applyFilter = () => {
  let filtered = [...allConfigs.value]
  if (query.category) filtered = filtered.filter(c => c.category === query.category)
  if (query.keyword) filtered = filtered.filter(c => c.name.includes(query.keyword))
  tableData.value = filtered
}
```

### 场景 E：有日期范围等特殊字段（CouponTemplateList）

`useCrud` 管理不了所有业务字段，日期范围、文件上传等**在组件内单独维护**：

```typescript
const dateRange = ref<[Date, Date] | null>(null)

const { handleAdd: _rawAdd, handleEdit: _rawEdit, ... } = useCrud({ /* ... */ })

const handleAddClean = () => {
  _rawAdd()
  dateRange.value = null
}

const handleEditWithDate = (row) => {
  _rawEdit(row)
  dateRange.value = row.startTime ? [new Date(row.startTime), new Date(row.endTime)] : null
}

// 提交时拼接日期
const wrappedCreateApi = async (data) => {
  const payload = { ...data }
  if (dateRange.value) {
    payload.validStartAt = dateRange.value[0].toISOString()
    payload.validEndAt = dateRange.value[1].toISOString()
  }
  return createCouponTemplate(payload)
}
```

### 场景 F：纯列表 + 删除，无新增编辑（review/list）

```typescript
const { loading, tableData, query, pagination, getList, handleDelete, handlePageChange, handleSizeChange } = useCrud({
  listApi: wrappedListApi,
  deleteApi: deleteReview,
  // 不传 createApi / updateApi
})
```

详情弹窗用独立的 `detailVisible` + `currentReview`，不走 `useCrud` 的 `dialogVisible`。

---

## 四、迁移 checklist

把一个旧页面改成 `useCrud`，按这个顺序：

1. [ ] **导入 `useCrud`**：`import { useCrud } from '@/composables/useCrud'`
2. [ ] **检查 API 签名**：是否需要包装 `listApi` / `createApi` / `updateApi` / `deleteApi`
3. [ ] **提取 `defaultQuery`**：把原来的 `query` / `searchForm` 搬过来
4. [ ] **提取 `defaultForm`**：把新增时的重置字段搬过来
5. [ ] **提取 `rules`**：把 `el-form` 的 rules 搬过来
6. [ ] **替换 script 逻辑**：删掉原来的 `getList`、`handleAdd`、`handleEdit`、`handleDelete`、`handleSubmit`、`handlePageChange` 等
7. [ ] **保留特殊逻辑**：日期范围、文件上传、行内 Switch、额外按钮等留在组件内
8. [ ] **检查模板绑定**：确保 `pagination.page` / `pagination.size` / `pagination.total` 绑定正确
9. [ ] **测试**：新增、编辑、删除、分页、搜索 都点一遍

---

## 五、常见问题

### Q1：页面没有新增功能，只有列表和删除，能用吗？

可以。只传 `listApi` 和 `deleteApi`，`createApi` / `updateApi` 不传。弹窗用独立的 ref 管理。

### Q2：后端分页从 1 开始，useCrud 从 0 开始，怎么办？

在 `listApi` 包装层里转换：

```typescript
const wrappedListApi = async (params) => {
  const res = await getItems({ ...params, page: params.page + 1 })
  return { content: res.data, totalElements: res.total }
}
```

### Q3：表单里有下拉框联动（选 A 后 B 的选项变化），useCrud 能处理吗？

`useCrud` 不管联动逻辑。联动逻辑写在组件内，`useCrud` 只管提交时把 `form` 的数据发出去。

### Q4：删除后如果当前页没数据了，想自动回到上一页，怎么办？

`useCrud` 的默认 `handleDelete` 不会处理这个。需要覆盖：

```typescript
const { handleDelete: _rawDelete, pagination, getList } = useCrud({ /* ... */ })

const handleDeleteSmart = async (row) => {
  await _rawDelete(row)
  if (tableData.value.length === 0 && pagination.page > 0) {
    pagination.page -= 1
    await getList()
  }
}
```

### Q5：表格行里有 Switch（如 review 的可见性切换），useCrud 支持吗？

行内 Switch 不属于 CRUD 弹窗逻辑，保留在组件内自己写。`useCrud` 不管这个。

---

## 六、已改造的参考页面

| 页面 | 路径 | 示范场景 |
|------|------|---------|
| 价格规则 | `views/pricing/PricingRuleManage.vue` | 标准 CRUD + 日期范围 |
| 公告管理 | `views/system/Announcement.vue` | 响应包装 + 编辑前加载详情 |
| 系统配置 | `views/system/Config.vue` | 前端筛选 + 不分页 |
| 优惠券模板 | `views/marketing/CouponTemplateList.vue` | 搜索表单 + 日期范围 |
| 评价列表 | `views/review/list.vue` | 纯列表 + 行内操作 |

看不懂的地方，直接打开这些文件看实际代码，比看文档更直观。

---

## 相关文档

- [[方案-定价引擎]]
- [[功能模块-定价引擎]]
- [[Homestay Admin 管理后台]]
