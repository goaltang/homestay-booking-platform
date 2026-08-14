<template>
  <el-dialog
    :model-value="modelValue"
    title="入住凭证"
    width="50%"
    @update:model-value="emit('update:modelValue', $event)"
    @open="loadCredential"
  >
    <div v-if="credential" class="credential-content">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="入住方式">
          {{ credential.checkInMethod === "MANUAL" ? "人工办理" : "自助入住" }}
        </el-descriptions-item>
        <el-descriptions-item label="入住码">
          <span style="font-weight: bold; font-size: 18px; color: var(--el-color-primary)">
            {{ credential.checkInCode }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="门锁密码" v-if="credential.doorPassword">{{ credential.doorPassword }}</el-descriptions-item>
        <el-descriptions-item label="密钥箱密码" v-if="credential.lockboxCode">{{ credential.lockboxCode }}</el-descriptions-item>
        <el-descriptions-item label="位置描述" :span="2" v-if="credential.locationDescription">{{ credential.locationDescription }}</el-descriptions-item>
        <el-descriptions-item label="有效起始" v-if="credential.validFrom">{{ credential.validFrom }}</el-descriptions-item>
        <el-descriptions-item label="有效截止" v-if="credential.validUntil">{{ credential.validUntil }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2" v-if="credential.remark">{{ credential.remark }}</el-descriptions-item>
      </el-descriptions>
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
import { getCheckInCredential } from "@/api/hostOrder";
import type { HostOrderItem } from "@/types/hostOrder";

const props = defineProps<{
  modelValue: boolean;
  order: HostOrderItem | null;
}>();

const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();

const credential = ref<Record<string, unknown> | null>(null);

const loadCredential = async () => {
  credential.value = null;
  if (!props.order?.id) return;
  try {
    const res = await getCheckInCredential(props.order.id);
    credential.value = res.data || res;
  } catch (error) {
    console.error("获取入住凭证失败:", error);
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
