<template>
  <el-dialog
    :model-value="modelValue"
    title="办理退房"
    width="50%"
    @update:model-value="emit('update:modelValue', $event)"
    @open="form.remark = ''"
  >
    <div v-if="order" class="checkout-content">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 20px">
        <template #title>
          <span>确认办理订单 <strong>#{{ order.id }}</strong> 退房</span>
        </template>
      </el-alert>

      <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
        <el-descriptions-item label="房源">{{ order.homestayTitle || order.homestayName }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ order.guestName }}</el-descriptions-item>
        <el-descriptions-item label="入住时间">{{ order.checkedInAt || order.checkInDate }}</el-descriptions-item>
        <el-descriptions-item label="退房时间">{{ formatDateTime(new Date().toISOString()) }}</el-descriptions-item>
        <el-descriptions-item label="押金金额">¥{{ order.depositAmount || 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" label-width="100px">
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填：退房备注" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认退房</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import type { HostOrderItem } from "@/types/hostOrder";
import { performCheckOut } from "@/api/hostOrder";
import { formatDateTime } from "@/utils/orderDisplay";

const props = defineProps<{
  modelValue: boolean;
  order: HostOrderItem | null;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  confirmed: [];
}>();

const submitting = ref(false);
const form = reactive({ remark: "" });

const handleSubmit = async () => {
  if (!props.order?.id) return;
  submitting.value = true;
  try {
    await performCheckOut(props.order.id, { remark: form.remark || undefined });
    ElMessage.success("已办理退房");
    emit("confirmed");
    emit("update:modelValue", false);
  } finally {
    submitting.value = false;
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
