<template>
  <div
    class="map-homestay-card"
    :class="{ active: isActive, hovered: isHovered }"
    :data-id="homestay.id"
    @click="handleClick"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <div class="card-image">
      <img :src="imageUrl" :alt="homestay.title" loading="lazy" />
      <div v-if="homestay.autoConfirm" class="auto-confirm-badge">
        <el-icon :size="12"><Lightning /></el-icon>
        即时预订
      </div>
    </div>
    <div class="card-content">
      <h4 class="card-title">{{ homestay.title }}</h4>
      <div class="card-location" v-if="locationText">
        <el-icon :size="12"><Location /></el-icon>
        {{ locationText }}
      </div>
      <div class="card-features" v-if="featuresText">
        {{ featuresText }}
      </div>
      <div class="card-distance" v-if="homestay.distanceKm">
        <el-icon :size="12"><Location /></el-icon>
        距当前位置 {{ homestay.distanceKm.toFixed(1) }}km
      </div>
      <div class="card-footer">
        <div class="card-rating" v-if="homestay.rating">
          <el-icon :size="14" color="#ffb800"><Star /></el-icon>
          <span>{{ homestay.rating.toFixed(1) }}</span>
        </div>
        <div class="card-actions">
          <button class="detail-button" type="button" @click.stop="handleViewDetail">
            查看详情
          </button>
          <div class="card-price">
            <span class="price">¥{{ homestay.price }}</span>
            <span class="unit">/晚</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Location, Star, Lightning } from '@element-plus/icons-vue';
import { codeToText } from 'element-china-area-data';
import type { MapHomestay } from '@/composables/useMapSearch';

interface Props {
  homestay: MapHomestay;
  isActive?: boolean;
  isHovered?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  isActive: false,
  isHovered: false,
});

const emit = defineEmits<{
  click: [homestay: MapHomestay];
  hover: [id: number | null];
  viewDetail: [id: number];
}>();

// 处理图片URL
const imageUrl = computed(() => {
  const defaultImage = 'https://picsum.photos/300/200';
  if (!props.homestay.coverImage) return defaultImage;

  if (props.homestay.coverImage.startsWith('http')) {
    return props.homestay.coverImage;
  }

  const cleanUrl = props.homestay.coverImage.startsWith('/')
    ? props.homestay.coverImage
    : `/${props.homestay.coverImage}`;

  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';
  return `${baseUrl}${cleanUrl}`;
});

// 位置文本
const locationText = computed(() => {
  const parts: string[] = [];
  if (props.homestay.cityCode && codeToText[props.homestay.cityCode]) {
    parts.push(codeToText[props.homestay.cityCode]);
  }
  if (props.homestay.districtCode && codeToText[props.homestay.districtCode]) {
    parts.push(codeToText[props.homestay.districtCode]);
  }
  return parts.join(' · ');
});

// 特征文本
const featuresText = computed(() => {
  const parts: string[] = [];
  if (props.homestay.maxGuests) {
    parts.push(`${props.homestay.maxGuests}人`);
  }
  if (props.homestay.propertyType) {
    const typeMap: Record<string, string> = {
      'ENTIRE': '整套公寓',
      'PRIVATE': '独立房间',
      'LOFT': '复式住宅',
      'VILLA': '别墅',
      'STUDIO': '开间/单间',
      'TOWNHOUSE': '联排别墅',
      'COURTYARD': '四合院/院子',
      'HOTEL': '酒店公寓',
      'SHARED': '合住房间',
    };
    parts.push(typeMap[props.homestay.propertyType] || props.homestay.propertyType);
  }
  return parts.join(' · ');
});

// 方法
const handleClick = () => {
  emit('click', props.homestay);
};

const handleMouseEnter = () => {
  emit('hover', props.homestay.id);
};

const handleMouseLeave = () => {
  emit('hover', null);
};

const handleViewDetail = () => {
  emit('viewDetail', props.homestay.id);
};
</script>

<style scoped>
.map-homestay-card {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 2px solid transparent;
}

.map-homestay-card:hover,
.map-homestay-card.hovered {
  background: #f8f9fa;
  transform: translateX(4px);
}

.map-homestay-card.active {
  border-color: var(--primary-color, #ff385c);
  background: #fff5f7;
}

.card-image {
  position: relative;
  width: 100px;
  height: 100px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.auto-confirm-badge {
  position: absolute;
  top: 4px;
  left: 4px;
  display: flex;
  align-items: center;
  gap: 2px;
  background: linear-gradient(135deg, #67c23a 0%, #5daf34 100%);
  color: #fff;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 6px;
}

.card-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-location {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #717171;
  margin-top: 4px;
}

.card-features {
  font-size: 12px;
  color: #717171;
  margin-top: 4px;
}

.card-distance {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #3b82f6;
  font-weight: 500;
  margin-top: 4px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 8px;
}

.card-rating {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #222;
}

.card-price {
  text-align: right;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-button {
  border: none;
  background: transparent;
  color: #ff385c;
  font-size: 12px;
  font-weight: 600;
  padding: 0;
  cursor: pointer;
}

.detail-button:hover {
  color: #e31c5f;
}

.card-price .price {
  font-size: 16px;
  font-weight: 600;
  color: #222;
}

.card-price .unit {
  font-size: 12px;
  color: #717171;
}
</style>
