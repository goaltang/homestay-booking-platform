<template>
  <div class="amenities-section">
    <h2>房源所有设施</h2>
    <div class="amenities-grid" v-if="amenities && amenities.length > 0">
      <div v-for="(amenity, index) in displayedAmenities" :key="index" class="amenity-item">
        <el-icon class="check-icon">
          <Check />
        </el-icon>
        <span class="amenity-label">{{ amenity.label || "设施项目" }}</span>
      </div>
    </div>

    <!-- 展开/收起按钮 -->
    <div v-if="amenities && amenities.length > initialDisplayCount" class="show-more-section">
      <el-button type="text" class="show-more-btn" @click="toggleShowAll">
        <span v-if="!showAll">显示全部 {{ amenities.length }} 项设施</span>
        <span v-else>收起设施</span>
        <el-icon class="arrow-icon" :class="{ rotate: showAll }">
          <CaretRight />
        </el-icon>
      </el-button>
    </div>

    <div v-else-if="!amenities || amenities.length === 0" class="no-amenities">
      <el-empty description="暂无设施信息" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { Check, CaretRight } from "@element-plus/icons-vue";
import type { AmenityItem } from "@/types/homestay";

// Props
interface Props {
  amenities: AmenityItem[];
  initialDisplayCount?: number;
}

const props = withDefaults(defineProps<Props>(), {
  amenities: () => [],
  initialDisplayCount: 10,
});

// State
const showAll = ref(false);

// Computed
const displayedAmenities = computed(() => {
  if (!props.amenities) return [];
  if (showAll.value || props.amenities.length <= props.initialDisplayCount) {
    return props.amenities;
  }
  return props.amenities.slice(0, props.initialDisplayCount);
});

// Methods
const toggleShowAll = () => {
  showAll.value = !showAll.value;
};
</script>

<style scoped>
.amenities-section {
  margin: 32px 0;
}

.amenities-section h2 {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 24px;
}

.amenities-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px 24px;
}

.amenity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}

.check-icon {
  color: #67c23a;
  font-size: 18px;
  flex-shrink: 0;
}

.amenity-label {
  font-size: 16px;
  color: #303133;
  line-height: 1.5;
}

.no-amenities {
  text-align: center;
  padding: 40px 0;
  color: #909399;
}

.show-more-section {
  text-align: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.show-more-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  color: #409eff;
  font-weight: 500;
  transition: all 0.3s ease;
}

.show-more-btn:hover {
  color: #66b1ff;
  transform: translateY(-1px);
}

.arrow-icon {
  transition: transform 0.3s ease;
}

.arrow-icon.rotate {
  transform: rotate(90deg);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .amenities-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .amenity-item {
    padding: 8px 0;
  }

  .amenity-label {
    font-size: 15px;
  }
}
</style>
