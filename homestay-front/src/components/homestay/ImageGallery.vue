<template>
  <div class="image-gallery-new" v-if="allImages.length > 0">
    <!-- 5+ images -->
    <div class="gallery-layout-5plus" v-if="allImages.length >= 5">
      <div class="main-image-wrapper" @click="emit('show-all-photos', 0)">
        <div class="image-container">
          <img
            :src="mainImageUrl"
            :alt="homestayTitle"
            class="main-img"
            @error="handleImageError"
            @load="handleImageLoad"
          />
          <div class="image-loading" v-if="imageLoading">
            <el-skeleton animated />
          </div>
        </div>
        <div class="image-overlay"></div>
      </div>
      <div class="grid-image-wrapper">
        <div
          v-for="(image, index) in gridImages.slice(0, 4)"
          :key="index"
          class="grid-img-item"
          @click="emit('show-all-photos', index + 1)"
        >
          <div class="image-container">
            <img
              :src="image"
              :alt="`${homestayTitle} - 图片 ${index + 2}`"
              class="grid-img"
              @error="handleImageError"
              @load="handleImageLoad"
              loading="lazy"
            />
          </div>
          <div class="image-overlay"></div>
        </div>
      </div>
    </div>

    <!-- 4 images -->
    <div v-else-if="allImages.length === 4" class="gallery-layout-4">
      <div
        v-for="(image, index) in allImages"
        :key="index"
        class="grid-img-item-4"
        @click="emit('show-all-photos', index)"
      >
        <div class="image-container">
          <img
            :src="image"
            :alt="`${homestayTitle} - 图片 ${index + 1}`"
            class="grid-img-4"
            @error="handleImageError"
            @load="handleImageLoad"
            loading="lazy"
          />
        </div>
        <div class="image-overlay"></div>
      </div>
    </div>

    <!-- 3 images -->
    <div v-else-if="allImages.length === 3" class="gallery-layout-3">
      <div
        v-for="(image, index) in allImages"
        :key="index"
        class="grid-img-item-3"
        @click="emit('show-all-photos', index)"
      >
        <div class="image-container">
          <img
            :src="image"
            :alt="`${homestayTitle} - 图片 ${index + 1}`"
            class="grid-img-3"
            @error="handleImageError"
            @load="handleImageLoad"
            loading="lazy"
          />
        </div>
        <div class="image-overlay"></div>
      </div>
    </div>

    <!-- 2 images -->
    <div v-else-if="allImages.length === 2" class="gallery-layout-2">
      <div
        v-for="(image, index) in allImages"
        :key="index"
        class="grid-img-item-2"
        @click="emit('show-all-photos', index)"
      >
        <div class="image-container">
          <img
            :src="image"
            :alt="`${homestayTitle} - 图片 ${index + 1}`"
            class="grid-img-2"
            @error="handleImageError"
            @load="handleImageLoad"
            loading="lazy"
          />
        </div>
        <div class="image-overlay"></div>
      </div>
    </div>

    <!-- 1 image -->
    <div
      v-else-if="allImages.length === 1"
      class="gallery-layout-1"
      @click="emit('show-all-photos', 0)"
    >
      <div class="main-image-wrapper-single">
        <div class="image-container">
          <img
            :src="mainImageUrl"
            :alt="homestayTitle"
            class="main-img-single"
            @error="handleImageError"
            @load="handleImageLoad"
            loading="lazy"
          />
        </div>
        <div class="image-overlay"></div>
      </div>
    </div>

    <!-- '查看全部照片' 按钮 -->
    <el-button
      class="view-all-photos"
      @click="emit('show-all-photos', 0)"
      v-if="allImages.length > 1"
    >
      <el-icon>
        <Camera />
      </el-icon>
      查看全部 {{ totalImageCount }} 张照片
    </el-button>
  </div>

  <!-- Fallback if no images -->
  <div v-else class="no-images-placeholder">
    <el-empty description="暂无房源图片">
      <el-button @click="$emit('go-home')">返回首页</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { Camera } from "@element-plus/icons-vue";

// Props
interface Props {
  allImages: string[];
  homestayTitle: string;
  totalImageCount?: number;
}

const props = withDefaults(defineProps<Props>(), {
  totalImageCount: 0,
});

// Emits
const emit = defineEmits<{
  "show-all-photos": [index: number];
  "go-home": [];
}>();

// Reactive data
const imageLoading = ref(false);
const imageErrors = ref(new Set<string>());

// Computed properties
const mainImageUrl = computed(() =>
  props.allImages.length > 0
    ? props.allImages[0]
    : "https://via.placeholder.com/800x600?text=No+Image"
);

const gridImages = computed(() => props.allImages.slice(1));

// Methods
const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  const originalSrc = img.src;

  console.warn("图片加载失败:", originalSrc);
  imageErrors.value.add(originalSrc);

  const fallbackImages = [
    "https://via.placeholder.com/800x600/f5f5f5/cccccc?text=房源图片",
    "https://picsum.photos/800/600?random=home",
    "https://dummyimage.com/800x600/f5f5f5/999999&text=No+Image",
  ];

  const currentFallbackIndex = fallbackImages.findIndex((url) =>
    img.src.includes(url.split("/")[2])
  );
  const nextFallbackIndex = currentFallbackIndex + 1;

  if (nextFallbackIndex < fallbackImages.length) {
    img.src = fallbackImages[nextFallbackIndex];
  } else {
    img.style.display = "none";
    const container = img.parentElement;
    if (container) {
      container.classList.add("image-error");
      container.innerHTML = `
                <div class="error-placeholder">
                    <i class="el-icon-picture-outline"></i>
                    <span>图片加载失败</span>
                </div>
            `;
    }
  }
};

const handleImageLoad = (event: Event) => {
  const img = event.target as HTMLImageElement;
  console.log("图片加载成功:", img.src);

  imageLoading.value = false;
  img.style.opacity = "1";
  img.classList.add("loaded");

  const container = img.parentElement;
  if (container) {
    container.classList.remove("image-loading");
  }
};
</script>

<style scoped>
.image-gallery-new {
  position: relative;
  margin-bottom: 32px;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.gallery-layout-5plus {
  display: grid;
  grid-template-columns: 2fr 1fr;
  grid-template-rows: 500px;
  gap: 4px;
  height: 500px;
}

.main-image-wrapper {
  grid-column: 1 / 2;
  grid-row: 1 / 2;
  position: relative;
  overflow: hidden;
  height: 100%;
}

.main-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.main-image-wrapper:hover .main-img {
  transform: scale(1.03);
}

.grid-image-wrapper {
  grid-column: 2 / 3;
  grid-row: 1 / 2;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 4px;
  height: 100%;
}

.grid-img-item {
  position: relative;
  overflow: hidden;
  height: 100%;
}

.grid-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.grid-img-item:hover .grid-img {
  transform: scale(1.03);
}

.image-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.1);
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.main-image-wrapper:hover .image-overlay,
.grid-img-item:hover .image-overlay {
  opacity: 1;
}

.view-all-photos {
  position: absolute;
  bottom: 16px;
  right: 16px;
  background-color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-weight: 500;
  font-size: 14px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
  transition: all 0.2s ease;
  z-index: 2;
}

.view-all-photos:hover {
  background-color: white;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.25);
  transform: scale(1.02);
}

/* 4 images layout */
.gallery-layout-4 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 250px 250px;
  gap: 4px;
  height: 504px;
}

.grid-img-item-4 {
  position: relative;
  overflow: hidden;
  height: 100%;
}

.grid-img-4 {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.grid-img-item-4:hover .grid-img-4 {
  transform: scale(1.03);
}

/* 3 images layout */
.gallery-layout-3 {
  display: grid;
  grid-template-columns: 2fr 1fr;
  grid-template-rows: 250px 250px;
  gap: 4px;
  height: 504px;
}

.grid-img-item-3:first-child {
  grid-column: 1 / 2;
  grid-row: 1 / 3;
}

.grid-img-item-3:nth-child(2) {
  grid-column: 2 / 3;
  grid-row: 1 / 2;
}

.grid-img-item-3:nth-child(3) {
  grid-column: 2 / 3;
  grid-row: 2 / 3;
}

.grid-img-item-3 {
  position: relative;
  overflow: hidden;
  height: 100%;
}

.grid-img-3 {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.grid-img-item-3:hover .grid-img-3 {
  transform: scale(1.03);
}

/* 2 images layout */
.gallery-layout-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 400px;
  gap: 4px;
  height: 400px;
}

.grid-img-item-2 {
  position: relative;
  overflow: hidden;
  height: 100%;
}

.grid-img-2 {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.grid-img-item-2:hover .grid-img-2 {
  transform: scale(1.03);
}

/* 1 image layout */
.gallery-layout-1 {
  display: block;
  height: 400px;
}

.main-image-wrapper-single {
  position: relative;
  overflow: hidden;
  height: 100%;
  width: 100%;
}

.main-img-single {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.main-image-wrapper-single:hover .main-img-single {
  transform: scale(1.03);
}

.image-container {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-container img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  opacity: 1;
  transition:
    opacity 0.3s ease,
    transform 0.3s ease;
}

.image-loading {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  z-index: 1;
}

.image-error {
  background-color: #f5f5f5;
  display: flex !important;
  align-items: center;
  justify-content: center;
}

.error-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #999;
  font-size: 14px;
}

.error-placeholder i {
  font-size: 24px;
}

.no-images-placeholder {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f7fa;
  border-radius: 12px;
  margin-bottom: 32px;
}

/* 响应式设计 */
@media (max-width: 992px) {
  .gallery-layout-5plus {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto;
    height: auto;
  }

  .main-image-wrapper {
    height: 400px;
  }

  .grid-image-wrapper {
    height: 250px;
    grid-template-columns: repeat(4, 1fr);
    grid-template-rows: 1fr;
  }
}

@media (max-width: 768px) {
  .gallery-layout-5plus {
    height: auto;
    display: flex;
    flex-direction: column;
  }

  .main-image-wrapper {
    height: 300px;
    width: 100%;
  }

  .grid-image-wrapper {
    display: none;
  }

  .gallery-layout-3,
  .gallery-layout-2,
  .gallery-layout-4 {
    grid-template-columns: 1fr;
    grid-template-rows: 300px;
    height: 300px;
  }

  .grid-img-item-3:first-child {
    grid-column: 1 / 2;
    grid-row: 1 / 2;
  }

  .grid-img-item-3:nth-child(2),
  .grid-img-item-3:nth-child(3) {
    display: none;
  }

  .grid-img-item-2:nth-child(2),
  .grid-img-item-4:nth-child(2),
  .grid-img-item-4:nth-child(3),
  .grid-img-item-4:nth-child(4) {
    display: none;
  }

  .gallery-layout-1 {
    height: 300px;
  }

  .view-all-photos {
    bottom: 10px;
    right: 10px;
    padding: 6px 10px;
    font-size: 12px;
  }
}
</style>
