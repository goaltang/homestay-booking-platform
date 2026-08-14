<template>
  <el-dialog
    :model-value="modelValue"
    title="发起退款"
    width="45%"
    @update:model-value="emit('update:modelValue', $event)"
    @open="fetchPreview"
  >
    <div v-if="order" class="refund-dialog-content">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 20px">
        <template #title>
          <span>确认对订单 <strong>#{{ order.id }}</strong> 发起退款？退款将原路退回给客户。</span>
        </template>
      </el-alert>

      <el-descriptions :column="1" border size="small" class="refund-order-info">
        <el-descriptions-item label="房源名称">
          {{ order.homestayTitle || order.homestayName }}
        </el-descriptions-item>
        <el-descriptions-item label="预订客户">{{ order.guestName }}</el-descriptions-item>
        <el-descriptions-item label="入住日期">
          {{ order.checkInDate }} 至 {{ order.checkOutDate }}
        </el-descriptions-item>
        <el-descriptions-item label="退款金额">
          <span
            v-if="preview && preview.estimatedRefundAmount !== undefined"
            class="refund-amount-highlight"
          >
            ¥{{ formatAmount(preview.estimatedRefundAmount) }}
          </span>
          <span v-else class="refund-amount-highlight">计算中...</span>
          <el-tag
            v-if="preview && preview.policyDescription"
            size="small"
            type="warning"
            effect="plain"
            style="margin-left: 8px"
          >
            {{ preview.policyDescription }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" ref="formRef" style="margin-top: 20px">
        <el-form-item
          label="退款原因"
          prop="reason"
          :rules="[{ required: true, message: '请输入退款原因', trigger: 'blur' }]"
        >
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入退款原因，例如：客户要求取消订单、房源临时不可用等"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleSubmit">确认发起退款</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance } from "element-plus";
import type { HostOrderItem } from "@/types/hostOrder";
import { getRefundPreview, hostInitiateRefund } from "@/api/hostOrder";
import { formatAmount } from "@/utils/orderDisplay";

const props = defineProps<{
  modelValue: boolean;
  order: HostOrderItem | null;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  /** 退款成功，父组件刷新列表 */
  confirmed: [];
}>();

const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive({ reason: "" });
const preview = ref<{ estimatedRefundAmount?: number; policyDescription?: string } | null>(null);

const fetchPreview = async () => {
  if (!props.order?.id) return;
  preview.value = null;
  form.reason = "";
  try {
    const res = await getRefundPreview(props.order.id);
    preview.value = res.data || res;
  } catch (err) {
    console.warn("获取退款预览失败:", err);
  }
};

const handleSubmit = async () => {
  if (!formRef.value || !props.order) return;
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    submitting.value = true;
    try {
      await hostInitiateRefund(props.order!.id, form.reason);
      ElMessage.success("退款申请已提交，等待处理");
      emit("confirmed");
      emit("update:modelValue", false);
    } finally {
      submitting.value = false;
    }
  });
};
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
