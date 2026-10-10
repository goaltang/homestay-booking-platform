<template>
  <div class="trust-bar">
    <div class="trust-item">
      <el-icon :size="20" class="trust-icon"><UserFilled /></el-icon>
      <span class="trust-text">实名认证房东</span>
    </div>
    <div class="trust-divider" />
    <div class="trust-item">
      <el-icon :size="20" class="trust-icon"><Lock /></el-icon>
      <span class="trust-text">安全支付保障</span>
    </div>
    <div class="trust-divider" />
    <div class="trust-item">
      <el-icon :size="20" class="trust-icon"><ChatDotRound /></el-icon>
      <span class="trust-text">24h 在线客服</span>
    </div>
    <div class="trust-divider" />
    <div class="trust-item">
      <el-icon :size="20" class="trust-icon"><RefreshLeft /></el-icon>
      <span class="trust-text">灵活退改政策</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { UserFilled, Lock, ChatDotRound, RefreshLeft } from "@element-plus/icons-vue";
</script>

<style scoped>
.trust-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px 24px;
  background: #f8f9fa;
  border-radius: 12px;
  margin-bottom: 32px;
  flex-wrap: wrap;
}

.trust-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
}

.trust-icon {
  color: #667eea;
}

.trust-divider {
  width: 1px;
  height: 20px;
  background: #dcdfe6;
}

@media (max-width: 768px) {
  .trust-bar {
    gap: 12px;
    padding: 12px 16px;
  }

  .trust-item {
    font-size: 13px;
  }

  .trust-divider {
    display: none;
  }
}
</style>
