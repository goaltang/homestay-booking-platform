<template>
  <div v-if="recentList.length > 0" class="recent-section">
    <div class="section-header">
      <h2 class="section-title">
        <el-icon :size="22" class="title-icon"><Timer /></el-icon>
        最近浏览
      </h2>
      <el-button link type="primary" size="small" @click="handleClear">清空</el-button>
    </div>
    <div class="recent-scroll">
      <div
        v-for="item in displayList"
        :key="item.id"
        class="recent-card"
        @click="handleClick(item)"
      >
        <div class="recent-image">
          <img
            v-if="item.coverImage"
            :src="getImageUrl(item.coverImage)"
            :alt="item.title"
            loading="lazy"
          />
          <div v-else class="image-placeholder">
            <el-icon :size="24"><Picture /></el-icon>
          </div>
        </div>
        <div class="recent-info">
          <div class="recent-title">{{ item.title }}</div>
          <div class="recent-meta">
            <span v-if="item.location" class="recent-location">{{ item.location }}</span>
            <span v-if="item.price" class="recent-price">¥{{ item.price }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Timer, Picture } from "@element-plus/icons-vue";
import { getRecentViews, clearRecentViews, type RecentViewItem } from "@/utils/recentViews";
import { getHomestayImageUrl } from "@/utils/image";

const emit = defineEmits<{
  click: [item: RecentViewItem];
  clear: [];
}>();

const recentList = computed(() => getRecentViews());
const displayList = computed(() => recentList.value.slice(0, 6));

const getImageUrl = (url: string) => getHomestayImageUrl(url);

const handleClick = (item: RecentViewItem) => {
  emit("click", item);
};

const handleClear = () => {
  clearRecentViews();
  emit("clear");
};
</script>

<style scoped>
.recent-section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #222;
}

.title-icon {
  color: #ff6b6b;
}

.recent-scroll {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.recent-scroll::-webkit-scrollbar {
  display: none;
}

.recent-card {
  flex-shrink: 0;
  width: 180px;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.recent-card:hover {
  transform: translateY(-4px);
}

.recent-image {
  width: 100%;
  height: 120px;
  border-radius: 12px;
  overflow: hidden;
  background: #f0f0f0;
  margin-bottom: 8px;
}

.recent-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #bbb;
}

.recent-title {
  font-size: 14px;
  font-weight: 500;
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.recent-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.recent-location {
  color: #717171;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100px;
}

.recent-price {
  color: #ff385c;
  font-weight: 600;
}

@media (max-width: 768px) {
  .recent-card {
    width: 150px;
  }

  .recent-image {
    height: 100px;
  }
}
</style>
