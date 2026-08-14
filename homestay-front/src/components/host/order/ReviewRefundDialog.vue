<template>
  <el-dialog
    :model-value="modelValue"
    title="审核退款申请"
    width="45%"
    @update:model-value="emit('update:modelValue', $event)"
    @open="reset"
  >
    <div v-if="order" class="refund-dialog-content">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 20px">
        <template #title>
          <span>请审核对订单 <strong>#{{ order.id }}</strong> 发起的退款申请。</span>
        </template>
      </el-alert>

      <el-descriptions :column="1" border size="small" class="refund-order-info">
        <el-descriptions-item label="退款原因">{{ order.refundReason || "无" }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">
          <span class="refund-amount-highlight">
            ¥{{ formatAmount(order.refundAmount || order.totalAmount) }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" ref="formRef" style="margin-top: 20px">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="form.action">
            <el-radio label="approve" value="approve">同意退款</el-radio>
            <el-radio label="reject" value="reject">拒绝退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          :label="form.action === 'approve' ? '同意备注' : '拒绝原因'"
          prop="reason"
          :rules="[{ required: form.action === 'reject', message: '请输入拒绝原因', trigger: 'blur' }]"
        >
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            :placeholder="form.action === 'approve' ? '选填：同意退款备注' : '必填：请输入拒绝退款的原因'"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button
          :type="form.action === 'approve' ? 'success' : 'danger'"
          :loading="submitting"
          @click="handleSubmit"
        >
          确认提交
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance } from "element-plus";
import type { HostOrderItem } from "@/types/hostOrder";
import { hostApproveRefund, hostRejectRefund } from "@/api/hostOrder";
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
const form = reactive({ action: "approve", reason: "" });

const reset = () => {
  form.action = "approve";
  form.reason = "";
};

const handleSubmit = async () => {
  if (!formRef.value || !props.order) return;
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    submitting.value = true;
    try {
      if (form.action === "approve") {
        await hostApproveRefund(props.order!.id, form.reason);
        ElMessage.success("已同意退款");
      } else {
        await hostRejectRefund(props.order!.id, form.reason);
        ElMessage.success("已拒绝退款");
      }
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
