<template>
  <!-- 房东简介卡片 (靠上显示) -->
  <div class="host-brief-enhanced" v-if="showBrief">
    <div class="host-basic-info">
      <div class="host-avatar-wrapper">
        <img :src="hostDisplayAvatar" :alt="hostDisplayName" class="host-avatar" />
        <div class="verification-badge" v-if="isVerified">
          <el-icon>
            <Check />
          </el-icon>
        </div>
      </div>
      <div class="host-meta">
        <h3 class="host-name">{{ hostDisplayName }}</h3>
        <div class="host-subtitle">
          <span class="host-type">{{ hostInfo?.occupation || "旅居主人" }}</span>
          <span v-if="isVerified" class="verified-status-brief">
            <el-icon>
              <Check />
            </el-icon>
            已认证
          </span>
        </div>
      </div>
    </div>

    <!-- 快速简介 -->
    <div class="host-quick-intro" v-if="hostInfo?.introduction">
      <div class="intro-text">
        <span class="intro-preview">
          {{
            hostInfo.introduction.length > 80
              ? hostInfo.introduction.substring(0, 80) + "..."
              : hostInfo.introduction
          }}
        </span>
        <el-button
          type="text"
          size="small"
          @click="$emit('scroll-to-detail')"
          class="read-more-btn"
          v-if="hostInfo.introduction.length > 80"
        >
          了解更多
        </el-button>
      </div>
    </div>

    <!-- 默认简介 -->
    <div class="host-default-intro" v-else>
      <p>星级旅居主人接待经验丰富、深获旅人好评，他们致力为旅人提供优质的住宿体验。</p>
    </div>

    <!-- 关键特色标签 -->
    <div class="host-highlights" v-if="achievementBadges.length > 0">
      <el-tooltip
        v-for="badge in achievementBadges.slice(0, 2)"
        :key="badge"
        :content="getBadgeDescription(badge)"
        placement="top"
      >
        <el-tag size="small" :type="getBadgeType(badge)" effect="light" class="highlight-badge">
          <el-icon class="badge-icon">
            <component :is="getBadgeIcon(badge)" />
          </el-icon>
          {{ badge }}
        </el-tag>
      </el-tooltip>
      <el-tooltip
        v-if="achievementBadges.length > 2"
        content="房东拥有更多平台认证的特色与成就"
        placement="top"
      >
        <span class="more-badges"> +{{ achievementBadges.length - 2 }}项认证 </span>
      </el-tooltip>
    </div>
  </div>

  <!-- 房东详细信息 (靠下显示) -->
  <div class="host-section" v-if="showDetail && hostInfo">
    <h2>旅居主人简介</h2>
    <div class="host-detail-layout">
      <!-- 左侧卡片: 头像, 姓名, 统计数据 -->
      <div class="host-left-card">
        <div class="host-profile-card">
          <div class="host-profile-avatar">
            <img :src="hostDisplayAvatar" :alt="hostDisplayName" />
          </div>
          <div class="host-name">{{ hostDisplayName }}</div>
          <div class="host-title">{{ hostInfo.occupation || "旅居主人" }}</div>

          <!-- 评分显示 -->
          <div class="host-rating" v-if="hostRating > 0">
            <el-rate
              :model-value="hostRating"
              disabled
              text-color="#FF9900"
              disabled-void-color="#C6D1DE"
              :max="5"
            />
            <span class="rating-text">{{ hostRating.toFixed(1) }}</span>
          </div>
        </div>

        <!-- 统计数据 -->
        <div class="host-stats">
          <div class="host-stat-item">
            <div class="stat-value">{{ hostInfo.reviewCount || 0 }}</div>
            <div class="stat-label">评价</div>
          </div>
          <div class="host-stat-item">
            <div class="stat-value">{{ hostInfo.homestayCount || 0 }}</div>
            <div class="stat-label">房源</div>
          </div>
          <div class="host-stat-item">
            <div class="stat-value">{{ hostInfo.orderCount || 0 }}</div>
            <div class="stat-label">订单</div>
          </div>
        </div>
      </div>

      <!-- 右侧信息: 简介, 详情, 联系按钮 -->
      <div class="host-right-info">
        <!-- 简介 -->
        <div class="host-intro">
          <h3>关于 {{ hostDisplayName }}</h3>
          <div class="intro-content">
            <p v-if="!showFullIntro && hostInfo.introduction && hostInfo.introduction.length > 150">
              {{ hostInfo.introduction.substring(0, 150) }}...
              <el-button type="text" @click="showFullIntro = true" class="show-more-btn">
                显示更多
              </el-button>
            </p>
            <p v-else-if="hostInfo.introduction">
              {{ hostInfo.introduction }}
              <el-button
                v-if="showFullIntro && hostInfo.introduction.length > 150"
                type="text"
                @click="showFullIntro = false"
                class="show-more-btn"
              >
                收起
              </el-button>
            </p>
            <p v-else class="no-intro">这位房东比较内向，还没有填写简介哦~</p>
          </div>
        </div>

        <!-- 房东详情 -->
        <div class="host-details">
          <h4>房东信息</h4>
          <div class="host-detail-info">
            <!-- 身份认证 -->
            <div class="detail-item" v-if="hostInfo.realName">
              <div class="detail-label">
                <el-icon>
                  <Check />
                </el-icon>
                真实姓名认证
              </div>
              <div class="detail-value verified">已认证</div>
            </div>

            <!-- 联系方式 -->
            <div class="detail-item" v-if="hostInfo.phone">
              <div class="detail-label">
                <el-icon>
                  <Check />
                </el-icon>
                手机号认证
              </div>
              <div class="detail-value verified">已认证</div>
            </div>

            <!-- 邮箱认证 -->
            <div class="detail-item" v-if="hostInfo.email">
              <div class="detail-label">
                <el-icon>
                  <Check />
                </el-icon>
                邮箱认证
              </div>
              <div class="detail-value verified">已认证</div>
            </div>

            <!-- 加入时间 -->
            <div class="detail-item" v-if="hostInfo.hostSince">
              <div class="detail-label">加入时间</div>
              <div class="detail-value">{{ formatJoinDate(hostInfo.hostSince) }}</div>
            </div>

            <!-- 响应率和响应时间 -->
            <div class="detail-item" v-if="hostInfo.hostResponseRate">
              <div class="detail-label">响应率</div>
              <div class="detail-value response-info">
                <span class="response-rate">{{ hostInfo.hostResponseRate }}</span>
                <span class="response-time" v-if="hostInfo.hostResponseTime">
                  · {{ hostInfo.hostResponseTime }}
                </span>
              </div>
            </div>

            <!-- 语言 -->
            <div class="detail-item" v-if="formattedLanguages.length > 0">
              <div class="detail-label">语言</div>
              <div class="detail-value">
                <span class="language-tags">
                  <el-tag
                    v-for="lang in formattedLanguages"
                    :key="lang"
                    size="small"
                    type="info"
                    class="language-tag"
                  >
                    {{ lang }}
                  </el-tag>
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 房东成就 -->
        <div class="host-achievements" v-if="achievementBadges.length > 0">
          <h4>房东成就</h4>
          <div class="achievements-list">
            <el-tooltip
              v-for="badge in achievementBadges"
              :key="badge"
              :content="getBadgeDescription(badge)"
              placement="top"
            >
              <div class="achievement-item">
                <el-icon class="achievement-icon">
                  <component :is="getBadgeIcon(badge)" />
                </el-icon>
                <div class="achievement-content">
                  <span class="achievement-title">{{ badge }}</span>
                  <small class="achievement-desc">
                    {{ getBadgeDetailDescription(badge) }}
                  </small>
                </div>
              </div>
            </el-tooltip>
          </div>
        </div>

        <!-- 联系按钮 -->
        <div class="host-contact">
          <el-button type="primary" class="contact-button" @click="$emit('contact-host')">
            <el-icon>
              <ChatRound />
            </el-icon>
            发送消息给房东
          </el-button>
        </div>

        <!-- 安全提示 -->
        <div class="host-safety-note">
          <el-icon>
            <InfoFilled />
          </el-icon>
          <p>为保障您的安全，请始终通过平台沟通，切勿在线下分享联系方式或付款。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { Check, ChatRound, InfoFilled, House, Trophy, Star } from "@element-plus/icons-vue";
import type { HostDTO } from "@/types/host";
import { HostUtils } from "@/utils/hostUtils";

// Props
interface Props {
  hostInfo: HostDTO | null;
  showBrief?: boolean;
  showDetail?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showBrief: true,
  showDetail: true,
});

// Emits
defineEmits<{
  "scroll-to-detail": [];
  "contact-host": [];
}>();

// Reactive data
const showFullIntro = ref(false);

// Computed properties
const hostDisplayName = computed(() => {
  return HostUtils.getDisplayName(props.hostInfo);
});

const hostDisplayAvatar = computed(() => {
  return HostUtils.getDisplayAvatar(props.hostInfo);
});

const isVerified = computed(() => {
  return props.hostInfo ? HostUtils.isVerified(props.hostInfo) : false;
});

const hostRating = computed(() => {
  return props.hostInfo ? HostUtils.getDisplayRating(props.hostInfo) : 0;
});

const achievementBadges = computed(() => {
  if (!props.hostInfo) return [];
  const displayInfo = HostUtils.getDisplayInfo(props.hostInfo);
  return displayInfo?.achievementBadges || [];
});

const formattedLanguages = computed(() => {
  if (!props.hostInfo?.languages) return [];
  return HostUtils.formatLanguages(props.hostInfo.languages);
});

// Methods
const formatJoinDate = (date: string | Date) => {
  return HostUtils.formatJoinDate(date);
};

const getBadgeType = (badge: string): "primary" | "success" | "warning" | "info" => {
  switch (badge) {
    case "资深房东":
      return "primary";
    case "接待达人":
      return "success";
    case "好评房东":
      return "warning";
    case "快速回复":
      return "info";
    default:
      return "info";
  }
};

const getBadgeIcon = (badge: string) => {
  switch (badge) {
    case "资深房东":
      return House;
    case "接待达人":
      return Trophy;
    case "好评房东":
      return Star;
    case "快速回复":
      return ChatRound;
    default:
      return Check;
  }
};

const getBadgeDescription = (badge: string): string => {
  const descriptions: Record<string, string> = {
    资深房东: "管理3间及以上房源，经验丰富。",
    接待达人: "已成功接待10位及以上客人，广受好评。",
    好评房东: "综合评分达到4.0分及以上，并收到3条及以上真实评价。",
    身份认证: "已通过平台身份信息验证，可信赖。",
    快速回复: "通常能在平台建议的时间内快速响应用户咨询。",
  };
  return descriptions[badge] || "平台认证特色房东";
};

const getBadgeDetailDescription = (badge: string): string => {
  if (!props.hostInfo) return "";

  switch (badge) {
    case "资深房东":
      return "经验丰富的多房源管理者";
    case "接待达人":
      return "成功接待了众多客人";
    case "好评房东":
      return `评分 ${hostRating.value.toFixed(1)} 分`;
    case "快速回复":
      return `通常 ${props.hostInfo.hostResponseTime || "24小时内"} 回复`;
    case "身份认证":
      return "已完成身份验证";
    default:
      return "平台认证成就";
  }
};
</script>

<style scoped>
/* 房东简介样式 */
.host-brief-enhanced {
  display: flex;
  flex-direction: column;
  gap: 24px;
  margin: 24px 0;
  padding: 16px;
  background-color: #f8f8f8;
  border-radius: 12px;
}

.host-basic-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.host-avatar-wrapper {
  width: 72px;
  height: 72px;
  position: relative;
  flex-shrink: 0;
}

.host-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.verification-badge {
  position: absolute;
  top: 0;
  right: 0;
  background-color: #67c23a;
  color: white;
  border-radius: 50%;
  padding: 3px;
  font-size: 10px;
}

.host-meta {
  flex-grow: 1;
  text-align: left;
}

.host-name {
  font-size: 18px;
  margin-bottom: 4px;
  font-weight: 600;
}

.host-subtitle {
  font-size: 13px;
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.verified-status-brief {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
  color: #67c23a;
}

.host-quick-intro {
  margin-top: 12px;
}

.intro-text {
  text-align: left;
}

.host-default-intro {
  color: #606266;
  font-style: italic;
}

.host-highlights {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.highlight-badge {
  background-color: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 12px;
  color: #0369a1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.badge-icon {
  font-size: 14px;
  color: #409eff;
  flex-shrink: 0;
}

.more-badges {
  font-size: 12px;
  color: #717171;
}

.read-more-btn {
  padding: 0;
  font-weight: 600;
  margin-left: 4px;
}

/* 房东详细信息样式 */
.host-section {
  padding: 32px 0;
  border-top: 1px solid #ebebeb;
  border-bottom: 1px solid #ebebeb;
}

.host-section h2 {
  margin-bottom: 24px;
  font-size: 22px;
  font-weight: 600;
}

.host-detail-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 32px;
}

.host-left-card {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.host-profile-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px;
  border: 1px solid #ebebeb;
  border-radius: 12px;
  text-align: center;
}

.host-profile-avatar {
  width: 120px;
  height: 120px;
  margin-bottom: 16px;
}

.host-profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.host-name {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
}

.host-title {
  font-size: 14px;
  color: #717171;
  margin-bottom: 12px;
}

.host-rating {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.rating-text {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.host-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border: 1px solid #ebebeb;
  border-radius: 12px;
  overflow: hidden;
}

.host-stat-item {
  padding: 16px 12px;
  text-align: center;
  border-right: 1px solid #ebebeb;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.host-stat-item:last-child {
  border-right: none;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #717171;
}

.host-right-info {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.host-intro h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.intro-content {
  line-height: 1.6;
  color: #484848;
}

.intro-content p {
  margin-bottom: 8px;
}

.no-intro {
  color: #909399;
  font-style: italic;
}

.show-more-btn {
  padding: 0;
  font-weight: 600;
  margin-left: 4px;
}

.host-details h4 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
}

.host-detail-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.detail-value {
  font-size: 14px;
  color: #303133;
  text-align: right;
}

.detail-value.verified {
  color: #67c23a;
  font-weight: 500;
}

.response-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.response-rate {
  font-weight: 600;
  color: #67c23a;
}

.response-time {
  font-size: 12px;
  color: #909399;
}

.language-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.language-tag {
  font-size: 12px;
}

.host-achievements h4 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
}

.achievements-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.achievement-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
  border-left: 3px solid #409eff;
}

.achievement-icon {
  font-size: 18px;
  color: #409eff;
  margin-top: 2px;
  flex-shrink: 0;
}

.achievement-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.achievement-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.achievement-desc {
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
}

.host-contact {
  margin-top: 24px;
}

.contact-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

.host-safety-note {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  padding: 12px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  font-size: 12px;
  color: #0369a1;
  line-height: 1.4;
}

.host-safety-note .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .host-brief-enhanced {
    padding: 12px;
    gap: 16px;
  }

  .host-basic-info {
    flex-direction: column;
    text-align: center;
    gap: 12px;
  }

  .host-avatar-wrapper {
    width: 80px;
    height: 80px;
    align-self: center;
  }

  .host-meta {
    align-items: center;
  }

  .host-highlights {
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
  }

  .host-detail-layout {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .host-stats {
    grid-template-columns: repeat(3, 1fr);
  }

  .achievements-list {
    gap: 12px;
  }

  .achievement-item {
    padding: 12px;
    flex-direction: column;
    align-items: flex-start;
    text-align: left;
  }

  .language-tags {
    justify-content: flex-start;
  }

  .response-info {
    align-items: flex-start;
  }
}
</style>
