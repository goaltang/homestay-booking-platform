<template>
  <div class="promo-section">
    <div class="section-header">
      <h2 class="section-title">限时特惠</h2>
      <el-button link type="primary" size="small" @click="handleViewAll">查看全部优惠</el-button>
    </div>
    <div class="promo-grid">
      <div
        v-for="promo in promos"
        :key="promo.id"
        class="promo-card"
        :style="{ background: promo.gradient }"
        @click="handleClick(promo)"
      >
        <div class="promo-content">
          <span class="promo-tag">{{ promo.tag }}</span>
          <h3 class="promo-title">{{ promo.title }}</h3>
          <p class="promo-desc">{{ promo.desc }}</p>
        </div>
        <div class="promo-icon" aria-hidden="true">
          <el-icon :size="48" color="rgba(255,255,255,0.3)">
            <component :is="promo.icon" />
          </el-icon>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Present, Ticket, Star, Sunrise } from "@element-plus/icons-vue";

interface PromoItem {
  id: string;
  tag: string;
  title: string;
  desc: string;
  gradient: string;
  icon: any;
  route: string;
  query?: Record<string, any>;
}

const promos: PromoItem[] = [
  {
    id: "newuser",
    tag: "新用户专享",
    title: "首单立减 ¥100",
    desc: "注册后 7 天内首次预订可享",
    gradient: "linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)",
    icon: Present,
    route: "/homestays",
    query: { search: "true" },
  },
  {
    id: "weekend",
    tag: "周末闪促",
    title: "周五入住 8 折起",
    desc: "精选城市周末房源限时特惠",
    gradient: "linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)",
    icon: Ticket,
    route: "/homestays",
    query: { search: "true" },
  },
  {
    id: "featured",
    tag: "房东推荐",
    title: "品质房源 9 折",
    desc: "高评分房东的精选优质房源",
    gradient: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
    icon: Star,
    route: "/homestays",
    query: { featured: "true" },
  },
  {
    id: "earlybird",
    tag: "早鸟优惠",
    title: "提前 14 天预订 85 折",
    desc: "规划行程越早，优惠越多",
    gradient: "linear-gradient(135deg, #f093fb 0%, #f5576c 100%)",
    icon: Sunrise,
    route: "/homestays",
    query: { search: "true" },
  },
];

const emit = defineEmits<{
  click: [promo: PromoItem];
}>();

const handleClick = (promo: PromoItem) => {
  emit("click", promo);
};

const handleViewAll = () => {
  emit("click", { id: "all", route: "/homestays", query: { search: "true" } } as any);
};
</script>

<style scoped>
.promo-section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #222;
}

.promo-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.promo-card {
  position: relative;
  border-radius: 16px;
  padding: 24px;
  color: white;
  cursor: pointer;
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;
  overflow: hidden;
  min-height: 160px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.promo-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
}

.promo-tag {
  display: inline-block;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(4px);
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 12px;
}

.promo-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
}

.promo-desc {
  margin: 0;
  font-size: 13px;
  opacity: 0.9;
  line-height: 1.4;
}

.promo-icon {
  position: absolute;
  bottom: 12px;
  right: 12px;
}

@media (max-width: 992px) {
  .promo-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .promo-grid {
    grid-template-columns: 1fr;
  }

  .promo-card {
    min-height: 120px;
  }
}
</style>
