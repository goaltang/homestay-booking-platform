# 订单状态筛选功能测试

## 修复内容

### 问题原因

原来的代码将新的状态分类（如 `NEED_PAYMENT`、`IN_PROGRESS` 等）直接发送给后端 API，但后端不认识这些新的状态名称，导致除了"全部订单"外，其他筛选都无效。

### 修复方案

1. **前端筛选**：修改为在前端进行状态筛选，而不是依赖后端筛选
2. **数据获取**：一次性获取所有订单数据（设置 size: 1000）
3. **分页处理**：在前端进行分页处理
4. **状态映射**：在前端根据业务逻辑映射订单状态

### 核心修改

#### 1. 数据获取逻辑

```javascript
// 修改前：传递状态给后端
if (activeTab.value !== "all") {
  params.status = activeTab.value; // 后端不认识新状态
}

// 修改后：获取所有数据，前端筛选
const params = {
  page: 0,
  size: 1000, // 获取足够多的订单数据
};
```

#### 2. 筛选逻辑

```javascript
const getFilteredOrders = () => {
  if (activeTab.value === "all") {
    return orders.value;
  }

  return orders.value.filter((order) => {
    const status = order.status;
    const paymentStatus = order.paymentStatus;

    switch (activeTab.value) {
      case "PENDING":
        return status === "PENDING";
      case "NEED_PAYMENT":
        return status === "CONFIRMED" && (paymentStatus === "UNPAID" || !paymentStatus);
      case "IN_PROGRESS":
        return (
          paymentStatus === "PAID" || status === "CHECKED_IN" || status === "READY_FOR_CHECKIN"
        );
      case "COMPLETED":
        return status === "COMPLETED";
      case "CANCELLED":
        return status.includes("CANCELLED") || status === "REJECTED";
      case "REFUND_RELATED":
        return (
          paymentStatus === "REFUND_PENDING" ||
          paymentStatus === "REFUNDED" ||
          paymentStatus === "REFUND_FAILED"
        );
      default:
        return true;
    }
  });
};
```

#### 3. 分页处理

```javascript
// 前端分页
const paginatedFilteredOrders = computed(() => {
  const filtered = filteredOrders.value;
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filtered.slice(start, end);
});

// 动态总数
const filteredTotal = computed(() => filteredOrders.value.length);
```

#### 4. 事件处理

```javascript
// 标签页切换：不重新获取数据
const handleTabChange = () => {
  currentPage.value = 1;
  updateStatusCounts(); // 只更新统计
};

// 分页切换：不重新获取数据
const handlePageChange = (page: number) => {
  currentPage.value = page;
  // 分页由计算属性自动处理
};
```

## 测试要点

### 1. 状态筛选测试

- [ ] 点击"待确认"标签页，应该显示 PENDING 状态的订单
- [ ] 点击"待支付"标签页，应该显示 CONFIRMED + UNPAID 的订单
- [ ] 点击"进行中"标签页，应该显示已支付但未完成的订单
- [ ] 点击"已完成"标签页，应该显示 COMPLETED 状态的订单
- [ ] 点击"已取消"标签页，应该显示各种取消状态的订单
- [ ] 点击"退款相关"标签页，应该显示退款相关状态的订单

### 2. 数量徽章测试

- [ ] 每个标签页应该显示对应状态订单的数量
- [ ] 数量为 0 时不显示徽章
- [ ] 数量大于 0 时显示蓝色徽章

### 3. 分页测试

- [ ] 每页显示正确数量的订单（默认 10 个）
- [ ] 分页总数应该基于筛选后的订单数量
- [ ] 切换页面应该显示不同的订单

### 4. 空状态测试

- [ ] 每个标签页在没有对应订单时显示相应的空状态信息
- [ ] 空状态描述应该符合当前标签页的含义

## 预期效果

1. **立即生效**：切换标签页后立即看到筛选结果，无需等待 API 请求
2. **准确筛选**：每个状态分类都能正确显示对应的订单
3. **数量显示**：徽章正确显示各状态订单数量
4. **性能提升**：减少 API 请求次数，提升用户体验

## 注意事项

1. **数据量限制**：当前设置获取 1000 条订单，如果用户订单超过此数量需要考虑真正的分页
2. **性能考虑**：所有筛选和分页都在前端进行，对大量数据可能有性能影响
3. **数据一致性**：页面需要刷新才能获取最新的订单状态变化
