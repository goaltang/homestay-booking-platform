<template>
  <div v-if="showBar && announcements.length > 0" class="announcement-bar">
    <div class="announcement-content">
      <el-icon class="ann-icon" :size="16">
        <Notification />
      </el-icon>
      <div class="ann-scroll">
        <span
          v-for="item in announcements"
          :key="item.id"
          class="ann-item"
          @click="handleClick(item)"
        >
          {{ item.title }}
        </span>
      </div>
      <el-icon class="ann-close" :size="16" @click="showBar = false">
        <Close />
      </el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { Notification, Close } from "@element-plus/icons-vue";
import { getAnnouncements, type Announcement } from "@/api/announcement";

const showBar = ref(true);
const announcements = ref<Announcement[]>([]);

const loadAnnouncements = async () => {
  try {
    const res = await getAnnouncements({ page: 0, size: 3 });
    if (res.data?.success && Array.isArray(res.data.data)) {
      announcements.value = res.data.data;
    }
  } catch {
    // 静默失败，不影响首页加载
  }
};

const handleClick = (item: Announcement) => {
  // 可跳转到公告详情页，或弹出对话框
  console.log("公告点击:", item);
};

onMounted(() => {
  loadAnnouncements();
});
</script>

<style scoped>
.announcement-bar {
  background: var(--color-primary-600);
  color: white;
  padding: 8px 16px;
  font-size: 13px;
}

.announcement-content {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.ann-icon {
  flex-shrink: 0;
}

.ann-scroll {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ann-item {
  cursor: pointer;
  margin-right: 24px;
  opacity: 0.95;
  transition: opacity 0.2s;
}

.ann-item:hover {
  opacity: 1;
  text-decoration: underline;
}

.ann-close {
  flex-shrink: 0;
  cursor: pointer;
  opacity: 0.8;
  transition: opacity 0.2s;
}

.ann-close:hover {
  opacity: 1;
}
</style>
