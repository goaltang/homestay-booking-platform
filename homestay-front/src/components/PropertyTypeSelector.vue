<template>
  <div class="property-types-selector">
    <el-tabs v-model="activeCategory">
      <el-tab-pane
        v-for="(types, category) in typesByCategory"
        :key="category"
        :label="category"
        :name="category"
      >
        <div class="type-cards-container">
          <div
            v-for="type in types"
            :key="type.code"
            :class="['type-card', modelValue === type.code ? 'selected' : '']"
            @click="$emit('update:modelValue', type.code)"
          >
            <div class="type-icon">
              <el-image v-if="type.icon" :src="type.icon" fit="cover">
                <template #error>
                  <div class="fallback-icon">
                    <i class="el-icon-house"></i>
                  </div>
                </template>
              </el-image>
              <div class="fallback-icon" v-else>
                <i class="el-icon-house"></i>
              </div>
            </div>
            <div class="type-info">
              <div class="type-name">{{ type.name }}</div>
              <div class="type-desc" v-if="type.description">{{ type.description }}</div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <div v-if="!hasTypes" class="empty-warning">
      <el-alert title="暂无可选房源类型" type="warning" :closable="false" />

      <!-- 传统下拉菜单的降级方案 -->
      <div class="fallback-select" v-if="fallbackOptions.length > 0">
        <el-select
          v-model="fallbackValue"
          placeholder="请选择房源类型"
          style="width: 100%; margin-top: 10px"
        >
          <el-option
            v-for="option in fallbackOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { getHomestayTypesByCategory, getHomestayTypes } from "@/api/homestay";

defineProps({
  modelValue: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["update:modelValue"]);

const typesByCategory = ref<Record<string, any[]>>({});
const fallbackOptions = ref<any[]>([]);
const activeCategory = ref<string>("");
const fallbackValue = ref<string>("");

const hasTypes = computed(() => Object.values(typesByCategory.value).flat().length > 0);

// 监听fallback的修改
watch(fallbackValue, (newValue) => {
  if (newValue) {
    emit("update:modelValue", newValue);
  }
});

// 获取房源类型数据
const fetchData = async () => {
  try {
    // 获取分类房源类型
    const response = await getHomestayTypesByCategory();
    typesByCategory.value = response?.data?.data || {};

    // 设置默认选中的分类，如果当前没有选中的分类
    if (Object.keys(typesByCategory.value).length > 0 && !activeCategory.value) {
      activeCategory.value = Object.keys(typesByCategory.value)[0];
    }

    // 作为备用，也获取非分类的房源类型列表
    const legacyResponse = await getHomestayTypes();
    fallbackOptions.value = legacyResponse || [];
  } catch (error) {
    console.error("获取房源类型数据失败：", error);
  }
};

onMounted(fetchData);
</script>

<style scoped>
.property-types-selector {
  width: 100%;
}

.type-cards-container {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  margin-top: 10px;
}

.type-card {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  min-width: 200px;
  width: calc(33.33% - 10px);
  box-sizing: border-box;
  cursor: pointer;
  transition: all 0.3s;
}

.type-card:hover {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.type-card.selected {
  border-color: #409eff;
  background-color: #f0f9ff;
}

.type-icon {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background-color: #f5f7fa;
  overflow: hidden;
}

.fallback-icon {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 24px;
}

.type-icon .el-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.type-info {
  flex: 1;
}

.type-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.type-desc {
  font-size: 12px;
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-warning {
  margin-top: 10px;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .type-card {
    width: calc(50% - 8px);
  }
}

@media (max-width: 576px) {
  .type-card {
    width: 100%;
  }
}
</style>
