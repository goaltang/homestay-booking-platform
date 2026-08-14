<template>
  <el-dialog
    :model-value="modelValue"
    title="入住凭证"
    width="450px"
    @update:model-value="emit('update:modelValue', $event)"
    @open="loadCredential"
  >
    <div v-if="credential" class="credential-content">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="入住方式">
          {{ credential.checkInMethod === "MANUAL" ? "人工办理" : "自助入住" }}
        </el-descriptions-item>
        <el-descriptions-item label="入住码">
          <span style="font-weight: bold; font-size: 20px; color: var(--el-color-primary);">
            {{ credential.checkInCode }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="门锁密码" v-if="credential.doorPassword">
          {{ credential.doorPassword }}
        </el-descriptions-item>
        <el-descriptions-item label="密钥箱密码" v-if="credential.lockboxCode">
          {{ credential.lockboxCode }}
        </el-descriptions-item>
        <el-descriptions-item label="位置描述" :span="2" v-if="credential.locationDescription">
          {{ credential.locationDescription }}
        </el-descriptions-item>
        <el-descriptions-item label="有效时间" v-if="credential.validFrom || credential.validUntil">
          {{ credential.validFrom }} ~ {{ credential.validUntil }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2" v-if="credential.remark">
          {{ credential.remark }}
        </el-descriptions-item>
      </el-descriptions>
      <el-alert type="info" :closable="false" style="margin-top: 15px;">
        请保管好您的入住码，到达后可用于自助办理入住。
      </el-alert>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { getCheckInCredential } from "@/api/order";

const props = defineProps<{
  modelValue: boolean;
  orderId?: number | null;
}>();

const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();

const credential = ref<Record<string, unknown> | null>(null);

const loadCredential = async () => {
  credential.value = null;
  if (!props.orderId) return;
  try {
    const res = await getCheckInCredential(props.orderId);
    credential.value = res.data || res;
  } catch (error) {
    // 错误已由拦截器统一提示
  }
};
</script>
