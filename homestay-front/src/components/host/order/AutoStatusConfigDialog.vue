<template>
  <el-dialog
    :model-value="modelValue"
    title="自动状态流转配置"
    width="60%"
    @update:model-value="emit('update:modelValue', $event)"
    @open="loadConfig"
  >
    <div v-if="config" class="auto-config-content">
      <el-descriptions title="配置信息" :column="2" border>
        <el-descriptions-item label="自动入住时间">{{ config.autoCheckInTime }}</el-descriptions-item>
        <el-descriptions-item label="自动完成时间">{{ config.autoCheckOutTime }}</el-descriptions-item>
        <el-descriptions-item label="错过入住处理时间">{{ config.cancelMissedCheckInTime }}</el-descriptions-item>
        <el-descriptions-item label="检查频率">{{ config.checkInterval }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left"><strong>自动流转规则</strong></el-divider>

      <div v-if="config.rules" class="rules-content">
        <el-card v-for="(rule, key) in config.rules" :key="key" class="rule-card" shadow="never">
          <template #header><span class="rule-title">{{ key }}</span></template>
          <p class="rule-description">{{ rule }}</p>
        </el-card>
      </div>

      <el-alert title="说明" type="info" :closable="false" style="margin-top: 20px">
        <p>系统会根据上述规则自动处理订单状态流转，减少人工干预，提高效率。</p>
        <p>如有特殊情况需要人工处理，请及时联系系统管理员。</p>
      </el-alert>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import request from "@/utils/request";

defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();

const config = ref<Record<string, unknown> | null>(null);

const loadConfig = async () => {
  config.value = null;
  try {
    const response = await request({ url: "/api/host/order-auto-status/config", method: "get" });
    if (response?.data) {
      config.value = response.data;
    }
  } catch (error) {
    console.error("获取自动状态配置失败:", error);
  }
};
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
