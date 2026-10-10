<template>
  <section class="trust-section">
    <div class="trust-header">
      <h2 class="trust-title">为什么选择我们</h2>
      <p class="trust-subtitle">让每一次旅行都安心、舒适、值得回忆</p>
    </div>

    <div class="trust-grid">
      <div class="trust-card">
        <div class="trust-icon-wrap">
          <el-icon :size="28"><UserFilled /></el-icon>
        </div>
        <h3 class="trust-card-title">实名认证房东</h3>
        <p class="trust-card-desc">所有房东均经过实名认证，确保房源信息真实可靠</p>
      </div>
      <div class="trust-card">
        <div class="trust-icon-wrap trust-icon--purple">
          <el-icon :size="28"><Lock /></el-icon>
        </div>
        <h3 class="trust-card-title">安全支付保障</h3>
        <p class="trust-card-desc">资金由平台托管，入住确认后才结算给房东</p>
      </div>
      <div class="trust-card">
        <div class="trust-icon-wrap trust-icon--orange">
          <el-icon :size="28"><ChatDotRound /></el-icon>
        </div>
        <h3 class="trust-card-title">24h 在线客服</h3>
        <p class="trust-card-desc">遇到问题随时联系，专业团队全程为您保驾护航</p>
      </div>
      <div class="trust-card">
        <div class="trust-icon-wrap trust-icon--green">
          <el-icon :size="28"><RefreshLeft /></el-icon>
        </div>
        <h3 class="trust-card-title">灵活退改政策</h3>
        <p class="trust-card-desc">多种退改方案可选，行程变动也能从容应对</p>
      </div>
    </div>

    <!-- 平台数据展示 -->
    <div class="trust-platform-stats">
      <div class="plat-stat">
        <span class="plat-num">{{ formatCount(stats.homestayCount) }}</span>
        <span class="plat-label">精选房源</span>
      </div>
      <div class="plat-divider" />
      <div class="plat-stat">
        <span class="plat-num">{{ formatCount(stats.cityCount) }}</span>
        <span class="plat-label">覆盖城市</span>
      </div>
      <div class="plat-divider" />
      <div class="plat-stat">
        <span class="plat-num">{{ stats.positiveRate }}%</span>
        <span class="plat-label">好评率</span>
      </div>
      <div class="plat-divider" />
      <div class="plat-stat">
        <span class="plat-num">{{ formatCount(stats.recentOrders) }}</span>
        <span class="plat-label">近30天成交</span>
      </div>
      <div class="plat-divider" />
      <div class="plat-stat">
        <span class="plat-num">{{ formatCount(stats.availableToday) }}</span>
        <span class="plat-label">今日可订</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { UserFilled, Lock, ChatDotRound, RefreshLeft } from "@element-plus/icons-vue";
import type { HomeStats } from "@/api/home";

interface Props {
  stats?: HomeStats;
}

withDefaults(defineProps<Props>(), {
  stats: () => ({
    homestayCount: 10000,
    cityCount: 50,
    positiveRate: 98,
    recentOrders: 0,
    availableToday: 0,
  }),
});

const formatCount = (num: number) => {
  if (!num && num !== 0) return "0";
  if (num >= 10000) return (num / 10000).toFixed(1) + "万+";
  return num.toLocaleString();
};
</script>

<style scoped>
.trust-section {
  margin-bottom: 48px;
  padding: var(--space-12) var(--space-8);
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.trust-header {
  text-align: center;
  margin-bottom: 36px;
}

.trust-title {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 700;
  color: var(--color-neutral-900);
  letter-spacing: 0;
}

.trust-subtitle {
  margin: 0;
  font-size: 15px;
  color: var(--color-neutral-600);
}

/* 信任卡片网格 */
.trust-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 40px;
}

.trust-card {
  text-align: center;
  padding: 28px 20px;
  border-radius: var(--radius-md);
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  transition:
    transform var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.trust-card:hover {
  transform: translateY(-4px);
  border-color: var(--color-primary-200);
  box-shadow: var(--shadow-lg);
}

.trust-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  background: var(--color-primary-500);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin: 0 auto 16px;
}

.trust-icon--purple {
  background: var(--color-secondary-500);
}

.trust-icon--orange {
  background: var(--color-warning);
}

.trust-icon--green {
  background: var(--color-success);
}

.trust-card-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-neutral-900);
}

.trust-card-desc {
  margin: 0;
  font-size: 13px;
  color: var(--color-neutral-600);
  line-height: 1.5;
}

/* 平台数据条 */
.trust-platform-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 40px;
  padding: 24px;
  background: var(--color-background-warm);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.plat-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.plat-num {
  font-size: 24px;
  font-weight: 800;
  color: var(--color-primary-700);
  letter-spacing: 0;
}

.plat-label {
  font-size: 13px;
  color: var(--color-neutral-600);
}

.plat-divider {
  width: 1px;
  height: 40px;
  background: var(--color-neutral-200);
}

/* 响应式 */
@media (max-width: 992px) {
  .trust-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .trust-section {
    padding: 32px 20px;
  }

  .trust-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .trust-card {
    display: flex;
    align-items: center;
    text-align: left;
    gap: 16px;
    padding: 20px;
  }

  .trust-icon-wrap {
    margin: 0;
    flex-shrink: 0;
  }

  .trust-platform-stats {
    flex-wrap: wrap;
    gap: 20px;
  }

  .plat-divider {
    display: none;
  }

  .plat-num {
    font-size: 20px;
  }
}
</style>
