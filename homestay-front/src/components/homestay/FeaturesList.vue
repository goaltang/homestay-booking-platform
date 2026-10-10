<template>
  <div class="features-redesigned" v-if="features.length > 0">
    <div
      class="feature-item-re"
      v-for="(feature, index) in features"
      :key="feature.featureId || feature.title || feature.label || index"
      :class="getFeatureItemClass(getFeatureTitle(feature), index)"
    >
      <div class="feature-icon-wrapper">
        <el-icon>
          <component
            :is="getIconComponent(feature.iconName || 'DefaultIcon', getFeatureTitle(feature))"
          />
        </el-icon>
      </div>
      <div class="feature-text">
        <h3>{{ getFeatureTitle(feature) }}</h3>
        <p>{{ getFeatureDescription(feature) }}</p>
      </div>
    </div>
  </div>

  <!-- Fallback if no features -->
  <div class="features-redesigned" v-else-if="showFallback">
    <div class="feature-item-re">
      <div class="feature-icon-wrapper">
        <el-icon>
          <House />
        </el-icon>
      </div>
      <div class="feature-text">
        <h3>{{ propertyType }}空间</h3>
        <p>您将拥有整个空间，享受私密住宿。最多可容纳 {{ maxGuests }} 位房客。</p>
      </div>
    </div>
    <div class="feature-item-re" v-if="keyFeatures.length > 0">
      <div class="feature-icon-wrapper">
        <el-icon>
          <Star />
        </el-icon>
      </div>
      <div class="feature-text">
        <h3>核心亮点</h3>
        <p>{{ keyFeatures.join(" · ") }}</p>
      </div>
    </div>
    <div class="feature-item-re">
      <div class="feature-icon-wrapper">
        <el-icon>
          <Key />
        </el-icon>
      </div>
      <div class="feature-text">
        <h3>便捷入住</h3>
        <p>通常支持自助入住，方便快捷。</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {} from "vue";
import {
  House,
  Star,
  Key,
  Check,
  InfoFilled,
  Trophy,
  Camera,
  ChatRound,
  OfficeBuilding,
  MagicStick,
  HomeFilled,
  KnifeFork,
  Van,
  Coffee,
  Ship,
  Bell,
  Refrigerator,
  Present,
  Medal,
  Suitcase,
  Sunny,
  Opportunity,
  Umbrella,
  Monitor,
  CaretRight,
  ShoppingCart,
  Document,
  Service,
  Promotion,
  ChatDotRound,
  Timer,
  Connection,
  Tools,
  VideoPlay,
  LocationFilled,
  Phone,
  User,
  Microphone,
  VideoCamera,
  PriceTag,
  Setting,
  Operation,
  Basketball,
  Football,
  TakeawayBox,
  Guide,
  MessageBox,
  FirstAidKit,
  Flag,
  Orange,
  Headset,
  Tickets,
  MapLocation,
  DataLine,
  Bicycle,
  Moon,
  Cherry,
  Sunrise,
  Warning,
  CircleCheck,
  HotWater,
} from "@element-plus/icons-vue";

// Types
interface FeatureItem {
  featureId?: string;
  iconName?: string;
  title?: string;
  description?: string;
  priority?: number;
  // 兼容设施对象格式
  label?: string;
  value?: string;
  categoryName?: string;
}

// Props
interface Props {
  features: FeatureItem[];
  showFallback?: boolean;
  propertyType?: string;
  maxGuests?: number;
  keyFeatures?: string[];
}

withDefaults(defineProps<Props>(), {
  showFallback: true,
  propertyType: "特色住宿",
  maxGuests: 1,
  keyFeatures: () => [],
});

// Icon components mapping
const iconComponents: Record<string, any> = {
  // 基础图标
  House,
  Star,
  Key,
  Check,
  InfoFilled,
  Camera,
  ChatRound,

  // 房屋类型图标
  OfficeBuilding, // 公寓、办公楼
  MagicStick, // 特色房源、独特住宿
  HomeFilled, // 传统民居、家庭式住宿

  // 设施相关图标
  ForkSpoon: KnifeFork, // 餐具、厨房设施
  Kitchen: KnifeFork, // 厨房设施
  Van: Van, // 交通、机场接送
  Car: Van, // 汽车、便捷出行
  Wifi: Connection, // 无线网络
  Coffee: Coffee, // 咖啡、饮品服务
  Monitor: Monitor, // 远程办公、工作区域
  Service: Service, // 全方位服务
  Tools: Tools, // 维修、工具
  VideoPlay: VideoPlay, // 视频播放、娱乐设施

  // 美食相关
  Mug: Coffee, // 饮品服务
  Food: TakeawayBox, // 餐饮服务
  KnifeFork: KnifeFork, // 厨房、餐具
  Refrigerator: Refrigerator, // 冰箱

  // 娱乐设施
  Ship: Ship, // 游艇、水上活动
  View: LocationFilled, // 景观、位置优势
  Bell: Bell, // 提醒、服务铃
  Basketball: Basketball, // 体育设施
  Football: Football, // 运动设施

  // 价格相关图标
  Hot: HotWater, // 热门、近期受欢迎
  HotWater: HotWater, // 热水图标
  PriceTag: PriceTag, // 价格标签
  Discount: Promotion, // 折扣、优惠
  Medal: Medal, // 奖章、特色认证

  // 服务品质相关
  Present: Present, // 礼品、额外服务
  Trophy: Trophy, // 奖杯、优秀房源
  StarFilled: Star, // 高评分
  ChatDotRound: ChatDotRound, // 沟通、评价互动

  // 便民服务
  Umbrella: Umbrella, // 雨伞、贴心服务
  Phone: Phone, // 电话联系
  Guide: Guide, // 导游、指引服务
  FirstAidKit: FirstAidKit, // 急救包、安全设施

  // 位置与交通
  Place: MapLocation, // 位置标记
  LocationFilled: LocationFilled, // 地理位置
  Bicycle: Bicycle, // 自行车、绿色出行

  // 时间与季节
  Timer: Timer, // 时间、预订时效
  Sunny: Sunny, // 阳光、明亮
  Moon: Moon, // 夜晚、安静
  Sunrise: Sunrise, // 日出、美景

  // 品质保证
  CircleCheck: CircleCheck, // 认证、验证
  Warning: Warning, // 注意事项
  Flag: Flag, // 标志、特色标记

  // 沟通与媒体
  Microphone: Microphone, // 语音、沟通
  VideoCamera: VideoCamera, // 视频、记录
  MessageBox: MessageBox, // 消息、留言
  Headset: Headset, // 客服、支持

  // 商务与文档
  Document: Document, // 文档、说明
  Tickets: Tickets, // 票务、预订
  Operation: Operation, // 操作、管理
  Setting: Setting, // 设置、配置

  // 购物与消费
  ShoppingCart: ShoppingCart, // 购物、消费
  Orange: Orange, // 特色、新鲜
  Cherry: Cherry, // 甜美、特色

  // 连接与网络
  Connection: Connection, // 连接、网络
  DataLine: DataLine, // 数据、统计

  // 通用图标
  User: User, // 用户、个人
  Suitcase: Suitcase, // 旅行、行李
  Opportunity: Opportunity, // 机会、推荐
  CaretRight: CaretRight, // 箭头、指示

  // 默认回退图标
  DefaultIcon: Star, // 默认星形图标
};

// Helper methods to handle different data formats
const getFeatureTitle = (feature: FeatureItem): string => {
  return feature.title || feature.label || "特色功能";
};

const getFeatureDescription = (feature: FeatureItem): string => {
  return feature.description || feature.categoryName || "暂无描述";
};

// Methods
const getFeatureItemClass = (title: string | undefined, index: number) => {
  const classes = ["feature-item-re"];

  // 添加空值检查
  if (!title) {
    return classes.join(" ");
  }

  // 为价格相关特色添加特殊样式
  if (
    title.includes("超值") ||
    title.includes("价格") ||
    title.includes("竞争力") ||
    title.includes("性价比") ||
    title.includes("实惠") ||
    title.includes("市场价格")
  ) {
    classes.push("feature-price");
  }
  // 为房屋类型添加特殊样式
  else if (
    title.includes("传统") ||
    title.includes("公寓") ||
    title.includes("特色房源") ||
    title.includes("民居") ||
    title.includes("宽敞") ||
    title.includes("现代") ||
    title.includes("精品") ||
    title.includes("独特")
  ) {
    classes.push("feature-property");
  }
  // 为位置相关特色添加特殊样式
  else if (
    title.includes("位置") ||
    title.includes("区域") ||
    title.includes("市中心") ||
    title.includes("核心") ||
    title.includes("交通") ||
    title.includes("出行")
  ) {
    classes.push("feature-location");
  }
  // 为服务相关特色添加特殊样式
  else if (
    title.includes("服务") ||
    title.includes("贴心") ||
    title.includes("便捷") ||
    title.includes("入住") ||
    title.includes("自助") ||
    title.includes("快速")
  ) {
    classes.push("feature-service");
  }
  // 为设施相关特色添加特殊样式
  else if (
    title.includes("厨房") ||
    title.includes("办公") ||
    title.includes("娱乐") ||
    title.includes("宠物") ||
    title.includes("设施") ||
    title.includes("停车") ||
    title.includes("网络")
  ) {
    classes.push("feature-amenity");
  }
  // 为评价和活动相关特色添加特殊样式
  else if (
    title.includes("好评") ||
    title.includes("热门") ||
    title.includes("经验") ||
    title.includes("新推") ||
    title.includes("活跃") ||
    title.includes("预订") ||
    title.includes("周末") ||
    title.includes("五星") ||
    title.includes("推荐")
  ) {
    classes.push("feature-activity");
  }

  // 为第一个特色添加突出显示
  if (index === 0) {
    classes.push("feature-primary");
  }

  return classes.join(" ");
};

const getIconComponent = (iconName: string, title?: string) => {
  // 首先检查基于标题的自定义图标映射
  const customIconMap: Record<string, string> = {
    // 房屋类型特色
    宽敞传统民居: "HomeFilled",
    现代精品公寓: "OfficeBuilding",
    独特特色房源: "MagicStick",
    传统民居: "HomeFilled",
    精品公寓: "OfficeBuilding",
    特色房源: "MagicStick",

    // 价格竞争力特色
    高性价比: "TrendCharts",
    超值价格: "TrendCharts",
    价格实惠: "TrendCharts",
    极具竞争力: "TrendCharts",
    市场价格: "PriceTag",
    价格优势: "Promotion",
    超值选择: "PriceTag",

    // 设施特色
    家庭友好厨房: "KnifeFork",
    商务办公设施: "Monitor",
    娱乐休闲设施: "VideoPlay",
    宠物友好设施: "Trophy",
    无线网络: "Connection",
    停车便利: "Van",
    远程办公: "Monitor",
    娱乐休闲: "VideoCamera",
    宠物天堂: "Trophy",

    // 位置特色
    首都核心区域: "LocationFilled",
    市中心位置: "LocationFilled",
    交通便利位置: "Place",
    便利位置: "LocationFilled",
    城市便利: "Place",
    便捷出行: "Van",

    // 预订活跃度特色
    近期热门: "HotWater",
    预订活跃: "HotWater",
    热门选择: "HotWater",

    // 周末受欢迎特色
    周末优选: "Umbrella",
    周末热门: "Umbrella",
    周末首选: "Umbrella",

    // 用户评价特色
    好评如潮: "Star",
    五星好评: "Star",
    用户推荐: "Star",

    // 入住便捷特色
    便捷入住: "Key",
    自助入住: "Key",
    快速入住: "Key",

    // 服务特色
    全方位服务: "Service",
    贴心服务: "Coffee",
    机场接送: "Van",
    早餐: "Coffee",
    清洁服务: "Service",
    洗衣服务: "Service",

    // 传统房源特色
    宠物友好: "Trophy",
    经验房源: "Medal",
    新推房源: "Sunny",
  };

  if (title && customIconMap[title]) {
    const customIcon = iconComponents[customIconMap[title]];
    if (customIcon) {
      return customIcon;
    }
  }

  // 然后检查直接的图标名称映射
  const component = iconComponents[iconName];
  if (component) {
    return component;
  } else {
    console.warn(`Icon "${iconName}" not found in iconComponents. Using default for "${title}".`);
    return iconComponents.DefaultIcon;
  }
};
</script>

<style scoped>
.features-redesigned {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
  margin: 32px 0;
}

.feature-item-re {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  background-color: #f9f9f9;
  border-radius: 12px;
  transition:
    transform 0.2s ease-in-out,
    box-shadow 0.2s ease-in-out;
}

.feature-item-re:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.feature-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: rgba(64, 158, 255, 0.1);
  margin-top: 2px;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.feature-item-re:hover .feature-icon-wrapper {
  transform: scale(1.1);
  background-color: rgba(64, 158, 255, 0.2);
}

.feature-icon-wrapper .el-icon {
  font-size: 20px !important;
  color: #409eff;
  margin: 0 !important;
}

.feature-text {
  flex: 1;
}

.feature-text h3 {
  margin: 0 0 8px 0;
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.feature-text p {
  margin: 0;
  font-size: 14px;
  color: #555;
  line-height: 1.6;
}

/* 特色功能类型样式 */
.feature-item-re.feature-price {
  background: linear-gradient(135deg, #fff5f5 0%, #fed7d7 100%);
  border-left: 4px solid #f56565;
}

.feature-item-re.feature-price .feature-icon-wrapper {
  background-color: rgba(245, 101, 101, 0.15);
}

.feature-item-re.feature-price .feature-icon-wrapper .el-icon {
  color: #f56565;
}

.feature-item-re.feature-property {
  background: linear-gradient(135deg, #f0fff4 0%, #c6f6d5 100%);
  border-left: 4px solid #48bb78;
}

.feature-item-re.feature-property .feature-icon-wrapper {
  background-color: rgba(72, 187, 120, 0.15);
}

.feature-item-re.feature-property .feature-icon-wrapper .el-icon {
  color: #48bb78;
}

.feature-item-re.feature-location {
  background: linear-gradient(135deg, #e6fffa 0%, #81e6d9 100%);
  border-left: 4px solid #319795;
}

.feature-item-re.feature-location .feature-icon-wrapper {
  background-color: rgba(49, 151, 149, 0.15);
}

.feature-item-re.feature-location .feature-icon-wrapper .el-icon {
  color: #319795;
}

.feature-item-re.feature-service {
  background: linear-gradient(135deg, #fef5e7 0%, #fbd38d 100%);
  border-left: 4px solid #ed8936;
}

.feature-item-re.feature-service .feature-icon-wrapper {
  background-color: rgba(237, 137, 54, 0.15);
}

.feature-item-re.feature-service .feature-icon-wrapper .el-icon {
  color: #ed8936;
}

.feature-item-re.feature-amenity {
  background: linear-gradient(135deg, #e6fffa 0%, #81e6d9 100%);
  border-left: 4px solid #38b2ac;
}

.feature-item-re.feature-amenity .feature-icon-wrapper {
  background-color: rgba(56, 178, 172, 0.15);
}

.feature-item-re.feature-amenity .feature-icon-wrapper .el-icon {
  color: #38b2ac;
}

.feature-item-re.feature-activity {
  background: linear-gradient(135deg, #f7fafc 0%, #cbd5e0 100%);
  border-left: 4px solid #718096;
}

.feature-item-re.feature-activity .feature-icon-wrapper {
  background-color: rgba(113, 128, 150, 0.15);
}

.feature-item-re.feature-activity .feature-icon-wrapper .el-icon {
  color: #718096;
}

/* 主要特色突出显示 */
.feature-item-re.feature-primary {
  transform: scale(1.02);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.15);
  border: 2px solid rgba(64, 158, 255, 0.2);
}

.feature-item-re.feature-primary .feature-text h3 {
  color: #409eff;
  font-weight: 700;
}

/* 响应式优化 */
@media (max-width: 768px) {
  .features-redesigned {
    grid-template-columns: 1fr;
    gap: 16px;
    margin: 24px 0;
  }

  .feature-item-re {
    padding: 16px;
    gap: 12px;
  }

  .feature-icon-wrapper {
    width: 36px;
    height: 36px;
  }

  .feature-icon-wrapper .el-icon {
    font-size: 18px !important;
  }

  .feature-text h3 {
    font-size: 16px;
  }

  .feature-text p {
    font-size: 13px;
  }
}

/* 动画效果 */
.feature-item-re {
  opacity: 0;
  transform: translateY(20px);
  animation: fadeInUp 0.6s ease forwards;
}

.feature-item-re:nth-child(1) {
  animation-delay: 0.1s;
}

.feature-item-re:nth-child(2) {
  animation-delay: 0.2s;
}

.feature-item-re:nth-child(3) {
  animation-delay: 0.3s;
}

.feature-item-re:nth-child(4) {
  animation-delay: 0.4s;
}

.feature-item-re:nth-child(5) {
  animation-delay: 0.5s;
}

.feature-item-re:nth-child(6) {
  animation-delay: 0.6s;
}

.feature-item-re:nth-child(7) {
  animation-delay: 0.7s;
}

.feature-item-re:nth-child(8) {
  animation-delay: 0.8s;
}

/* 对于超过8个特色的情况，使用通用延迟 */
.feature-item-re:nth-child(n + 9) {
  animation-delay: 0.9s;
}

@keyframes fadeInUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
