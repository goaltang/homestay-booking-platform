<template>
  <el-dialog
    :model-value="modelValue"
    title="设置准备入住"
    width="50%"
    @update:model-value="emit('update:modelValue', $event)"
    @open="reset"
  >
    <div v-if="order" class="prepare-checkin-content">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 20px">
        <template #title>
          <span>为订单 <strong>#{{ order.id }}</strong> 设置入住凭证</span>
        </template>
      </el-alert>

      <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
        <el-descriptions-item label="房源">{{ order.homestayTitle || order.homestayName }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ order.guestName }}</el-descriptions-item>
        <el-descriptions-item label="入住日期">{{ order.checkInDate }}</el-descriptions-item>
        <el-descriptions-item label="退房日期">{{ order.checkOutDate }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="form" label-width="120px">
        <el-form-item label="入住方式">
          <el-radio-group v-model="form.checkInMethod">
            <el-radio label="MANUAL">人工办理</el-radio>
            <el-radio label="SELF_SERVICE">自助入住</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="门锁密码"
          v-if="form.checkInMethod === 'MANUAL' || form.checkInMethod === 'SELF_SERVICE'"
        >
          <el-input v-model="form.doorPassword" placeholder="请输入门锁密码" />
        </el-form-item>
        <el-form-item label="密钥箱密码" v-if="form.checkInMethod === 'SELF_SERVICE'">
          <el-input v-model="form.lockboxCode" placeholder="请输入密钥箱密码" />
        </el-form-item>
        <el-form-item label="位置描述" v-if="form.checkInMethod === 'SELF_SERVICE'">
          <el-input
            v-model="form.locationDescription"
            type="textarea"
            :rows="2"
            placeholder="请输入房源位置描述，帮助客人找到房源"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填：备注信息" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认设置</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import type { HostOrderItem } from "@/types/hostOrder";
import { prepareCheckIn } from "@/api/hostOrder";

const props = defineProps<{
  modelValue: boolean;
  order: HostOrderItem | null;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  confirmed: [];
}>();

const submitting = ref(false);
const form = reactive({
  checkInMethod: "MANUAL",
  doorPassword: "",
  lockboxCode: "",
  locationDescription: "",
  remark: "",
});

const reset = () => {
  form.checkInMethod = "MANUAL";
  form.doorPassword = "";
  form.lockboxCode = "";
  form.locationDescription = "";
  form.remark = "";
};

const handleSubmit = async () => {
  if (!props.order?.id) return;
  submitting.value = true;
  try {
    await prepareCheckIn(props.order.id, {
      checkInMethod: form.checkInMethod,
      doorPassword: form.doorPassword || undefined,
      lockboxCode: form.lockboxCode || undefined,
      locationDescription: form.locationDescription || undefined,
      remark: form.remark || undefined,
    });
    ElMessage.success("已设置准备入住");
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
