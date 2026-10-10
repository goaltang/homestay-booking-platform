<template>
  <div class="hero-wrapper">
    <!-- Banner 轮播或默认背景 -->
    <div class="hero-visual">
      <el-carousel
        v-if="banners.length > 0"
        height="100%"
        :interval="5000"
        arrow="hover"
        indicator-position="none"
        @change="handleBannerChange"
        class="hero-carousel"
      >
        <el-carousel-item v-for="banner in banners" :key="banner.id">
          <div
            class="hero-slide"
            :style="getBannerStyle(banner)"
            @click="handleBannerClick(banner)"
          >
            <div class="hero-overlay" />
            <div class="hero-content">
              <h1 class="hero-title">{{ banner.title }}</h1>
              <p v-if="banner.subtitle" class="hero-desc">{{ banner.subtitle }}</p>
              <el-button v-if="banner.linkUrl" type="primary" class="hero-btn" round size="large">
                立即探索
                <el-icon class="btn-icon"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>

      <!-- 默认背景 -->
      <div v-else class="hero-slide default-slide">
        <div class="hero-overlay" />
        <div class="hero-content">
          <h1 class="hero-title">
            发现独特民宿
            <span class="hero-title-line">像当地人一样旅行</span>
          </h1>
          <p class="hero-desc">
            从城市公寓到海边别墅，探索数万个独特房源，每一次入住都是难忘的体验。
          </p>
          <el-button type="primary" class="hero-btn" round size="large" @click="$emit('explore')">
            开始探索
            <el-icon class="btn-icon"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 底部渐变遮罩，让搜索框叠加更自然 -->
      <div class="hero-fade" />
    </div>

    <!-- 搜索框插槽区域 -->
    <div class="hero-search-slot">
      <slot name="search" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight } from "@element-plus/icons-vue";
import type { Banner, HomeStats } from "@/api/home";

interface Props {
  banners?: Banner[];
  stats?: HomeStats;
}

withDefaults(defineProps<Props>(), {
  banners: () => [],
  stats: () => ({
    homestayCount: 10000,
    cityCount: 50,
    positiveRate: 98,
    recentOrders: 0,
    availableToday: 0,
  }),
});

const emit = defineEmits<{
  bannerClick: [banner: Banner];
  explore: [];
}>();

const getBannerStyle = (banner: Banner) => {
  const style: Record<string, string> = {};
  if (banner.imageUrl) {
    style.backgroundImage = `url(${banner.imageUrl})`;
    style.backgroundSize = "cover";
    style.backgroundPosition = "center";
  } else if (banner.bgGradient) {
    style.background = banner.bgGradient;
  } else {
    style.background = "linear-gradient(135deg, #6a2d1e 0%, #a33d22 48%, #d45f2e 100%)";
  }
  return style;
};

const handleBannerClick = (banner: Banner) => {
  emit("bannerClick", banner);
};

const handleBannerChange = (_index: number) => {
  // 可扩展：埋点统计 Banner 曝光
};
</script>

<style scoped>
.hero-wrapper {
  position: relative;
}

.hero-visual {
  position: relative;
  height: 480px;
  overflow: hidden;
  background: var(--color-primary-800);
}

.hero-carousel,
:deep(.el-carousel__container) {
  height: 100% !important;
}

.hero-slide {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  cursor: pointer;
}

.default-slide {
  background:
    radial-gradient(circle at 18% 20%, rgba(249, 224, 210, 0.22), transparent 28%),
    linear-gradient(
      135deg,
      var(--color-primary-900) 0%,
      var(--color-primary-700) 48%,
      var(--color-primary-500) 100%
    );
}

.hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to bottom,
    rgba(31, 28, 25, 0.34) 0%,
    rgba(31, 28, 25, 0.14) 42%,
    rgba(31, 28, 25, 0.42) 100%
  );
}

.hero-fade {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 120px;
  background: linear-gradient(to bottom, transparent, var(--color-background-warm, #f7f4f0));
  pointer-events: none;
}

.hero-content {
  position: relative;
  z-index: 1;
  max-width: 720px;
  padding: 0 24px;
  color: white;
}

.hero-title {
  font-family: var(--font-display);
  font-size: var(--text-hero);
  font-weight: 800;
  line-height: 1.15;
  margin: 0 0 16px;
  text-shadow: 0 2px 20px rgba(0, 0, 0, 0.3);
  letter-spacing: 0;
}

.hero-title-line {
  display: block;
  font-size: clamp(1.375rem, 3vw, 2rem);
  font-weight: 500;
  opacity: 0.95;
  margin-top: 8px;
}

.hero-desc {
  font-size: 17px;
  line-height: 1.7;
  opacity: 0.9;
  margin: 0 auto 28px;
  max-width: 520px;
}

.hero-btn {
  background: var(--color-surface-elevated);
  color: var(--color-primary-800);
  border: none;
  font-weight: 600;
  padding: 12px 32px;
  font-size: 15px;
  box-shadow: 0 10px 28px rgba(31, 28, 25, 0.22);
  transition:
    transform var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.hero-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 36px rgba(31, 28, 25, 0.28);
  background: var(--color-primary-50);
}

.btn-icon {
  margin-left: 6px;
}

/* 搜索框插槽区域 */
.hero-search-slot {
  position: relative;
  z-index: 10;
  margin-top: -40px;
  padding: 0 20px;
}

/* 响应式 */
@media (max-width: 768px) {
  .hero-visual {
    height: 380px;
  }

  .hero-title {
    font-size: 32px;
  }

  .hero-title-line {
    font-size: 22px;
  }

  .hero-desc {
    font-size: 15px;
    margin-bottom: 20px;
  }

  .hero-btn {
    padding: 10px 24px;
    font-size: 14px;
  }

  .hero-search-slot {
    margin-top: -28px;
    padding: 0 16px;
  }
}
</style>
