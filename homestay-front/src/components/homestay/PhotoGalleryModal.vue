<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="photo-gallery-modal" @click="handleBackdropClick">
        <!-- 顶部工具栏 -->
        <div class="gallery-toolbar">
          <span class="gallery-title">{{ title }}</span>
          <el-button class="close-btn" text circle @click.stop="close">
            <el-icon :size="24">
              <Close />
            </el-icon>
          </el-button>
        </div>

        <!-- 左右导航 -->
        <button v-if="currentIndex > 0" class="nav-btn prev" @click.stop="prev">
          <el-icon :size="32">
            <ArrowLeft />
          </el-icon>
        </button>
        <button v-if="currentIndex < images.length - 1" class="nav-btn next" @click.stop="next">
          <el-icon :size="32">
            <ArrowRight />
          </el-icon>
        </button>

        <!-- 主图区域 -->
        <div
          class="gallery-main"
          @click.stop
          @touchstart="handleTouchStart"
          @touchend="handleTouchEnd"
        >
          <Transition name="slide" mode="out-in">
            <img
              :key="currentIndex"
              :src="currentImage"
              :alt="`${title} - 图片 ${currentIndex + 1}`"
              class="gallery-image"
              @error="handleImageError"
            />
          </Transition>
        </div>

        <!-- 底部信息栏 -->
        <div class="gallery-footer">
          <span class="gallery-counter">{{ currentIndex + 1 }} / {{ images.length }}</span>

          <!-- 缩略图条 -->
          <div class="thumbnail-strip" ref="thumbStripRef">
            <div
              v-for="(img, idx) in images"
              :key="idx"
              class="thumb-item"
              :class="{ active: idx === currentIndex }"
              @click.stop="goTo(idx)"
            >
              <img :src="img" :alt="`缩略图 ${idx + 1}`" />
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from "vue";
import { Close, ArrowLeft, ArrowRight } from "@element-plus/icons-vue";

// Props
interface Props {
  visible: boolean;
  images: string[];
  title?: string;
  initialIndex?: number;
}

const props = withDefaults(defineProps<Props>(), {
  title: "房源照片",
  initialIndex: 0,
});

// Emits
const emit = defineEmits<{
  "update:visible": [value: boolean];
}>();

// State
const currentIndex = ref(props.initialIndex);
const touchStartX = ref(0);
const touchEndX = ref(0);
const thumbStripRef = ref<HTMLDivElement | null>(null);

// Computed
const currentImage = computed(() => {
  if (props.images.length === 0) return "";
  return props.images[currentIndex.value] || "";
});

// Methods
const close = () => {
  emit("update:visible", false);
};

const prev = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--;
    scrollThumbIntoView();
  }
};

const next = () => {
  if (currentIndex.value < props.images.length - 1) {
    currentIndex.value++;
    scrollThumbIntoView();
  }
};

const goTo = (index: number) => {
  currentIndex.value = index;
  scrollThumbIntoView();
};

const scrollThumbIntoView = () => {
  nextTick(() => {
    const strip = thumbStripRef.value;
    if (!strip) return;
    const activeThumb = strip.querySelector(".thumb-item.active") as HTMLElement;
    if (activeThumb) {
      activeThumb.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
    }
  });
};

const handleBackdropClick = () => {
  close();
};

const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  img.style.display = "none";
  const placeholder = document.createElement("div");
  placeholder.className = "image-error-placeholder";
  placeholder.textContent = "图片加载失败";
  img.parentElement?.appendChild(placeholder);
};

// Touch handling
const handleTouchStart = (e: TouchEvent) => {
  touchStartX.value = e.changedTouches[0].screenX;
};

const handleTouchEnd = (e: TouchEvent) => {
  touchEndX.value = e.changedTouches[0].screenX;
  const diff = touchStartX.value - touchEndX.value;
  const threshold = 50;
  if (Math.abs(diff) > threshold) {
    if (diff > 0) {
      next();
    } else {
      prev();
    }
  }
};

// Keyboard navigation
const handleKeydown = (e: KeyboardEvent) => {
  if (!props.visible) return;
  switch (e.key) {
    case "Escape":
      close();
      break;
    case "ArrowLeft":
      prev();
      break;
    case "ArrowRight":
      next();
      break;
  }
};

// Watch
watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      currentIndex.value = props.initialIndex;
      scrollThumbIntoView();
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
  }
);

watch(
  () => props.initialIndex,
  (newVal) => {
    if (props.visible) {
      currentIndex.value = newVal;
    }
  }
);

// Lifecycle
onMounted(() => {
  document.addEventListener("keydown", handleKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", handleKeydown);
  document.body.style.overflow = "";
});
</script>

<style scoped>
.photo-gallery-modal {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background-color: rgba(0, 0, 0, 0.95);
  display: flex;
  flex-direction: column;
  user-select: none;
}

/* 顶部工具栏 */
.gallery-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  flex-shrink: 0;
}

.gallery-title {
  color: #fff;
  font-size: 16px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 16px;
}

.close-btn {
  color: #fff;
  flex-shrink: 0;
}

.close-btn:hover {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.1);
}

/* 主图区域 */
.gallery-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 64px;
  position: relative;
  overflow: hidden;
  touch-action: pan-y;
}

.gallery-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
}

.image-error-placeholder {
  color: #999;
  font-size: 16px;
  padding: 40px;
}

/* 导航按钮 */
.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  background-color: rgba(255, 255, 255, 0.15);
  border: none;
  color: #fff;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s ease;
  z-index: 10;
}

.nav-btn:hover {
  background-color: rgba(255, 255, 255, 0.3);
}

.nav-btn.prev {
  left: 16px;
}

.nav-btn.next {
  right: 16px;
}

/* 底部信息栏 */
.gallery-footer {
  flex-shrink: 0;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.gallery-counter {
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
}

/* 缩略图条 */
.thumbnail-strip {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  max-width: 100%;
  padding: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.3) transparent;
}

.thumbnail-strip::-webkit-scrollbar {
  height: 4px;
}

.thumbnail-strip::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
}

.thumb-item {
  flex-shrink: 0;
  width: 72px;
  height: 48px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  opacity: 0.6;
  transition: all 0.2s ease;
}

.thumb-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-item.active {
  border-color: #fff;
  opacity: 1;
}

.thumb-item:hover {
  opacity: 0.9;
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.25s ease;
}

.slide-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.slide-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

/* 响应式 */
@media (max-width: 768px) {
  .gallery-toolbar {
    padding: 12px 16px;
  }

  .gallery-main {
    padding: 0 48px;
  }

  .nav-btn {
    width: 40px;
    height: 40px;
  }

  .nav-btn.prev {
    left: 8px;
  }

  .nav-btn.next {
    right: 8px;
  }

  .thumb-item {
    width: 56px;
    height: 40px;
  }
}
</style>
