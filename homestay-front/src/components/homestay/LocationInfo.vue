<template>
  <div class="location-section">
    <h2>位置信息</h2>

    <!-- 地址信息 -->
    <div class="address-info">
      <div class="primary-location">
        <el-icon class="location-icon">
          <Location />
        </el-icon>
        <div class="location-text">
          <div class="main-address">{{ formattedLocation }}</div>
          <div class="detail-address" v-if="addressDetail">
            {{ addressDetail }}
          </div>
        </div>
      </div>

      <div class="location-features" v-if="distanceFromCenter">
        <div class="distance-info">
          <el-icon>
            <House />
          </el-icon>
          <span>距离市中心 {{ distanceFromCenter }} 公里</span>
        </div>
      </div>
    </div>

    <!-- 地图区域 -->
    <div class="map-section">
      <div class="map-container" @click="$emit('open-map')">
        <!-- 加载状态 -->
        <div v-if="mapLoading" class="map-loading">
          <el-skeleton :rows="5" animated />
          <div class="loading-text">正在加载地图...</div>
        </div>

        <!-- 真实地图 -->
        <div v-else-if="hasLocation && staticMapUrl" class="map-placeholder">
          <div class="map-overlay">
            <el-icon class="map-icon">
              <Location />
            </el-icon>
            <div class="map-text">
              <div class="map-title">查看详细地图</div>
              <div class="map-subtitle">点击打开交互式地图</div>
            </div>
          </div>
          <img
            :src="staticMapUrl"
            alt="房源位置地图"
            class="map-image"
            @error="(event) => $emit('map-error', event)"
          />
        </div>

        <div v-else class="map-placeholder">
          <div class="map-placeholder-content" role="status">
            <span class="placeholder-text">地图加载失败</span>
          </div>
        </div>
      </div>
    </div>

    <p v-if="nearbyError" role="status">{{ nearbyError }}</p>

    <!-- 周边信息 -->
    <div class="nearby-info" v-if="nearbyPlaces.length > 0">
      <h3>周边设施</h3>
      <div class="nearby-grid">
        <div v-for="(place, index) in nearbyPlaces" :key="index" class="nearby-item">
          <el-icon class="facility-icon">
            <component :is="getFacilityIcon(place.type)" />
          </el-icon>
          <div class="facility-info">
            <div class="facility-name">{{ place.name }}</div>
            <div class="facility-distance">{{ formatDistance(place.distance) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch } from "vue";
import { Location, House, Check } from "@element-plus/icons-vue";

// Types
interface NearbyPlace {
  name: string;
  type: string;
  distance: number;
  address: string;
}

// Props
interface Props {
  formattedLocation: string;
  addressDetail?: string;
  distanceFromCenter?: number;
  mapLoading?: boolean;
  hasLocation?: boolean;
  staticMapUrl?: string;
  nearbyPlaces: NearbyPlace[];
  nearbyError?: string;
}

const props = withDefaults(defineProps<Props>(), {
  mapLoading: false,
  hasLocation: false,
  staticMapUrl: "",
  nearbyPlaces: () => [],
  nearbyError: "",
});

// Emits
defineEmits<{
  "open-map": [];
  "map-error": [event: Event];
}>();

// Methods
const getFacilityIcon = (type: string) => {
  switch (type) {
    case "地铁站":
    case "交通":
      return Location;
    case "商场":
    case "购物":
      return House;
    default:
      return Check;
  }
};

const formatDistance = (distance: number): string => {
  if (distance < 1000) {
    return `${Math.round(distance)}米`;
  } else {
    return `${(distance / 1000).toFixed(1)}公里`;
  }
};

// 监视 staticMapUrl 变化
watch(
  () => props.staticMapUrl,
  (newUrl, oldUrl) => {
    console.log("staticMapUrl 变化:", {
      oldUrl: oldUrl || "(empty)",
      newUrl: newUrl || "(empty)",
      newUrlLength: newUrl?.length || 0,
    });
  }
);
</script>

<style scoped>
.location-section {
  margin: 48px 0;
}

.location-section h2 {
  font-size: 22px;
  margin-bottom: 16px;
  font-weight: 600;
}

.address-info {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.primary-location {
  display: flex;
  align-items: center;
  gap: 8px;
}

.location-icon {
  font-size: 24px;
  color: #409eff;
  flex-shrink: 0;
}

.location-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.main-address {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.detail-address {
  font-size: 14px;
  color: #717171;
}

.location-features {
  display: flex;
  align-items: center;
  gap: 16px;
}

.distance-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 14px;
}

.map-section {
  margin-bottom: 20px;
}

.map-container {
  width: 100%;
  height: 400px;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.map-container:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.map-container:hover .map-overlay {
  opacity: 1;
}

.map-placeholder {
  position: relative;
  width: 100%;
  height: 100%;
}

.map-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
  transition: opacity 0.3s ease;
  opacity: 0;
  pointer-events: none;
  z-index: 2;
}

.map-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.map-text {
  text-align: center;
}

.map-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.map-subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}

.map-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.map-placeholder-content {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  border: 2px dashed #d0d0d0;
}

.placeholder-text {
  color: #999;
  font-size: 18px;
}

.map-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background-color: rgba(255, 255, 255, 0.8);
  border-radius: 12px;
  height: 100%;
}

.loading-text {
  font-size: 14px;
  color: #717171;
  margin-top: 12px;
}

.nearby-info {
  margin-top: 24px;
}

.nearby-info h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
}

.nearby-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 16px;
}

.nearby-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
  transition:
    background-color 0.3s ease,
    transform 0.2s ease;
}

.nearby-item:hover {
  background-color: #e9ecef;
  transform: translateY(-2px);
}

.facility-icon {
  font-size: 24px;
  color: #409eff;
  flex-shrink: 0;
}

.facility-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.facility-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.facility-distance {
  font-size: 14px;
  color: #717171;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .address-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .map-container {
    height: 300px;
  }

  .nearby-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .nearby-item {
    padding: 12px;
  }

  .facility-name {
    font-size: 14px;
  }

  .facility-distance {
    font-size: 12px;
  }

  .map-title {
    font-size: 14px;
  }

  .map-subtitle {
    font-size: 12px;
  }
}

/* 加载动画 */
.map-loading {
  background: linear-gradient(45deg, #f8f9fa, #e9ecef);
}

/* 设施项目动画 */
.nearby-item {
  opacity: 0;
  transform: translateY(20px);
  animation: fadeInUp 0.6s ease forwards;
}

.nearby-item:nth-child(1) {
  animation-delay: 0.1s;
}

.nearby-item:nth-child(2) {
  animation-delay: 0.2s;
}

.nearby-item:nth-child(3) {
  animation-delay: 0.3s;
}

.nearby-item:nth-child(4) {
  animation-delay: 0.4s;
}

.nearby-item:nth-child(5) {
  animation-delay: 0.5s;
}

.nearby-item:nth-child(6) {
  animation-delay: 0.6s;
}

@keyframes fadeInUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
