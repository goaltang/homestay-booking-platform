<template>
  <section class="host-cta-section">
    <div class="host-cta-card" @click="goToHost">
      <div class="host-visual">
        <!-- 装饰性背景元素 -->
        <div class="host-decor host-decor-1" />
        <div class="host-decor host-decor-2" />
        <div class="host-decor host-decor-3" />
      </div>
      <div class="host-content">
        <div class="host-badge">
          <el-icon :size="14"><HomeFilled /></el-icon>
          成为房东
        </div>
        <h2 class="host-title">
          分享你的空间
          <br />
          <span class="host-highlight">获得额外收益</span>
        </h2>
        <p class="host-desc">
          无论是一间空房还是整栋别墅，都可以成为独特的民宿体验。已有数千位房东通过平台获得稳定收入。
        </p>
        <div class="host-stats">
          <div class="host-stat">
            <span class="host-stat-num">¥8,000+</span>
            <span class="host-stat-label">月均收入</span>
          </div>
          <div class="host-stat-divider" />
          <div class="host-stat">
            <span class="host-stat-num">48h</span>
            <span class="host-stat-label">快速上架</span>
          </div>
          <div class="host-stat-divider" />
          <div class="host-stat">
            <span class="host-stat-num">零门槛</span>
            <span class="host-stat-label">免费入驻</span>
          </div>
        </div>
        <el-button type="primary" class="host-btn" round size="large">
          立即成为房东
          <el-icon class="btn-icon"><ArrowRight /></el-icon>
        </el-button>
      </div>
      <div class="host-image">
        <div class="host-img-placeholder">
          <el-icon :size="64" color="rgba(255,255,255,0.3)"><HomeFilled /></el-icon>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { HomeFilled, ArrowRight } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";

const router = useRouter();
const goToHost = () => {
  router.push("/host/onboarding");
};
</script>

<style scoped>
.host-cta-section {
  margin-bottom: 48px;
}

.host-cta-card {
  position: relative;
  display: flex;
  align-items: center;
  background:
    radial-gradient(circle at 86% 18%, rgba(249, 224, 210, 0.16), transparent 24%),
    linear-gradient(
      135deg,
      var(--color-neutral-900) 0%,
      var(--color-primary-900) 52%,
      var(--color-primary-700) 100%
    );
  border-radius: var(--radius-md);
  overflow: hidden;
  padding: 48px;
  cursor: pointer;
  transition:
    transform var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out);
  min-height: 320px;
}

.host-cta-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 52px rgba(31, 28, 25, 0.22);
}

/* 装饰背景 */
.host-visual {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.host-decor {
  position: absolute;
  border-radius: 50%;
  opacity: 0.08;
}

.host-decor-1 {
  width: 400px;
  height: 400px;
  background: var(--color-primary-300);
  top: -100px;
  right: -100px;
}

.host-decor-2 {
  width: 300px;
  height: 300px;
  background: var(--color-secondary-300);
  bottom: -80px;
  left: 30%;
}

.host-decor-3 {
  width: 200px;
  height: 200px;
  background: var(--color-primary-100);
  top: 40%;
  left: -60px;
}

/* 内容 */
.host-content {
  position: relative;
  z-index: 1;
  flex: 1;
  color: white;
}

.host-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 20px;
}

.host-title {
  font-size: 32px;
  font-weight: 800;
  line-height: 1.2;
  margin: 0 0 12px;
  letter-spacing: 0;
}

.host-highlight {
  color: var(--color-primary-200);
}

.host-desc {
  font-size: 15px;
  line-height: 1.7;
  opacity: 0.8;
  margin: 0 0 24px;
  max-width: 440px;
}

/* 数据 */
.host-stats {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 28px;
}

.host-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.host-stat-num {
  font-size: 18px;
  font-weight: 700;
}

.host-stat-label {
  font-size: 12px;
  opacity: 0.7;
}

.host-stat-divider {
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.2);
}

/* 按钮 */
.host-btn {
  background: var(--color-surface-elevated);
  color: var(--color-primary-800);
  border: none;
  font-weight: 600;
  padding: 12px 28px;
  font-size: 15px;
  transition:
    transform var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.host-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(31, 28, 25, 0.24);
  background: var(--color-primary-50);
}

.btn-icon {
  margin-left: 6px;
}

/* 右侧图片区域 */
.host-image {
  position: relative;
  z-index: 1;
  width: 280px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.host-img-placeholder {
  width: 200px;
  height: 200px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* 响应式 */
@media (max-width: 768px) {
  .host-cta-card {
    flex-direction: column;
    padding: 32px 24px;
    text-align: center;
  }

  .host-title {
    font-size: 26px;
  }

  .host-desc {
    margin-left: auto;
    margin-right: auto;
  }

  .host-stats {
    justify-content: center;
  }

  .host-image {
    display: none;
  }
}
</style>
