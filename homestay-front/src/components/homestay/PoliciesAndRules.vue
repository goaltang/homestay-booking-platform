<template>
  <div class="policies-and-rules">
    <h2>须知事项</h2>

    <div class="rules-grid">
      <!-- 房屋守则 -->
      <div class="rule-column">
        <h3>房屋守则</h3>
        <div class="rule-item" v-if="checkInTime">
          <el-icon><Clock /></el-icon>
          <span>入住房源：{{ checkInTime }}</span>
        </div>
        <div class="rule-item" v-if="checkOutTime">
          <el-icon><Clock /></el-icon>
          <span>退房时间：{{ checkOutTime }}</span>
        </div>

        <div class="custom-rules" v-if="houseRules">
          <div class="rule-text-preview">{{ houseRules }}</div>
          <el-button type="text" class="more-btn" @click="showRulesDrawer = true">
            显示更多 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 安全与住宿须知 -->
      <div class="rule-column">
        <h3>安全与住宿须知</h3>
        <div class="rule-item warning">
          <el-icon><Warning /></el-icon>
          <span>房东未明确提供烟雾报警器信息</span>
        </div>
        <div class="rule-item">
          <el-icon><InfoFilled /></el-icon>
          <span>请爱护房屋内的设施</span>
        </div>
      </div>

      <!-- 退订政策 -->
      <div class="rule-column policy-column">
        <h3>退订政策</h3>
        <div class="policy-card">
          <h4>{{ policyTitle }}</h4>
          <p>{{ policyDescription }}</p>
        </div>
      </div>
    </div>

    <!-- 抽屉：显示所有规则详情 -->
    <el-drawer v-model="showRulesDrawer" title="房屋守则" size="400px">
      <div class="drawer-content">
        <h3>入住/退房</h3>
        <ul class="drawer-list">
          <li v-if="checkInTime">入住：{{ checkInTime }}</li>
          <li v-if="checkOutTime">退房：{{ checkOutTime }}</li>
        </ul>

        <el-divider />

        <h3>房东的话</h3>
        <div class="drawer-text">
          {{ houseRules }}
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { Clock, Warning, ArrowRight, InfoFilled } from "@element-plus/icons-vue";

const props = defineProps<{
  checkInTime?: string;
  checkOutTime?: string;
  cancelPolicyType?: number;
  houseRules?: string;
}>();

const showRulesDrawer = ref(false);

// 计算属性：生成退订政策标题（与后端 OrderServiceImpl 保持一致）
// policyType: 1=宽松, 2=普通, 3=严格
const policyTitle = computed(() => {
  switch (props.cancelPolicyType) {
    case 1:
      return "宽松";
    case 2:
      return "普通";
    case 3:
      return "严格";
    default:
      return "标准";
  }
});

// 计算属性：生成退订政策描述（与后端 calculateRefundAmount 逻辑一致）
const policyDescription = computed(() => {
  switch (props.cancelPolicyType) {
    case 1:
      return "入住前 24 小时取消，可获全额退款；24 小时内取消，扣除首晚房费。";
    case 2:
      return "入住前 48 小时取消，可获全额退款；24-48 小时内取消，退款 50%；24 小时内取消，扣除首晚房费。";
    case 3:
      return "入住前 72 小时取消，可获全额退款；72 小时内取消，退款 50%。";
    default:
      return "请在预订时仔细确认取消政策。";
  }
});
</script>

<style scoped>
.policies-and-rules {
  margin: 48px 0;
}

.policies-and-rules h2 {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 24px;
  color: #222;
}

.rules-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.rule-column h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #222;
}

.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
  font-size: 15px;
  color: #484848;
}

.rule-item .el-icon {
  font-size: 20px;
  color: #717171;
  margin-top: 2px;
}

.custom-rules {
  margin-top: 16px;
}

.rule-text-preview {
  font-size: 15px;
  color: #717171;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 8px;
}

.more-btn {
  padding: 0;
  font-size: 15px;
  font-weight: 600;
  color: #222;
  text-decoration: underline;
  display: flex;
  align-items: center;
  gap: 4px;
}

.more-btn:hover {
  color: #000;
}

.policy-column {
  background: #f7f7f7;
  padding: 24px;
  border-radius: 12px;
}

.policy-card h4 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #222;
}

.policy-card p {
  font-size: 15px;
  color: #717171;
  line-height: 1.5;
}

.drawer-content {
  padding: 0 20px 20px;
}

.drawer-content h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #222;
}

.drawer-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.drawer-list li {
  font-size: 16px;
  color: #484848;
  margin-bottom: 12px;
}

.drawer-text {
  font-size: 16px;
  color: #484848;
  line-height: 1.6;
  white-space: pre-wrap;
}

@media (max-width: 768px) {
  .rules-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}
</style>
