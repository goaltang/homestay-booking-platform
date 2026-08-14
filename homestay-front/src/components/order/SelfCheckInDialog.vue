<template>
  <el-dialog
    :model-value="modelValue"
    title="自助入住"
    width="400px"
    @update:model-value="emit('update:modelValue', $event)"
    @open="code = ''"
  >
    <div class="self-checkin-content">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 20px;">
        请输入房东提供的6位入住码完成入住。
      </el-alert>
      <el-form>
        <el-form-item label="入住码">
          <el-input v-model="code" placeholder="请输入6位入住码" maxlength="6" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">确认入住</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { selfCheckIn } from "@/api/order";

defineProps<{ modelValue: boolean }>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  confirmed: [];
}>();

const code = ref("");
const loading = ref(false);

const handleSubmit = async () => {
  if (!code.value) {
    ElMessage.warning("请输入入住码");
    return;
  }
  loading.value = true;
  try {
    await selfCheckIn(code.value);
    ElMessage.success("自助入住成功");
    emit("confirmed");
    emit("update:modelValue", false);
  } finally {
    loading.value = false;
  }
};
</script>
