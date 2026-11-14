<template>
  <section class="trust">
    <h2 class="trust-title">订房有保障</h2>
    <ul class="trust-list">
      <li v-for="item in guarantees" :key="item.title" class="trust-item">
        <span class="trust-icon" aria-hidden="true">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
        </span>
        <div>
          <h3 class="trust-item-title">{{ item.title }}</h3>
          <p class="trust-item-desc">{{ item.desc }}</p>
        </div>
      </li>
    </ul>

    <!-- 平台数据：只展示接口返回且有说服力的指标 -->
    <dl v-if="platformStats.length > 0" class="trust-stats">
      <div v-for="item in platformStats" :key="item.label" class="trust-stat">
        <dt>{{ item.label }}</dt>
        <dd>{{ item.value }}</dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { UserFilled, Lock, ChatDotRound, RefreshLeft } from "@element-plus/icons-vue";
import type { HomeStats } from "@/api/home";

// 好评率低于该值时不展示，避免统计条反过来削弱信任
const MIN_DISPLAY_POSITIVE_RATE = 90;

const guarantees = [
  { icon: UserFilled, title: "房东实名认证", desc: "房东需要完成实名认证，房源信息可追溯" },
  { icon: Lock, title: "平台代收房费", desc: "房费先由平台保管，入住后再结算给房东" },
  { icon: RefreshLeft, title: "按规则退改", desc: "每套房源都写明退改政策，下单前就能看到" },
  { icon: ChatDotRound, title: "在线客服", desc: "订单、退款和纠纷问题都可以在线咨询" },
];

const props = defineProps<{
  stats?: HomeStats | null;
}>();

const formatCount = (num: number) => {
  if (num >= 10000) return (num / 10000).toFixed(1) + "万+";
  return num.toLocaleString();
};

// availableToday 目前后端直接等于在线房源数，不是真实可订量，暂不展示
const platformStats = computed(() => {
  const stats = props.stats;
  if (!stats) return [];
  const items: Array<{ label: string; value: string }> = [];
  if (stats.homestayCount > 0) {
    items.push({ label: "在线房源", value: formatCount(stats.homestayCount) });
  }
  if (stats.cityCount > 0) {
    items.push({ label: "覆盖城市", value: formatCount(stats.cityCount) });
  }
  if (stats.positiveRate >= MIN_DISPLAY_POSITIVE_RATE) {
    items.push({ label: "好评率", value: `${stats.positiveRate}%` });
  }
  if (stats.recentOrders > 0) {
    items.push({ label: "近30天成交", value: formatCount(stats.recentOrders) });
  }
  return items;
});
</script>

<style scoped>
.trust {
  padding: var(--space-8) 0;
  border-top: 1px solid var(--color-neutral-200);
  border-bottom: 1px solid var(--color-neutral-200);
}

.trust-title {
  margin: 0 0 var(--space-6);
  font-size: var(--text-h3);
}

.trust-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-6);
  margin: 0;
  padding: 0;
  list-style: none;
}

.trust-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.trust-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  background: var(--color-secondary-50);
  color: var(--color-secondary-700);
}

.trust-item-title {
  margin: 0 0 var(--space-1);
  font-family: var(--font-body);
  font-size: var(--text-body);
  font-weight: 600;
}

.trust-item-desc {
  margin: 0;
  font-size: var(--text-body-sm);
  line-height: 1.6;
  color: var(--color-neutral-600);
}

.trust-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-8);
  margin: var(--space-6) 0 0;
}

.trust-stat {
  display: flex;
  flex-direction: column-reverse;
  gap: var(--space-1);
}

.trust-stat dt {
  font-size: var(--text-body-sm);
  color: var(--color-neutral-600);
}

.trust-stat dd {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-h2);
  font-weight: 700;
  color: var(--color-primary-700);
}

@media (max-width: 992px) {
  .trust-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .trust-list {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }
}
</style>
