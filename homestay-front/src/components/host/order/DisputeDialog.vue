<template>
  <el-dialog
    :model-value="modelValue"
    title="发起争议"
    width="45%"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="order" class="refund-dialog-content">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 20px">
        <template #title>
          <span>对订单 <strong>#{{ order.id }}</strong> 的退款有异议？</span>
        </template>
        <div>发起争议后，订单将进入争议处理流程，需要管理员进行仲裁。</div>
      </el-alert>

      <el-descriptions :column="1" border size="small" class="refund-order-info">
        <el-descriptions-item label="订单号">{{ order.orderNumber || order.id }}</el-descriptions-item>
        <el-descriptions-item label="退款原因">{{ order.refundReason || "无" }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">
          <span class="refund-amount-highlight">
            ¥{{ formatAmount(order.refundAmount || order.totalAmount) }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" ref="formRef" style="margin-top: 20px">
        <el-form-item
          label="争议原因"
          prop="reason"
          :rules="[{ required: true, message: '请输入争议原因', trigger: 'blur' }]"
        >
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入您认为不应退款的原因，例如：客人违反了房屋使用规定、房屋损坏等"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleSubmit">发起争议</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance } from "element-plus";
import type { HostOrderItem } from "@/types/hostOrder";
import { hostRaiseDispute } from "@/api/hostOrder";
import { formatAmount } from "@/utils/orderDisplay";

const props = defineProps<{
  modelValue: boolean;
  order: HostOrderItem | null;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  confirmed: [];
}>();

const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive({ reason: "" });

const handleSubmit = async () => {
  if (!formRef.value || !props.order) return;
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    submitting.value = true;
    try {
      await hostRaiseDispute(props.order!.id, form.reason);
      ElMessage.success("争议已发起，等待管理员仲裁");
      form.reason = "";
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
