<template>
  <HomestaySection
    title="猜你喜欢"
    :homestays="homestays"
    :loading="loading"
    :empty-text="emptyText"
    :max-display="6"
    view-all-route="/homestays"
    :view-all-query="{ personalized: 'true' }"
    @homestay-click="handleHomestayClick"
    @view-all="handleViewAll"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { getMyPersonalizedRecommendations } from "../../api/recommendation";
import { useUserStore } from "../../stores/user";
import HomestaySection from "./HomestaySection.vue";

const userStore = useUserStore();
const homestays = ref<any[]>([]);
const loading = ref(false);

// 计算空状态提示文本
const emptyText = computed(() => {
  if (!userStore.isAuthenticated) {
    return "登录后为您推荐专属房源";
  } else {
    return "暂无个性化推荐，多浏览和收藏房源来获得更精准的推荐";
  }
});

const emit = defineEmits<{
  homestayClick: [homestay: any];
  viewAll: [route: string, query: Record<string, any>];
}>();

// 加载个性化推荐数据
const loadPersonalizedRecommendations = async () => {
  // 只有登录用户才加载个性化推荐
  if (!userStore.isAuthenticated) {
    homestays.value = [];
    return;
  }

  loading.value = true;
  try {
    const homestaysResponse = await getMyPersonalizedRecommendations(6);
    const homestaysList = homestaysResponse.data?.content || homestaysResponse.data || [];
    homestays.value = homestaysList.slice(0, 12);
  } catch (error) {
    console.error("加载个性化推荐失败:", error);
    homestays.value = [];
  } finally {
    loading.value = false;
  }
};

// 处理房源点击
const handleHomestayClick = (homestay: any) => {
  emit("homestayClick", homestay);
};

// 处理查看全部
const handleViewAll = (route: string, query: Record<string, any>) => {
  emit("viewAll", route, query);
};

// 监听用户登录状态变化
watch(
  () => userStore.isAuthenticated,
  (isAuthenticated) => {
    if (isAuthenticated) {
      // 用户登录后加载个性化推荐
      loadPersonalizedRecommendations();
    } else {
      // 用户退出登录后清空推荐
      homestays.value = [];
    }
  },
  { immediate: true }
);

// 暴露刷新方法供父组件调用
defineExpose({
  refresh: loadPersonalizedRecommendations,
});
</script>

<style scoped>
/* 个性化推荐区域的特殊样式 */
:deep(.homestay-section-title) {
  color: #ff6b6b;
  position: relative;
}

:deep(.homestay-section-title::after) {
  content: "✨";
  margin-left: 8px;
  font-size: 0.9em;
}
</style>
