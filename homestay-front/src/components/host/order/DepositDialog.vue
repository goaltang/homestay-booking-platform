<template>
  <el-dialog
    :model-value="modelValue"
    title="押金操作"
    width="50%"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="order && record" class="deposit-content">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 20px">
        <template #title>
          <span>请选择押金操作类型</span>
        </template>
      </el-alert>

      <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
        <el-descriptions-item label="押金状态">
          <el-tag :type="getDepositStatusType(record.depositStatus || '')" size="small">
            {{ getDepositStatusText(record.depositStatus || '') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="押金金额">¥{{ record.depositAmount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="结算金额">¥{{ record.settlementAmount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="额外费用">¥{{ record.extraCharges || 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" label-width="100px">
        <el-form-item label="操作类型" required>
          <el-radio-group v-model="form.action">
            <el-radio label="COLLECT">收取押金</el-radio>
            <el-radio label="REFUND">退还押金</el-radio>
            <el-radio label="RETAIN">扣押押金</el-radio>
            <el-radio label="WAIVE">免除押金</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" v-if="form.action === 'COLLECT' || form.action === 'RETAIN'">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="10" style="width: 200px" />
        </el-form-item>
        <el-form-item label="说明" v-if="form.action === 'RETAIN'">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="请输入扣押原因" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认操作</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import { ElMessage } from "element-plus";
import type { HostOrderItem } from "@/types/hostOrder";
import { getCheckOutRecord, processDeposit } from "@/api/hostOrder";
import { getDepositStatusText, getDepositStatusType } from "@/utils/orderDisplay";

const props = defineProps<{
  modelValue: boolean;
  order: HostOrderItem | null;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  confirmed: [];
}>();

const submitting = ref(false);
const record = ref<{ depositStatus?: string; depositAmount?: number; settlementAmount?: number; extraCharges?: number } | null>(null);
const form = reactive({ action: "REFUND", amount: 0, note: "" });

const loadRecord = async () => {
  if (!props.order?.id) return;
  record.value = null;
  try {
    const res = await getCheckOutRecord(props.order.id);
    record.value = res.data || res;
    form.action = "REFUND";
    form.amount = record.value?.depositAmount || 0;
    form.note = "";
  } catch (err) {
    console.warn("获取退房记录失败:", err);
  }
};

watch(() => props.modelValue, (v) => {
  if (v) loadRecord();
});

const handleSubmit = async () => {
  if (!props.order?.id) return;
  if ((form.action === "COLLECT" || form.action === "RETAIN") && form.amount <= 0) {
    ElMessage.error("请输入有效的金额");
    return;
  }
  submitting.value = true;
  try {
    await processDeposit(
      props.order.id,
      form.action,
      form.action === "COLLECT" || form.action === "RETAIN" ? form.amount : undefined,
      form.action === "RETAIN" ? form.note : undefined
    );
    ElMessage.success("押金操作成功");
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
