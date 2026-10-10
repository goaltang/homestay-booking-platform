<template>
  <div class="reviews-section">
    <div class="reviews-header">
      <h2>
        <el-icon>
          <Star />
        </el-icon>
        {{ displayRating }} · {{ reviewCount }}条评价
      </h2>
    </div>

    <!-- 评分统计 -->
    <div class="rating-summary" v-if="reviewStats.length > 0">
      <div class="rating-bars">
        <div class="rating-bar-item" v-for="(stat, index) in reviewStats" :key="index">
          <span class="rating-name">{{ stat.name }}</span>
          <div class="rating-bar-container">
            <div class="rating-bar" :style="{ width: `${stat.score * 20}%` }"></div>
          </div>
          <span class="rating-score">{{ stat.score.toFixed(1) }}</span>
        </div>
      </div>
    </div>

    <!-- 评价列表 -->
    <div class="review-list" v-if="reviews.length > 0">
      <div class="review-item" v-for="(review, index) in reviews" :key="index">
        <div class="review-header">
          <div class="reviewer-info">
            <div class="avatar">
              <img :src="getReviewerAvatar(review)" :alt="review.userName" />
            </div>
            <div class="reviewer-details">
              <div class="reviewer-name">{{ review.userName }}</div>
              <div class="review-date">{{ formatDisplayDate(review.createTime) }}</div>
            </div>
          </div>
          <div class="review-rating">
            <el-rate v-model="review.rating" disabled text-color="#ff9900" />
          </div>
        </div>
        <div class="review-content">
          {{ review.content }}
        </div>
        <!-- 评价图片 -->
        <div class="review-images" v-if="review.images && review.images.length > 0">
          <el-image
            v-for="(img, imgIndex) in review.images"
            :key="imgIndex"
            :src="getImageUrl(img)"
            :preview-src-list="review.images.map((i) => getImageUrl(i))"
            :initial-index="imgIndex"
            fit="cover"
            class="review-image"
          />
        </div>
        <div class="host-response" v-if="review.response">
          <div class="response-header">房东回复:</div>
          <div class="response-content">{{ review.response }}</div>
        </div>
      </div>
    </div>

    <!-- 无评价提示 -->
    <div class="no-reviews" v-else>
      <el-empty description="暂无评价" v-if="reviewCount === 0">
        <span>这个房源还没有收到评价</span>
      </el-empty>
      <div class="loading-reviews" v-else>
        <el-skeleton :rows="3" animated />
      </div>
    </div>

    <!-- 加载更多按钮 -->
    <div class="load-more" v-if="reviews.length > 0 && reviews.length < totalCount">
      <el-button @click="$emit('load-more')">加载更多评价</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Star } from "@element-plus/icons-vue";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import "dayjs/locale/zh-cn";

dayjs.extend(relativeTime);
dayjs.locale("zh-cn");

// Types
interface Review {
  id: number;
  userName: string;
  userAvatar?: string;
  rating: number;
  content: string;
  createTime: string;
  response?: string;
  responseTime?: string;
  images?: string[];
}

interface ReviewStat {
  name: string;
  score: number;
}

// Props
interface Props {
  reviews: Review[];
  reviewStats: ReviewStat[];
  reviewCount: number;
  totalCount: number;
  rating?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  rating: 0,
});

// Emits
defineEmits<{
  "load-more": [];
}>();

// Computed properties
// 评分由父组件通过 props.rating 统一传入（来自 useReviews 的 formattedRating），
// 避免与 useReviews 中的计算逻辑重复。
const displayRating = computed(() => {
  const r = props.rating;
  if (!r || r === 0 || r === "暂无评分") return "暂无评分";
  return typeof r === "number" ? r.toFixed(1) : r;
});

// Methods
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

const getReviewerAvatar = (review: Review): string => {
  if (review.userAvatar) {
    if (review.userAvatar.startsWith("/")) {
      return `${API_BASE_URL}${review.userAvatar}`;
    }
    return review.userAvatar;
  }
  return "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png";
};

const getImageUrl = (url: string): string => {
  if (!url) return "";
  if (url.startsWith("/")) {
    return `${API_BASE_URL}${url}`;
  }
  return url;
};

const formatDisplayDate = (date: Date | string | null): string => {
  if (!date) return "日期无效";
  try {
    const d = typeof date === "string" ? new Date(date) : date;
    if (!(d instanceof Date) || isNaN(d.getTime())) return "日期无效";

    const now = dayjs();
    const target = dayjs(d);
    const diffDays = now.diff(target, "day");

    // 30天内显示相对时间
    if (diffDays < 30) {
      return target.fromNow();
    }

    // 超过30天显示具体日期
    return target.format("YYYY年MM月DD日");
  } catch (e) {
    return "日期格式错误";
  }
};
</script>

<style scoped>
.reviews-section {
  margin-top: 24px;
}

.reviews-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}

.reviews-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 22px;
  font-weight: 600;
  margin: 0;
}

.rating-summary {
  margin: 24px 0;
}

.rating-bars {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.rating-bar-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rating-name {
  flex: 0 0 80px;
  font-size: 14px;
  color: #606266;
}

.rating-bar-container {
  flex: 1;
  height: 4px;
  background-color: #eee;
  border-radius: 2px;
}

.rating-bar {
  height: 100%;
  background-color: #333;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.rating-score {
  width: 30px;
  text-align: right;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.review-list {
  margin-top: 32px;
}

.review-item {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid #eee;
}

.review-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.reviewer-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.reviewer-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.reviewer-name {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}

.review-date {
  color: #717171;
  font-size: 14px;
}

.review-rating {
  flex-shrink: 0;
}

.review-content {
  line-height: 1.6;
  margin-bottom: 16px;
  color: #484848;
  font-size: 15px;
}

.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.review-image {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  cursor: pointer;
}

.host-response {
  background-color: #f7f7f7;
  padding: 16px;
  border-radius: 8px;
  margin-top: 16px;
  border-left: 3px solid #409eff;
}

.response-header {
  font-weight: 600;
  margin-bottom: 8px;
  color: #409eff;
  font-size: 14px;
}

.response-content {
  color: #484848;
  line-height: 1.6;
  font-size: 14px;
}

.no-reviews {
  margin: 48px 0;
  text-align: center;
}

.loading-reviews {
  padding: 24px;
}

.load-more {
  text-align: center;
  margin-top: 32px;
}

.load-more .el-button {
  padding: 12px 24px;
  font-size: 14px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .rating-bars {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .rating-bar-item {
    gap: 12px;
  }

  .rating-name {
    flex: 0 0 60px;
    font-size: 13px;
  }

  .review-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .reviewer-info {
    gap: 12px;
  }

  .avatar {
    width: 40px;
    height: 40px;
  }

  .reviewer-name {
    font-size: 14px;
  }

  .review-date {
    font-size: 12px;
  }

  .review-content {
    font-size: 14px;
  }

  .host-response {
    padding: 12px;
    margin-top: 12px;
  }

  .response-header {
    font-size: 13px;
  }

  .response-content {
    font-size: 13px;
  }
}

/* 动画效果 */
.review-item {
  opacity: 0;
  transform: translateY(20px);
  animation: fadeInUp 0.6s ease forwards;
}

.review-item:nth-child(1) {
  animation-delay: 0.1s;
}

.review-item:nth-child(2) {
  animation-delay: 0.2s;
}

.review-item:nth-child(3) {
  animation-delay: 0.3s;
}

.review-item:nth-child(4) {
  animation-delay: 0.4s;
}

.review-item:nth-child(5) {
  animation-delay: 0.5s;
}

@keyframes fadeInUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.rating-bar {
  animation: progressBar 1s ease-out forwards;
}

@keyframes progressBar {
  from {
    width: 0;
  }
}
</style>
