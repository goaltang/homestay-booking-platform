<template>
  <el-card class="homestay-card" :body-style="{ padding: '0px' }">
    <div class="homestay-image-container" @click.stop="handleCardClick">
      <el-carousel v-if="images.length > 1" height="220px" indicator-position="none" arrow="hover">
        <el-carousel-item v-for="(image, index) in images" :key="index">
          <img
            :src="image"
            :alt="imageAlt"
            class="homestay-image"
            loading="lazy"
            decoding="async"
            @error="handleHomestayImageError"
          />
        </el-carousel-item>
      </el-carousel>
      <img
        v-else
        :src="images[0]"
        :alt="imageAlt"
        class="homestay-image"
        loading="lazy"
        decoding="async"
        @error="handleHomestayImageError"
      />

      <div v-if="isUserLoggedIn" class="favorite-button" @click.stop="toggleFavorite">
        <el-icon :size="iconSize" :color="isFavorite ? 'var(--color-primary-500)' : '#fff'">
          <component :is="favoriteIcon" />
        </el-icon>
      </div>
    </div>

    <div class="homestay-info" @click.stop="handleCardClick">
      <div class="homestay-header">
        <h3 class="homestay-title">{{ homestay.title || homestay.name }}</h3>
        <div class="homestay-rating" v-if="rating">
          <el-icon>
            <Star />
          </el-icon>
          <span>{{ rating }}</span>
        </div>
      </div>

      <div class="homestay-location">{{ location }}</div>
      <div class="homestay-distance" v-if="homestay.distanceFromCenter">
        距离市中心 {{ homestay.distanceFromCenter }} 公里
      </div>
      <div class="group-tag" v-if="homestay.groupName">
        <span class="group-badge" :style="{ backgroundColor: homestay.groupColor || '#409eff' }">
          {{ homestay.groupName }}
        </span>
      </div>
      <div class="homestay-features" v-if="features.length > 0">
        <span v-for="feature in features" :key="feature" class="feature-tag">{{ feature }}</span>
      </div>
      <div class="booking-mode-tag" v-if="homestay.autoConfirm">
        <el-icon class="auto-confirm-icon" :size="14">
          <Lightning />
        </el-icon>
        <span>即时预订</span>
      </div>
      <div class="homestay-price">
        <span class="price">¥{{ price }}</span>
        <span class="night">/晚</span>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { Star, StarFilled, Lightning } from "@element-plus/icons-vue";
import { useFavoritesStore } from "@/stores/favorites";
import { useUserStore } from "@/stores/user";
import { codeToText } from "element-china-area-data";
import { formatPropertyType } from "@/utils/homestayUtils";
import { getHomestayImageUrl } from "@/utils/image";
import { trackClick } from "@/api/tracking";

const HOMESTAY_IMAGE_FALLBACK =
  "data:image/svg+xml;utf8," +
  encodeURIComponent(`
<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400" viewBox="0 0 600 400">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#f7f3ee"/>
      <stop offset="1" stop-color="#e8ded2"/>
    </linearGradient>
  </defs>
  <rect width="600" height="400" fill="url(#bg)"/>
  <path d="M180 255V155l120-78 120 78v100h-70v-72h-100v72z" fill="#c9a77d"/>
  <path d="M150 164 300 66l150 98-24 37-126-82-126 82z" fill="#9b7651"/>
  <rect x="257" y="190" width="86" height="65" rx="6" fill="#f9f5ef"/>
  <text x="300" y="318" text-anchor="middle" font-family="Arial, sans-serif" font-size="24" fill="#8a6f56">民宿图片待更新</text>
</svg>`);

interface Props {
  homestay: any;
  iconSize?: number;
  showCarousel?: boolean;
  navigateOnClick?: boolean;
  homestayTypes?: any[]; // 添加房源类型数据
}

const props = withDefaults(defineProps<Props>(), {
  iconSize: 24,
  showCarousel: true,
  navigateOnClick: true,
  homestayTypes: () => [],
});

const emit = defineEmits<{
  cardClick: [homestay: any];
}>();

const router = useRouter();
const favoritesStore = useFavoritesStore();
const userStore = useUserStore();

// 计算属性
const isFavorite = computed(() => favoritesStore.isFavorite(props.homestay.id));
const isUserLoggedIn = computed(() => userStore.isAuthenticated);
const favoriteIcon = computed(() => (isFavorite.value ? StarFilled : Star));
const imageAlt = computed(() => props.homestay.title || props.homestay.name || "民宿图片");

const images = computed(() => {
  const rawImages: string[] = [];

  if (props.homestay.coverImage) {
    rawImages.push(props.homestay.coverImage);
  }

  if (
    props.homestay.images &&
    Array.isArray(props.homestay.images) &&
    props.homestay.images.length > 0
  ) {
    props.homestay.images.forEach((image: string | { url?: string; path?: string }) => {
      const imageUrl = typeof image === "string" ? image : image?.url || image?.path;
      if (imageUrl) rawImages.push(imageUrl);
    });
  }

  const uniqueImages = Array.from(new Set(rawImages));
  const visibleImages = props.showCarousel ? uniqueImages : uniqueImages.slice(0, 1);

  if (visibleImages.length === 0) {
    return [HOMESTAY_IMAGE_FALLBACK];
  }

  return visibleImages.map((image) => getHomestayImageUrl(image));
});

const rating = computed(() => {
  if (props.homestay.rating) {
    return typeof props.homestay.rating === "number"
      ? props.homestay.rating.toFixed(1)
      : props.homestay.rating;
  }
  return null;
});

const location = computed(() => {
  if (props.homestay.location) return props.homestay.location;

  const parts = [];
  // 优先使用后端返回的文本字段
  if (props.homestay.provinceText) {
    parts.push(props.homestay.provinceText);
  }
  if (props.homestay.cityText) {
    if (!parts.includes(props.homestay.cityText)) {
      parts.push(props.homestay.cityText);
    }
  }
  if (props.homestay.districtText) {
    if (!parts.includes(props.homestay.districtText)) {
      parts.push(props.homestay.districtText);
    }
  }
  // fallback：用 code 通过 codeToText 转换
  if (parts.length === 0) {
    if (props.homestay.provinceCode && codeToText[props.homestay.provinceCode]) {
      parts.push(codeToText[props.homestay.provinceCode]);
    }
    if (props.homestay.cityCode && codeToText[props.homestay.cityCode]) {
      if (!parts.includes(codeToText[props.homestay.cityCode])) {
        parts.push(codeToText[props.homestay.cityCode]);
      }
    }
  }
  return parts.length > 0 ? parts.join(" · ") : "位置待更新";
});

const formatHomestayType = (typeCode: string | undefined): string => {
  if (!typeCode) return "";
  const foundType = props.homestayTypes.find(
    (type) => type.code === typeCode || type.value === typeCode || type.name === typeCode
  );
  return foundType?.name || foundType?.label || formatPropertyType(typeCode);
};

const features = computed(() => {
  const features = [];

  if (props.homestay.maxGuests) {
    features.push(`${props.homestay.maxGuests}人`);
  }

  // 使用类型转码功能
  if (props.homestay.propertyType || props.homestay.type) {
    const typeCode = props.homestay.propertyType || props.homestay.type;
    features.push(formatHomestayType(typeCode));
  }

  if (props.homestay.amenities && Array.isArray(props.homestay.amenities)) {
    const amenityNames = props.homestay.amenities
      .slice(0, 2)
      .map((amenity: any) =>
        typeof amenity === "string" ? amenity : amenity.label || amenity.name
      );
    features.push(...amenityNames);
  }

  return features.slice(0, 4);
});

const price = computed(() => {
  return props.homestay.price || props.homestay.pricePerNight || 0;
});

// 方法
const toggleFavorite = async () => {
  try {
    await favoritesStore.toggleFavorite(props.homestay.id);
  } catch (error) {
    console.error("切换收藏状态失败:", error);
  }
};

const handleHomestayImageError = (event: Event) => {
  const image = event.target as HTMLImageElement;
  if (image.src !== HOMESTAY_IMAGE_FALLBACK) {
    image.src = HOMESTAY_IMAGE_FALLBACK;
  }
};

const handleCardClick = () => {
  if (props.homestay?.id) {
    trackClick(Number(props.homestay.id));
  }

  if (props.navigateOnClick) {
    router.push(`/homestays/${props.homestay.id}`);
    return;
  }
  emit("cardClick", props.homestay);
};
</script>

<style scoped>
.homestay-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
  overflow: hidden;
  border-radius: 12px;
}

.homestay-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}

.homestay-image-container {
  position: relative;
  height: 220px;
  cursor: pointer;
  background-color: #f0f0f0;
  overflow: hidden;
}

.homestay-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: opacity 0.3s ease;
}

.favorite-button {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  cursor: pointer;
  background-color: rgba(0, 0, 0, 0.3);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
}

.favorite-button:hover {
  background-color: rgba(0, 0, 0, 0.5);
}

.homestay-info {
  padding: 16px;
  cursor: pointer;
}

.homestay-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.homestay-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.homestay-rating {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
}

.homestay-location,
.homestay-distance {
  color: #717171;
  font-size: 14px;
  margin-bottom: 4px;
}

.homestay-features {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.feature-tag {
  color: #717171;
  font-size: 14px;
  margin-bottom: 4px;
}

.group-tag {
  margin-bottom: 6px;
}

.group-badge {
  display: inline-block;
  color: #fff;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.homestay-price {
  margin-top: 8px;
  font-size: 16px;
}

.homestay-price .price {
  font-weight: 600;
}

.homestay-price .night {
  font-weight: normal;
}

/* 自动确认标签样式 */
.booking-mode-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: linear-gradient(135deg, #67c23a 0%, #5daf34 100%);
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  margin-top: 8px;
  box-shadow: 0 2px 4px rgba(103, 194, 58, 0.3);
}

.auto-confirm-icon {
  color: white;
}

@media (max-width: 768px) {
  .homestay-image-container {
    height: 180px;
  }

  .homestay-info {
    padding: 12px;
  }

  .homestay-title {
    font-size: 14px;
  }
}
</style>
