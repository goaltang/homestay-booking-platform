<template>
  <div class="homestay-section">
    <!-- 区域标题和操作 -->
    <div v-if="title || showViewAll" class="section-header">
      <div class="section-title-wrap">
        <h2 v-if="title" class="section-title">{{ title }}</h2>
        <p v-if="subtitle" class="section-subtitle">{{ subtitle }}</p>
      </div>
      <div class="section-actions">
        <el-button v-if="showViewAll && homestays.length > 0" link @click="handleViewAll">
          查看全部
        </el-button>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <HomestaySkeleton :count="maxDisplay || 6" />
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-result">
      <el-empty :description="errorText">
        <template #extra>
          <el-button type="primary" @click="handleRetry">重新加载</el-button>
        </template>
      </el-empty>
    </div>

    <!-- 空状态 -->
    <div v-else-if="homestays.length === 0" class="empty-result">
      <el-empty :description="emptyText" />
    </div>

    <!-- 房源列表 -->
    <div v-else class="homestay-grid">
      <HomestayCard
        v-for="homestay in displayHomestays"
        :key="homestay.id"
        :homestay="homestay"
        :homestay-types="homestayTypes"
        :navigate-on-click="false"
        @card-click="handleHomestayClick"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import HomestayCard from "./HomestayCard.vue";
import HomestaySkeleton from "./HomestaySkeleton.vue";

interface Props {
  title: string; // 区域标题
  subtitle?: string; // 区域副标题
  homestays: any[]; // 房源数据
  homestayTypes?: any[]; // 房源类型数据
  loading?: boolean; // 加载状态
  error?: boolean; // 错误状态
  emptyText?: string; // 空状态文本
  errorText?: string; // 错误状态文本
  showViewAll?: boolean; // 是否显示查看全部按钮
  maxDisplay?: number; // 最大显示数量
  viewAllRoute?: string; // 查看全部的路由
  viewAllQuery?: Record<string, any>; // 查看全部的查询参数
}

const props = withDefaults(defineProps<Props>(), {
  title: "",
  homestayTypes: () => [],
  loading: false,
  error: false,
  emptyText: "暂无房源数据",
  errorText: "加载失败，请稍后重试",
  showViewAll: true,
  maxDisplay: 6,
  viewAllRoute: "/homestays",
  viewAllQuery: () => ({}),
});

const emit = defineEmits<{
  homestayClick: [homestay: any];
  viewAll: [route: string, query: Record<string, any>];
  retry: [];
}>();

// 显示的房源列表（限制数量）
const displayHomestays = computed(() => {
  return Array.isArray(props.homestays) ? props.homestays.slice(0, props.maxDisplay) : [];
});

// 处理房源点击
const handleHomestayClick = (homestay: any) => {
  emit("homestayClick", homestay);
};

// 处理查看全部
const handleViewAll = () => {
  emit("viewAll", props.viewAllRoute, props.viewAllQuery);
};

// 处理重试
const handleRetry = () => {
  emit("retry");
};
</script>

<style scoped>
.homestay-section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-neutral-200);
}

.section-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--color-neutral-900);
}

.section-subtitle {
  margin-top: var(--space-1);
  color: var(--color-neutral-600);
  font-size: var(--text-body-sm);
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.loading-container {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.empty-result,
.error-result {
  text-align: center;
  padding: 40px 20px;
}

.homestay-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

@media (max-width: 900px) {
  .homestay-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .homestay-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .section-title {
    font-size: 20px;
  }
}
</style>
