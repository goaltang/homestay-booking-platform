<template>
  <section class="hero" :class="{ 'hero--no-photo': !hero.image }">
    <img
      v-if="hero.image"
      class="hero-photo"
      :src="hero.image"
      alt=""
      fetchpriority="high"
      decoding="async"
    />
    <div class="hero-shade" aria-hidden="true" />

    <div class="hero-inner">
      <div class="hero-copy">
        <h1 class="hero-title">{{ hero.title }}</h1>
        <p class="hero-desc">{{ hero.subtitle }}</p>
      </div>

      <div class="hero-search">
        <slot name="search" />
      </div>

      <button v-if="hero.caption" type="button" class="hero-caption" @click="handleCaptionClick">
        <span class="hero-caption-label">{{ hero.caption }}</span>
        <el-icon :size="14"><ArrowRight /></el-icon>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { ArrowRight } from "@element-plus/icons-vue";
import type { Banner } from "@/api/home";
import { getHomestayImageUrl } from "@/utils/image";

interface HeroHomestay {
  id: number | string;
  title?: string;
  coverImage?: string;
  price?: string | number;
  cityText?: string;
}

interface Props {
  banners?: Banner[];
  /** 没有带图片的 Banner 时，用这套房源的封面做首屏大图 */
  homestay?: HeroHomestay | null;
}

const props = withDefaults(defineProps<Props>(), {
  banners: () => [],
  homestay: null,
});

const emit = defineEmits<{
  bannerClick: [banner: Banner];
  homestayClick: [homestay: HeroHomestay];
}>();

const DEFAULT_TITLE = "住进当地人的家";
const DEFAULT_SUBTITLE = "从城市公寓到山间小院，选好日期和人数就能找房";

// Unsplash 外链默认 800 宽，首屏铺满时会糊，按需换成大图
const toHeroSize = (url: string) => {
  try {
    const parsed = new URL(url);
    if (parsed.hostname === "images.unsplash.com") {
      parsed.searchParams.set("w", "1920");
      parsed.searchParams.delete("h");
      return parsed.toString();
    }
  } catch {
    // 相对路径等，原样使用
  }
  return url;
};

const imageBanner = computed(() => props.banners.find((b) => !!b.imageUrl) || null);

const hero = computed(() => {
  const banner = imageBanner.value;
  if (banner) {
    return {
      image: toHeroSize(banner.imageUrl as string),
      title: banner.title || DEFAULT_TITLE,
      subtitle: banner.subtitle || DEFAULT_SUBTITLE,
      caption: banner.linkUrl ? "查看活动详情" : "",
    };
  }
  const homestay = props.homestay;
  if (homestay?.coverImage) {
    const price = Number(homestay.price);
    const parts = [
      homestay.title,
      Number.isFinite(price) && price > 0 ? `¥${Math.round(price)}/晚` : "",
    ];
    return {
      image: toHeroSize(getHomestayImageUrl(homestay.coverImage)),
      title: DEFAULT_TITLE,
      subtitle: DEFAULT_SUBTITLE,
      caption: `图中房源：${parts.filter(Boolean).join("，")}`,
    };
  }
  return { image: "", title: DEFAULT_TITLE, subtitle: DEFAULT_SUBTITLE, caption: "" };
});

const handleCaptionClick = () => {
  if (imageBanner.value) emit("bannerClick", imageBanner.value);
  else if (props.homestay) emit("homestayClick", props.homestay);
};
</script>

<style scoped>
.hero {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  background: var(--color-primary-900);
  color: white;
}

.hero--no-photo {
  background: linear-gradient(
    120deg,
    var(--color-neutral-900) 0%,
    var(--color-primary-900) 60%,
    var(--color-primary-700) 100%
  );
}

.hero-photo {
  position: absolute;
  inset: 0;
  z-index: -2;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 左侧压暗保证文字可读，右侧保留照片本身 */
.hero-shade {
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    linear-gradient(90deg, rgba(31, 28, 25, 0.72) 0%, rgba(31, 28, 25, 0.38) 45%, transparent 75%),
    linear-gradient(0deg, rgba(31, 28, 25, 0.45) 0%, transparent 40%);
}

.hero-inner {
  position: relative;
  max-width: 1280px;
  margin: 0 auto;
  /* 底部留白较小：SearchBar 自带 24px 下边距 */
  padding: var(--space-16) var(--space-6) var(--space-4);
  min-height: 440px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: var(--space-8);
}

.hero-copy {
  max-width: 560px;
}

.hero-title {
  /* 全局 h1-h6 设了深色，深色底上要继承容器的白字 */
  color: inherit;
  font-family: var(--font-display);
  font-size: clamp(2.25rem, 4.5vw, 3.5rem);
  font-weight: 700;
  line-height: 1.1;
  margin: 0 0 var(--space-3);
  letter-spacing: 0.02em;
  text-shadow: 0 2px 24px rgba(0, 0, 0, 0.25);
}

.hero-desc {
  margin: 0;
  font-size: var(--text-body-lg);
  line-height: 1.6;
  opacity: 0.92;
}

/* 与 SearchBar 内部 900px 宽度一致，搜索框左边缘和标题对齐 */
.hero-search {
  max-width: 900px;
  color: var(--color-neutral-900);
}

.hero-caption {
  position: absolute;
  right: var(--space-6);
  bottom: var(--space-4);
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  max-width: min(420px, 60%);
  padding: var(--space-1) var(--space-3);
  border: none;
  border-radius: var(--radius-full);
  background: rgba(31, 28, 25, 0.5);
  color: rgba(255, 255, 255, 0.92);
  font-family: inherit;
  font-size: var(--text-body-sm);
  cursor: pointer;
  backdrop-filter: blur(6px);
  transition: background-color var(--duration-fast) var(--ease-out);
}

.hero-caption:hover {
  background: rgba(31, 28, 25, 0.72);
}

.hero-caption:focus-visible {
  outline: 2px solid white;
  outline-offset: 2px;
}

.hero-caption-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .hero-inner {
    min-height: 0;
    padding: var(--space-10) var(--space-4) 0;
    gap: var(--space-5);
  }

  .hero-shade {
    background: linear-gradient(0deg, rgba(31, 28, 25, 0.78) 0%, rgba(31, 28, 25, 0.35) 100%);
  }

  .hero-desc {
    font-size: var(--text-body);
  }

  /* 手机上说明条放到标题上方，避免压住搜索卡片 */
  .hero-caption {
    position: static;
    order: -1;
    align-self: flex-start;
    max-width: 100%;
  }
}
</style>
