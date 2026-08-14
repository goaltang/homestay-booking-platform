<template>
    <div class="amenity-selector">
        <!-- 已选设施摘要 - 新增，放在顶部显示 -->
        <div class="selected-summary" v-if="normalizedModelValue.length > 0">
            <div class="summary-header">
                <el-badge :value="normalizedModelValue.length" type="primary">
                    <span class="summary-title">已选设施</span>
                </el-badge>
                <el-button type="danger" text size="small" @click="clearAll">
                    清空所有
                </el-button>
            </div>
            <div class="selected-pills">
                <div v-for="item in normalizedModelValue" :key="typeof item === 'object' ? item.value : item"
                    class="selected-pill">
                    <span class="pill-icon" v-if="getAmenityIcon(typeof item === 'object' ? item.value : item)">
                        <el-icon>
                            <component :is="getAmenityIcon(typeof item === 'object' ? item.value : item)" />
                        </el-icon>
                    </span>
                    <span class="pill-text">{{ typeof item === 'object' && item.label ? item.label :
                        getAmenityLabel(typeof item === 'object' ? item.value : item) }}</span>
                    <el-icon class="pill-delete" @click.stop="removeAmenity(item)">
                        <Close />
                    </el-icon>
                </div>
            </div>
        </div>

        <el-divider v-if="normalizedModelValue.length > 0" content-position="center">选择更多设施</el-divider>

        <el-tabs v-model="activeCategory" class="amenity-tabs">
            <el-tab-pane v-for="category in amenitiesByCategory" :key="category.code" :label="category.name"
                :name="category.code">
                <div class="amenity-actions" v-if="amenitiesByCategory.length > 0">
                    <el-button type="primary" size="small" @click="handleQuickAddAmenities(category.code)">
                        一键添加<span class="hidden-xs-only">{{ category.name }}</span>
                        <el-icon>
                            <Plus />
                        </el-icon>
                    </el-button>
                </div>

                <div class="amenity-cards-container">
                    <div v-for="item in category.amenities" :key="item.value"
                        :class="['amenity-card', isSelected(item) ? 'selected' : '']" @click="toggleAmenity(item)">
                        <div class="amenity-icon">
                            <el-icon v-if="item.icon">
                                <component :is="item.icon" />
                            </el-icon>
                            <div class="fallback-icon" v-else>
                                <i class="el-icon-setting"></i>
                            </div>
                        </div>
                        <div class="amenity-info">
                            <div class="amenity-name">{{ item.label }}</div>
                            <div class="amenity-desc" v-if="item.description">{{ item.description }}</div>
                        </div>
                        <div class="amenity-check" v-if="isSelected(item)">
                            <el-icon>
                                <Check />
                            </el-icon>
                        </div>
                    </div>
                </div>
            </el-tab-pane>
        </el-tabs>

        <div v-if="amenitiesByCategory.length === 0" class="empty-warning">
            <el-alert title="正在加载设施数据..." type="info" :closable="false" />
            <el-skeleton :rows="3" animated />
        </div>

        <div class="amenity-global-actions" v-if="amenitiesByCategory.length > 0">
            <el-tooltip content="添加所有可用设施" placement="top">
                <el-button type="success" plain size="small" @click="handleQuickAddAmenities('all')">
                    一键添加所有设施
                    <el-icon>
                        <Plus />
                    </el-icon>
                </el-button>
            </el-tooltip>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { getAmenitiesByCategoryApi } from '@/api/amenities'
import { Check, Plus, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

// 定义设施类型接口
interface Amenity {
    value: string;
    label: string;
    icon?: string;
    description?: string;
}

// 定义设施分类接口
interface AmenityCategory {
    code: string;
    name: string;
    icon?: string;
    amenities: Amenity[];
}

const props = defineProps({
    modelValue: {
        type: [Array, String, Object],
        default: () => []
    },
    multiple: {
        type: Boolean,
        default: true
    },
    categoryCode: {
        type: String,
        default: ''
    },
    showSelectedOnly: {
        type: Boolean,
        default: false
    },
    disabled: {
        type: Boolean,
        default: false
    }
})

const emit = defineEmits(['update:modelValue', 'change'])

const amenitiesByCategory = ref<AmenityCategory[]>([])
const activeCategory = ref<string>('')
const amenitiesMap = ref<Map<string, Amenity>>(new Map())
// 规范化处理modelValue，确保即使是JSON字符串也能正确解析
const normalizedModelValue = computed(() => {
    console.log('原始modelValue类型:', typeof props.modelValue)
    console.log('原始modelValue值:', props.modelValue)

    // 处理字符串输入（可能是JSON字符串）
    if (typeof props.modelValue === 'string') {
        try {
            // 尝试解析JSON字符串
            const parsed = JSON.parse(props.modelValue)
            console.log('解析后的JSON:', parsed)
            // 解析成功后，可能是数组或单个对象
            if (Array.isArray(parsed)) {
                return parsed
            } else {
                return [parsed]
            }
        } catch (e) {
            // 如果解析失败，则视为单个字符串值
            console.log('解析JSON失败:', e)
            return [{ value: props.modelValue }]
        }
    }

    // 处理对象输入
    if (props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue)) {
        return [props.modelValue]
    }

    // 处理数组输入
    if (Array.isArray(props.modelValue)) {
        return props.modelValue.map(item => {
            if (typeof item === 'string') {
                try {
                    // 尝试解析可能是JSON字符串的单个项
                    return JSON.parse(item)
                } catch (e) {
                    return { value: item }
                }
            }
            return item
        })
    }

    // 默认返回空数组
    return []
})

// 获取已选设施的值列表
const selectedValues = computed(() => {
    return normalizedModelValue.value.map(item => {
        if (typeof item === 'object' && item !== null) {
            return item.value
        }
        return String(item)
    })
})

// 检查设施是否被选中
const isSelected = (amenity: Amenity) => {
    return selectedValues.value.includes(amenity.value)
}

// 切换选中状态
const toggleAmenity = (amenity: Amenity) => {
    if (props.disabled) return;

    let newValue

    if (props.multiple) {
        // 多选模式
        if (isSelected(amenity)) {
            // 如果已选中，则移除
            newValue = normalizedModelValue.value.filter(item => {
                const itemValue = typeof item === 'object' && item !== null ? item.value : item
                return itemValue !== amenity.value
            })
        } else {
            // 如果未选中，则添加
            newValue = [...normalizedModelValue.value, amenity]
        }
    } else {
        // 单选模式
        newValue = [amenity]
    }

    // 发射规范化后的数据
    emit('update:modelValue', newValue);
}

// 移除设施
const removeAmenity = (amenity: Amenity | string) => {
    let amenityValue = typeof amenity === 'object' && amenity !== null ? amenity.value : amenity

    const newValues = normalizedModelValue.value.filter(item => {
        const itemValue = typeof item === 'object' && item !== null ? item.value : item
        return itemValue !== amenityValue
    })

    emit('update:modelValue', newValues)
}

// 获取设施标签文本
const getAmenityLabel = (amenityValue: string) => {
    if (!amenityValue) return '';

    // 在所有分类中查找匹配的设施
    for (const category of amenitiesByCategory.value) {
        for (const amenity of category.amenities) {
            if (amenity.value === amenityValue) {
                return amenity.label;
            }
        }
    }

    // 如果在当前分类中找不到，则在已选择的项中查找
    for (const item of normalizedModelValue.value) {
        if (typeof item === 'object' && item !== null && item.value === amenityValue) {
            return item.label || amenityValue;
        }
    }

    return amenityValue;
};

// 获取设施图标
const getAmenityIcon = (amenityValue: string) => {
    if (!amenityValue) return null;

    // 在所有分类中查找匹配的设施
    for (const category of amenitiesByCategory.value) {
        for (const amenity of category.amenities) {
            if (amenity.value === amenityValue) {
                return amenity.icon ? amenity.icon : null;
            }
        }
    }

    // 如果在当前分类中找不到，则在已选择的项中查找
    for (const item of normalizedModelValue.value) {
        if (typeof item === 'object' && item !== null && item.value === amenityValue && item.icon) {
            return item.icon;
        }
    }

    return null;
};

// 清空所有设施
const clearAll = () => {
    emit('update:modelValue', []);
    ElMessage.success('已清空所有设施');
}

// 一键添加设施
const handleQuickAddAmenities = (command: string) => {
    try {
        let newAmenities: Amenity[] = [];

        if (command === 'all') {
            // 添加所有设施
            amenitiesByCategory.value.forEach(category => {
                category.amenities.forEach((item: Amenity) => {
                    if (!isSelected(item)) {
                        newAmenities.push(item);
                    }
                });
            });
        } else {
            // 添加特定分类的设施
            const category = amenitiesByCategory.value.find(cat => cat.code === command);
            if (category) {
                category.amenities.forEach((item: Amenity) => {
                    if (!isSelected(item)) {
                        newAmenities.push(item);
                    }
                });
            }
        }

        if (newAmenities.length > 0) {
            emit('update:modelValue', [...normalizedModelValue.value, ...newAmenities]);
            ElMessage.success(`已添加${newAmenities.length}个设施`);
        } else {
            ElMessage.info('没有新的设施可添加');
        }
    } catch (error) {
        console.error('快速添加设施失败:', error);
    }
}

// 获取所有设施数据
const fetchData = async () => {
    try {
        const response = await getAmenitiesByCategoryApi();

        if (response?.data?.success) {
            amenitiesByCategory.value = response.data.data || [];

            // 如果存在分类，设置默认选中第一个分类
            if (amenitiesByCategory.value.length > 0) {
                activeCategory.value = amenitiesByCategory.value[0].code;
            }

            // 创建设施映射表，用于快速查找
            amenitiesByCategory.value.forEach((category: AmenityCategory) => {
                category.amenities.forEach((amenity: Amenity) => {
                    amenitiesMap.value.set(amenity.value, amenity);
                });
            });
        } else {
            console.error('获取设施数据失败:', response?.data?.message);
        }
    } catch (error) {
        console.error('获取设施数据出错:', error);
    }
}

// 监听modelValue变化，打印调试信息
watch(() => props.modelValue, (newVal) => {
    console.log('接收到的modelValue:', newVal);
    console.log('规范化后的modelValue:', normalizedModelValue.value);
}, { deep: true, immediate: true });

onMounted(fetchData);
</script>

<style scoped>
.amenity-selector {
    width: 100%;
}

/* 已选设施摘要样式 */
.selected-summary {
    background-color: #f8f9fa;
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 20px;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
    border-left: 4px solid #409eff;
}

.summary-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
}

.summary-title {
    font-weight: 600;
    font-size: 15px;
    color: #303133;
}

.selected-pills {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
}

.selected-pill {
    display: flex;
    align-items: center;
    background-color: #ecf5ff;
    border: 1px solid #d9ecff;
    border-radius: 20px;
    padding: 6px 12px;
    font-size: 13px;
    color: #409eff;
    transition: all 0.2s;
}

.selected-pill:hover {
    background-color: #d9ecff;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.pill-icon {
    margin-right: 6px;
    display: flex;
    align-items: center;
}

.pill-delete {
    margin-left: 6px;
    cursor: pointer;
    color: #909399;
}

.pill-delete:hover {
    color: #f56c6c;
}

/* 改进标签页样式 */
.amenity-tabs :deep(.el-tabs__nav) {
    border-radius: 4px;
    background-color: #f5f7fa;
    padding: 4px;
}

.amenity-tabs :deep(.el-tabs__item) {
    padding: 0 16px;
    height: 36px;
    line-height: 36px;
    transition: all 0.3s;
    border-radius: 4px;
}

.amenity-tabs :deep(.el-tabs__item.is-active) {
    background-color: #fff;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 设施卡片容器 */
.amenity-cards-container {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    margin-top: 16px;
}

/* 设施卡片样式 */
.amenity-card {
    display: flex;
    align-items: center;
    gap: 12px;
    border: 1px solid #ebeef5;
    border-radius: 8px;
    padding: 14px 16px;
    min-width: 180px;
    width: calc(33.33% - 11px);
    box-sizing: border-box;
    cursor: pointer;
    transition: all 0.3s;
    position: relative;
    background-color: #fff;
}

.amenity-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.amenity-card.selected {
    border-color: #409eff;
    background-color: #ecf5ff;
}

.amenity-icon {
    width: 42px;
    height: 42px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    background-color: #f0f5ff;
    color: #409eff;
    font-size: 20px;
    transition: all 0.3s;
}

.amenity-card.selected .amenity-icon {
    background-color: #409eff;
    color: white;
}

.fallback-icon {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #909399;
    font-size: 20px;
}

.amenity-info {
    flex: 1;
}

.amenity-name {
    font-weight: 600;
    margin-bottom: 4px;
    color: #303133;
}

.amenity-desc {
    font-size: 12px;
    color: #909399;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.amenity-check {
    position: absolute;
    top: 8px;
    right: 8px;
    background-color: #409eff;
    color: white;
    border-radius: 50%;
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.empty-warning {
    margin-top: 20px;
    padding: 16px;
    border-radius: 8px;
    background-color: #f8f9fa;
}

.amenity-actions {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 8px;
}

.amenity-global-actions {
    display: flex;
    justify-content: center;
    margin-top: 24px;
}

/* 响应式调整 */
@media (max-width: 768px) {
    .amenity-card {
        width: calc(50% - 8px);
    }

    .hidden-xs-only {
        display: none;
    }
}

@media (max-width: 576px) {
    .amenity-card {
        width: 100%;
    }

    .selected-pill {
        width: calc(50% - 5px);
        box-sizing: border-box;
    }
}
</style>
