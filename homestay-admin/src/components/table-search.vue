<script lang="ts" setup>
import { FormInstance } from "element-plus";
import { Search, Refresh } from "@element-plus/icons-vue";
import { PropType, reactive, ref } from "vue";
import { FormOptionList } from "@/types/form-option";

const props = defineProps({
  query: {
    type: Object,
    required: true,
  },
  options: {
    type: Array as PropType<Array<FormOptionList>>,
    required: true,
  },
  search: {
    type: Function,
    default: () => {},
  },
});

const searchRef = ref<FormInstance>();

// 远程搜索状态
const remoteLoading = reactive<Record<string, boolean>>({});
const remoteOptions = reactive<Record<string, Array<{ label: string; value: any }>>>({});

const handleRemoteSearch = async (item: FormOptionList, query: string) => {
  if (!item.remoteMethod) return;
  remoteLoading[item.prop] = true;
  try {
    const result = await item.remoteMethod(query);
    remoteOptions[item.prop] = result;
  } finally {
    remoteLoading[item.prop] = false;
  }
};

// 保留首次显示时的筛选默认值，重置时只更新配置中的字段。
const initialQuery = Object.fromEntries(
  Object.entries(props.query).map(([key, value]) => [
    key,
    Array.isArray(value) ? [...value] : value,
  ])
);
const emit = defineEmits<{
  "update:query": [query: Record<string, unknown>];
}>();

const updateQuery = (prop: string, value: unknown) => {
  emit("update:query", { ...props.query, [prop]: value });
};

const updateRange = (prop: string, index: number, value: unknown) => {
  const range = Array.isArray(props.query[prop]) ? [...props.query[prop]] : [null, null];
  range[index] = value;
  updateQuery(prop, range);
};

const resetForm = () => {
  const nextQuery = { ...props.query };
  props.options.forEach((item) => {
    const initial = initialQuery[item.prop];
    nextQuery[item.prop] =
      item.type === "number-range" ? [null, null] : Array.isArray(initial) ? [...initial] : initial;
  });
  emit("update:query", nextQuery);
  searchRef.value?.clearValidate();
  props.search();
};
</script>

<template>
  <div class="search-container">
    <el-form ref="searchRef" :model="query" :inline="true">
      <template v-for="item in options" :key="item.prop">
        <el-form-item :label="item.label" :prop="item.prop">
          <!-- 文本框 -->
          <el-input
            v-if="item.type === 'input'"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :placeholder="item.placeholder || '请输入' + item.label"
            :clearable="item.clearable !== false"
          >
          </el-input>

          <!-- 下拉框 -->
          <el-select
            v-else-if="item.type === 'select'"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :placeholder="item.placeholder || '请选择' + item.label"
            :clearable="item.clearable !== false"
            :multiple="item.multiple"
          >
            <el-option
              v-for="opt in item.opts"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>

          <!-- 远程搜索下拉框 -->
          <el-select
            v-else-if="item.type === 'remote-select'"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :placeholder="item.placeholder || '请输入' + item.label + '搜索'"
            :clearable="item.clearable !== false"
            :multiple="item.multiple"
            filterable
            remote
            :remote-method="(q: string) => handleRemoteSearch(item, q)"
            :loading="remoteLoading[item.prop]"
          >
            <el-option
              v-for="opt in remoteOptions[item.prop] || []"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>

          <!-- 日期选择器 -->
          <el-date-picker
            v-else-if="item.type === 'date'"
            type="date"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :placeholder="item.placeholder || '选择日期'"
            :value-format="item.format || 'YYYY-MM-DD'"
            :clearable="item.clearable !== false"
          >
          </el-date-picker>

          <!-- 日期时间选择器 -->
          <el-date-picker
            v-else-if="item.type === 'datetime'"
            type="datetime"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :placeholder="item.placeholder || '选择日期时间'"
            :value-format="item.format || 'YYYY-MM-DD HH:mm:ss'"
            :clearable="item.clearable !== false"
          >
          </el-date-picker>

          <!-- 日期范围选择器 -->
          <el-date-picker
            v-else-if="item.type === 'daterange'"
            type="daterange"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :start-placeholder="'开始'"
            :end-placeholder="'结束'"
            :range-separator="item.rangeSeparator || '至'"
            :value-format="item.format || 'YYYY-MM-DD'"
            :clearable="item.clearable !== false"
          >
          </el-date-picker>

          <!-- 日期时间范围选择器 -->
          <el-date-picker
            v-else-if="item.type === 'datetimerange'"
            type="datetimerange"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :start-placeholder="'开始'"
            :end-placeholder="'结束'"
            :range-separator="item.rangeSeparator || '至'"
            :value-format="item.format || 'YYYY-MM-DD HH:mm:ss'"
            :clearable="item.clearable !== false"
          >
          </el-date-picker>

          <!-- 数字输入 -->
          <el-input-number
            v-else-if="item.type === 'number'"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :placeholder="item.placeholder || '请输入' + item.label"
            :min="item.min"
            :max="item.max"
            :precision="item.precision"
            :clearable="item.clearable !== false"
            controls-position="right"
            style="width: 180px"
          >
          </el-input-number>

          <!-- 数字范围 -->
          <div v-else-if="item.type === 'number-range'" class="number-range">
            <el-input-number
              :model-value="query[item.prop]?.[0]"
              @update:model-value="updateRange(item.prop, 0, $event)"
              :disabled="item.disabled"
              placeholder="最小值"
              :min="item.min"
              :max="item.max"
              :precision="item.precision"
              controls-position="right"
              style="width: 100px"
            />
            <span class="range-separator">{{ item.rangeSeparator || "-" }}</span>
            <el-input-number
              :model-value="query[item.prop]?.[1]"
              @update:model-value="updateRange(item.prop, 1, $event)"
              :disabled="item.disabled"
              placeholder="最大值"
              :min="item.min"
              :max="item.max"
              :precision="item.precision"
              controls-position="right"
              style="width: 100px"
            />
          </div>

          <!-- 开关 -->
          <el-switch
            v-else-if="item.type === 'switch'"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :disabled="item.disabled"
            :active-value="true"
            :inactive-value="false"
            :active-text="(item.opts && item.opts[0]?.label) || '是'"
            :inactive-text="(item.opts && item.opts[1]?.label) || '否'"
          >
          </el-switch>

          <!-- 级联选择器 -->
          <el-cascader
            v-else-if="item.type === 'cascader'"
            :model-value="query[item.prop]"
            @update:model-value="updateQuery(item.prop, $event)"
            :options="item.opts"
            :disabled="item.disabled"
            :placeholder="item.placeholder || '请选择' + item.label"
            :props="item.cascaderProps || { checkStrictly: true }"
            :clearable="item.clearable !== false"
            style="width: 220px"
          >
          </el-cascader>
        </el-form-item>
      </template>

      <el-form-item>
        <el-button type="primary" :icon="Search" @click="search">搜索</el-button>
        <el-button :icon="Refresh" @click="resetForm">重置</el-button>
      </el-form-item>

      <!-- 自定义操作按钮插槽（如新增、导出等） -->
      <el-form-item v-if="$slots.actions">
        <slot name="actions" />
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.search-container {
  padding: 20px 30px 0;
  background-color: #fff;
  margin-bottom: 10px;
  border: 1px solid #ddd;
  border-radius: 5px;
}

.number-range {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.range-separator {
  color: #909399;
  font-size: 14px;
}
</style>
