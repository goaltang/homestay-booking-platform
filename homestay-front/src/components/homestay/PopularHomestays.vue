<template>
  <HomestaySection
    title="热门民宿"
    :homestays="homestays"
    :loading="loading"
    :error="error"
    empty-text="暂无热门民宿"
    error-text="加载热门失败，点击重试"
    :max-display="6"
    view-all-route="/homestays"
    :view-all-query="{ type: 'popular' }"
    @homestay-click="handleHomestayClick"
    @view-all="handleViewAll"
    @retry="loadPopularHomestays"
  />
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { getPopularHomestays } from "../../api/recommendation";
import HomestaySection from "./HomestaySection.vue";

const homestays = ref<any[]>([]);
const loading = ref(false);
const error = ref(false);

const emit = defineEmits<{
  homestayClick: [homestay: any];
  viewAll: [route: string, query: Record<string, any>];
}>();

// 加载热门民宿数据
const loadPopularHomestays = async () => {
  loading.value = true;
  error.value = false;
  try {
    const response = await getPopularHomestays();
    homestays.value = response.data || [];
  } catch (err) {
    console.error("加载热门民宿失败:", err);
    error.value = true;
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

// 组件挂载时加载数据
onMounted(() => {
  loadPopularHomestays();
});

// 暴露刷新方法供父组件调用
defineExpose({
  refresh: loadPopularHomestays,
});
</script>
