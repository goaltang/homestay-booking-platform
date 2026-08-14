<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="40%"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" ref="formRef">
      <el-form-item
        label="原因"
        prop="reason"
        :rules="[{ required: true, message: `请输入${label}`, trigger: 'blur' }]"
      >
        <el-input v-model="form.reason" type="textarea" :rows="3" :placeholder="`请输入${label}`" />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleSubmit">
          {{ confirmText }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import type { FormInstance } from "element-plus";

const props = defineProps<{
  modelValue: boolean;
  title: string;
  /** 原因文案，如"取消原因"/"拒绝原因" */
  label: string;
  confirmText: string;
  /** 提交回调：由父组件执行实际 API 调用 */
  onSubmit: (reason: string) => Promise<void>;
}>();

const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();

const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive({ reason: "" });

const handleSubmit = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    submitting.value = true;
    try {
      await props.onSubmit(form.reason);
      form.reason = "";
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
