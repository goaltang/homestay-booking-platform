<template>
  <section class="map-cta-section">
    <div class="map-cta-card" @click="goToMap">
      <!-- 左侧地图预览 -->
      <div class="map-preview">
        <svg class="map-svg" viewBox="0 0 400 280" preserveAspectRatio="xMidYMid slice">
          <!-- 地图背景 -->
          <rect width="400" height="280" fill="#f0f4f8" />
          <!-- 水域 -->
          <ellipse cx="320" cy="60" rx="60" ry="40" fill="#dbeafe" opacity="0.6" />
          <ellipse cx="60" cy="220" rx="40" ry="30" fill="#dbeafe" opacity="0.5" />
          <!-- 公园绿地 -->
          <rect x="40" y="40" width="80" height="60" rx="8" fill="#bbf7d0" opacity="0.5" />
          <rect x="260" y="160" width="70" height="50" rx="6" fill="#bbf7d0" opacity="0.4" />
          <!-- 道路 -->
          <g stroke="#cbd5e1" stroke-width="3" fill="none" stroke-linecap="round">
            <!-- 主干道 -->
            <path d="M0 140 Q100 135 200 140 Q300 145 400 140" />
            <path d="M200 0 Q195 80 200 140 Q205 210 200 280" />
            <!-- 次干道 -->
            <path d="M0 80 Q80 85 160 80 Q240 75 320 80 Q360 82 400 80" stroke-width="2" />
            <path d="M0 200 Q100 195 200 200 Q300 205 400 200" stroke-width="2" />
            <path d="M80 0 Q75 60 80 140 Q85 220 80 280" stroke-width="2" />
            <path d="M320 0 Q315 70 320 140 Q325 210 320 280" stroke-width="2" />
            <!-- 小路 -->
            <path d="M40 0 Q35 40 40 80" stroke-width="1.5" />
            <path d="M280 0 Q275 40 280 80" stroke-width="1.5" />
            <path d="M120 140 Q125 170 120 200" stroke-width="1.5" />
            <path d="M280 140 Q285 170 280 200" stroke-width="1.5" />
            <path d="M0 40 Q50 45 80 40" stroke-width="1.5" />
            <path d="M320 240 Q360 245 400 240" stroke-width="1.5" />
          </g>
          <!-- 建筑区块 -->
          <g fill="#e2e8f0" opacity="0.6">
            <rect x="100" y="50" width="50" height="40" rx="3" />
            <rect x="180" y="60" width="60" height="35" rx="3" />
            <rect x="100" y="110" width="40" height="50" rx="3" />
            <rect x="160" y="170" width="55" height="40" rx="3" />
            <rect x="240" y="100" width="45" height="45" rx="3" />
            <rect x="50" y="170" width="50" height="35" rx="3" />
            <rect x="340" y="120" width="40" height="50" rx="3" />
            <rect x="340" y="200" width="45" height="35" rx="3" />
          </g>
          <!-- 房源标记点 -->
          <g v-for="(pin, i) in pins" :key="i">
            <circle :cx="pin.x" :cy="pin.y" r="14" fill="white" opacity="0.9" />
            <circle :cx="pin.x" :cy="pin.y" r="5" :fill="pin.color" />
            <circle :cx="pin.x" :cy="pin.y" r="10" :fill="pin.color" opacity="0.2">
              <animate
                attributeName="r"
                values="10;16;10"
                dur="2s"
                :begin="`${i * 0.3}s`"
                repeatCount="indefinite"
              />
              <animate
                attributeName="opacity"
                values="0.3;0;0.3"
                dur="2s"
                :begin="`${i * 0.3}s`"
                repeatCount="indefinite"
              />
            </circle>
          </g>
        </svg>
        <!-- 左下角小标签 -->
        <div class="map-badge">
          <el-icon :size="14"><MapLocation /></el-icon>
          探索周边房源
        </div>
      </div>

      <!-- 右侧文案 -->
      <div class="map-content">
        <h3 class="map-title">地图找房</h3>
        <p class="map-desc">
          在地图上直观查看房源分布，拖拽地图按视口范围筛选，或使用地标、POI 搜索找到理想住宿。
        </p>
        <div class="map-features">
          <div class="map-feature">
            <el-icon :size="18" color="var(--color-primary-600)"><Location /></el-icon>
            <span>视口范围筛选</span>
          </div>
          <div class="map-feature">
            <el-icon :size="18" color="var(--color-secondary-600)"><Compass /></el-icon>
            <span>周边房源搜索</span>
          </div>
          <div class="map-feature">
            <el-icon :size="18" color="var(--color-primary-600)"><ZoomIn /></el-icon>
            <span>地标 / POI 定位</span>
          </div>
        </div>
        <el-button type="primary" class="map-btn" round>
          打开地图
          <el-icon class="btn-icon"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { MapLocation, ArrowRight, Location, Compass, ZoomIn } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";

const router = useRouter();

const pins = [
  { x: 130, y: 110, color: "var(--color-primary-500)" },
  { x: 210, y: 80, color: "var(--color-secondary-500)" },
  { x: 180, y: 190, color: "var(--color-primary-500)" },
  { x: 270, y: 130, color: "var(--color-secondary-500)" },
  { x: 80, y: 190, color: "var(--color-primary-500)" },
  { x: 350, y: 150, color: "var(--color-primary-500)" },
  { x: 360, y: 220, color: "var(--color-secondary-500)" },
  { x: 120, y: 70, color: "var(--color-primary-500)" },
];

const goToMap = () => {
  router.push("/map-search");
};
</script>

<style scoped>
.map-cta-section {
  margin-bottom: 48px;
}

.map-cta-card {
  display: flex;
  background: var(--color-surface-elevated);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-neutral-200);
  transition:
    transform var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
  min-height: 280px;
}

.map-cta-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
  border-color: var(--color-primary-200);
}

/* 左侧地图 */
.map-preview {
  position: relative;
  flex: 1;
  min-width: 0;
  background: var(--color-background-warm);
}

.map-svg {
  width: 100%;
  height: 100%;
  display: block;
}

.map-badge {
  position: absolute;
  bottom: 16px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--color-surface-elevated);
  padding: 8px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-neutral-700);
  box-shadow: var(--shadow-md);
}

/* 右侧文案 */
.map-content {
  flex: 0 0 360px;
  padding: 40px 36px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.map-title {
  margin: 0 0 10px;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-neutral-900);
  letter-spacing: 0;
}

.map-desc {
  margin: 0 0 24px;
  font-size: 14px;
  color: var(--color-neutral-600);
  line-height: 1.6;
}

.map-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 28px;
}

.map-feature {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-neutral-700);
}

.map-btn {
  align-self: flex-start;
  background: var(--color-primary-500);
  border: none;
  font-weight: 600;
  padding: 10px 24px;
  font-size: 14px;
  transition:
    transform var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.map-btn:hover {
  transform: translateY(-1px);
  background: var(--color-primary-600);
  box-shadow: 0 8px 24px rgba(212, 95, 46, 0.24);
}

.btn-icon {
  margin-left: 4px;
}

/* 响应式 */
@media (max-width: 900px) {
  .map-cta-card {
    flex-direction: column;
  }

  .map-preview {
    height: 200px;
  }

  .map-content {
    flex: none;
    padding: 28px 24px;
  }

  .map-title {
    font-size: 20px;
  }
}
</style>
