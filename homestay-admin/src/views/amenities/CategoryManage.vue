<template>
    <div class="category-manage-container">
        <!-- 顶部卡片 - 操作区 -->
        <el-card shadow="hover" class="mb20">
            <template #header>
                <div class="card-header">
                    <div class="header-title">设施分类管理</div>
                    <div class="header-actions">
                        <el-input placeholder="搜索分类名称或编码" v-model="query.keyword" clearable @keyup.enter="handleSearch"
                            style="width: 200px; margin-right: 10px;">
                            <template #append>
                                <el-button @click="handleSearch">
                                    <el-icon>
                                        <Search />
                                    </el-icon>
                                </el-button>
                            </template>
                        </el-input>
                        <el-button type="primary" @click="handleAdd">
                            <el-icon>
                                <Plus />
                            </el-icon>添加分类
                        </el-button>
                        <el-button type="danger" @click="handleBatchDelete" :disabled="selectedRows.length === 0">
                            <el-icon>
                                <Delete />
                            </el-icon>批量删除
                        </el-button>
                        <el-tooltip content="刷新数据" placement="top">
                            <el-button circle @click="getCategories">
                                <el-icon>
                                    <Refresh />
                                </el-icon>
                            </el-button>
                        </el-tooltip>
                    </div>
                </div>
            </template>

            <div class="filter-section">
                <el-row :gutter="20">
                    <el-col :sm="24" :md="16" :lg="18" class="stats-col">
                        <el-tag v-if="pageTotal > 0" type="info">共 {{ pageTotal }} 个分类</el-tag>
                        <div class="help-text" v-if="pageTotal === 0">
                            <el-icon>
                                <InfoFilled />
                            </el-icon>
                            <span>暂无分类数据，点击"新增分类"按钮创建</span>
                        </div>
                    </el-col>
                </el-row>
            </div>
        </el-card>

        <!-- 表格区域 -->
        <el-card shadow="hover">
            <el-table v-loading="loading" :data="tableData" border stripe highlight-current-row class="category-table"
                empty-text="暂无设施分类数据，请添加" :header-cell-style="{ background: '#f5f7fa', color: '#606266' }"
                row-key="code" @selection-change="handleSelectionChange">
                <el-table-column type="selection" width="55" align="center" />
                <el-table-column prop="code" label="分类编码" min-width="180" show-overflow-tooltip>
                    <template #default="scope">
                        <el-tag size="small">{{ scope.row.code }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="name" label="分类名称" min-width="150" show-overflow-tooltip></el-table-column>
                <el-table-column prop="icon" label="图标" min-width="100" align="center">
                    <template #default="scope">
                        <div class="icon-preview">
                            <el-icon v-if="scope.row.icon" size="20">
                                <component :is="scope.row.icon"></component>
                            </el-icon>
                            <span v-else class="no-icon">-</span>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="操作" min-width="180" align="center" fixed="right">
                    <template #default="scope">
                        <el-button-group>
                            <el-button type="primary" size="small" :icon="Edit" @click="handleEdit(scope.row)">
                                编辑
                            </el-button>
                            <el-button type="danger" size="small" :icon="Delete" @click="handleDelete(scope.row)">
                                删除
                            </el-button>
                        </el-button-group>
                    </template>
                </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div class="pagination-container">
                <el-pagination background layout="total, sizes, prev, pager, next, jumper"
                    :current-page="query.pageIndex" :page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]"
                    :total="pageTotal" @current-change="handlePageChange" @size-change="handleSizeChange">
                </el-pagination>
            </div>
        </el-card>

        <!-- 新增/编辑弹出框 -->
        <el-dialog :title="editMode ? '编辑设施分类' : '新增设施分类'" v-model="dialogVisible" width="500px" center
            destroy-on-close>
            <el-form ref="formRef" :model="form" label-width="100px" :rules="rules" status-icon>
                <el-form-item label="分类编码" prop="code">
                    <el-input v-model="form.code" placeholder="请输入分类编码（大写英文）" :disabled="editMode">
                        <template #append v-if="editMode">
                            <el-tooltip content="编码创建后不可修改" placement="top">
                                <el-icon>
                                    <Lock />
                                </el-icon>
                            </el-tooltip>
                        </template>
                    </el-input>
                    <div class="form-tip" v-if="!editMode">分类编码创建后不可修改，建议使用大写英文字母</div>
                </el-form-item>
                <el-form-item label="分类名称" prop="name">
                    <el-input v-model="form.name" placeholder="请输入分类名称"></el-input>
                </el-form-item>
                <el-form-item label="图标" prop="icon">
                    <el-row :gutter="15" style="width: 100%">
                        <el-col :span="18">
                            <el-input v-model="form.icon" placeholder="请输入或从下方选择图标" readonly
                                @click="showIconSelector = true">
                                <template #prepend>
                                    <el-icon v-if="form.icon">
                                        <component :is="form.icon"></component>
                                    </el-icon>
                                    <el-icon v-else>
                                        <PictureFilled />
                                    </el-icon>
                                </template>
                                <template #append>
                                    <el-button @click.stop="showIconSelector = true">选择图标</el-button>
                                </template>
                            </el-input>
                        </el-col>
                        <el-col :span="6" style="display: flex; align-items: center;">
                            <div v-if="form.icon" class="icon-preview-large">
                                <el-icon>
                                    <component :is="form.icon"></component>
                                </el-icon>
                            </div>
                            <div v-else class="icon-placeholder">
                                <el-icon>
                                    <PictureFilled />
                                </el-icon>
                            </div>
                        </el-col>
                    </el-row>
                    <div class="form-tip">点击"选择图标"按钮从图标库中选择，无需手动输入</div>
                </el-form-item>
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="dialogVisible = false">取 消</el-button>
                    <el-button type="primary" @click="saveCategory" :loading="loading">确 定</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 图标选择器弹出框 -->
        <el-dialog title="选择图标" v-model="showIconSelector" width="700px" append-to-body destroy-on-close>
            <div class="icon-selector-container">
                <div class="icon-search">
                    <el-input v-model="iconSearch" placeholder="搜索图标" clearable @input="filterIcons">
                        <template #prefix>
                            <el-icon>
                                <Search />
                            </el-icon>
                        </template>
                    </el-input>
                </div>
                <div class="icon-categories">
                    <el-radio-group v-model="iconCategory" @change="filterIcons" size="small">
                        <el-radio-button label="all">全部图标</el-radio-button>
                        <el-radio-button label="common">常用图标</el-radio-button>
                        <el-radio-button label="direction">方向图标</el-radio-button>
                        <el-radio-button label="object">对象图标</el-radio-button>
                    </el-radio-group>
                </div>
                <el-scrollbar height="350px">
                    <div class="icon-grid">
                        <div v-for="icon in filteredIcons" :key="icon" class="icon-item"
                            :class="{ 'is-selected': form.icon === icon }" @click="selectIcon(icon)">
                            <el-icon>
                                <component :is="icon"></component>
                            </el-icon>
                            <span class="icon-name">{{ icon }}</span>
                        </div>
                        <div v-if="filteredIcons.length === 0" class="no-icons">
                            没有找到匹配的图标
                        </div>
                    </div>
                </el-scrollbar>
            </div>
            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="showIconSelector = false">取消</el-button>
                    <el-button type="primary" @click="confirmIconSelection">确认选择</el-button>
                    <el-button @click="form.icon = ''" type="warning">清除图标</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Delete, Edit, Search, Plus, Refresh, Lock, InfoFilled, PictureFilled } from '@element-plus/icons-vue';
import { getCategoriesApi, createCategoryApi, updateCategoryApi, deleteCategoryApi, batchDeleteCategoriesApi } from '@/api/amenities';

// 数据类型定义
interface Category {
    code: string;
    name: string;
    icon?: string;
    sortOrder?: number; // 保留字段但不使用
}

// 表格数据
const tableData = ref<Category[]>([]);
// 查询参数
const query = reactive({
    keyword: '',
    pageIndex: 1,
    pageSize: 10
});
// 分页总数
const pageTotal = ref(0);
// 弹出框显示状态
const dialogVisible = ref(false);
// 是否是编辑模式
const editMode = ref(false);
// 表单对象
const form = reactive<Category>({
    code: '',
    name: '',
    icon: ''
    // 移除 sortOrder 字段
});
// 加载状态
const loading = ref(false);
// 表单验证规则
const rules = {
    code: [
        { required: true, message: '请输入分类编码', trigger: 'blur' },
        { pattern: /^[A-Z0-9_]+$/, message: '编码只能包含大写字母、数字和下划线', trigger: 'blur' }
    ],
    name: [
        { required: true, message: '请输入分类名称', trigger: 'blur' },
        { min: 2, max: 20, message: '分类名称长度应在2-20个字符之间', trigger: 'blur' }
    ]
};

// 添加formRef引用
const formRef = ref();

// 图标选择器相关状态
const showIconSelector = ref(false);
const iconSearch = ref('');
const iconCategory = ref('common');
const selectedIcon = ref('');

// 图标列表 - 常用图标
const commonIcons = [
    'House', 'Setting', 'User', 'Shopping', 'Menu', 'Location',
    'Document', 'Tickets', 'OfficeBuilding', 'Check', 'HomeFilled',
    'Briefcase', 'Connection', 'Monitor', 'KitchenKnife'
];

// 方向类图标
const directionIcons = [
    'Top', 'TopRight', 'Right', 'Bottom', 'Back'
];

// 对象类图标
const objectIcons = [
    'Umbrella', 'WaterCup', 'Food', 'Mug', 'Box', 'Medal',
    'Van', 'Camera', 'Trophy', 'Calendar', 'Star', 'Bell'
];

// 所有图标
const allIcons = [...commonIcons, ...directionIcons, ...objectIcons];

// 过滤后的图标
const filteredIcons = computed(() => {
    let icons = [];

    // 根据分类过滤
    switch (iconCategory.value) {
        case 'common':
            icons = commonIcons;
            break;
        case 'direction':
            icons = directionIcons;
            break;
        case 'object':
            icons = objectIcons;
            break;
        default:
            icons = allIcons;
    }

    // 根据搜索词过滤
    if (iconSearch.value) {
        const keyword = iconSearch.value.toLowerCase();
        icons = icons.filter(icon => icon.toLowerCase().includes(keyword));
    }

    return icons;
});

// 过滤图标的方法
const filterIcons = () => {
    // 已通过计算属性实现
};

// 选择图标
const selectIcon = (icon: string) => {
    selectedIcon.value = icon;
    form.icon = icon;
};

// 确认图标选择
const confirmIconSelection = () => {
    form.icon = selectedIcon.value || form.icon;
    showIconSelector.value = false;
};

// 页面加载时获取数据
onMounted(() => {
    getCategories();
});

// 获取设施分类数据
const getCategories = () => {
    loading.value = true;
    getCategoriesApi().then(response => {
        if (response && response.success) {
            let data = response.data || [];
            console.log('获取到的分类数据:', data);

            // 如果有搜索关键字，过滤结果
            if (query.keyword) {
                const keyword = query.keyword.toLowerCase();
                data = data.filter((item: Category) =>
                    item.name.toLowerCase().includes(keyword) ||
                    item.code.toLowerCase().includes(keyword)
                );
            }

            // 分页处理
            const startIndex = (query.pageIndex - 1) * query.pageSize;
            const endIndex = startIndex + query.pageSize;
            tableData.value = data.slice(startIndex, endIndex);
            pageTotal.value = data.length;
        } else {
            ElMessage.error(response?.message || '获取分类数据失败');
            tableData.value = [];
            pageTotal.value = 0;
        }
    }).catch(error => {
        console.error('获取分类数据出错:', error);
        ElMessage.error(`获取分类数据出错: ${error.message || '网络错误'}`);
        tableData.value = [];
        pageTotal.value = 0;
    }).finally(() => {
        loading.value = false;
    });
};

// 搜索操作
const handleSearch = () => {
    query.pageIndex = 1;
    getCategories();
};

// 添加操作
const handleAdd = () => {
    editMode.value = false;
    form.code = '';
    form.name = '';
    form.icon = 'Setting'; // 设置默认图标
    dialogVisible.value = true;
};

// 编辑操作
const handleEdit = (row: Category) => {
    editMode.value = true;
    form.code = row.code;
    form.name = row.name;
    form.icon = row.icon || '';
    dialogVisible.value = true;
};

// 删除操作
const handleDelete = (row: Category) => {
    ElMessageBox.confirm('确定要删除该分类吗？删除分类将会影响到关联的设施。', '提示', {
        type: 'warning'
    }).then(() => {
        loading.value = true;
        deleteCategoryApi(row.code).then(response => {
            if (response && response.success) {
                ElMessage.success(response.message || '删除成功');
                getCategories();
            } else {
                ElMessage.error(response?.message || '删除失败');
            }
        }).catch(error => {
            console.error('删除分类出错:', error);
            if (error.response && error.response.data && error.response.data.message) {
                ElMessage.error(error.response.data.message);
                // 判断是否是被使用中的分类
                if (error.response.data.message.includes('使用')) {
                    ElMessageBox.alert(
                        '该分类下还有设施正在使用中，无法删除。您可以：<br>' +
                        '1. 先将该分类下的设施移动到其他分类<br>' +
                        '2. 或删除该分类下的所有设施',
                        '操作提示',
                        { dangerouslyUseHTMLString: true, type: 'warning' }
                    );
                }
            } else {
                ElMessage.error(`删除分类出错: ${error.message || '未知错误'}`);
            }
        }).finally(() => {
            loading.value = false;
        });
    }).catch(() => { });
};

// 保存分类
const saveCategory = () => {
    if (!formRef.value) return;

    formRef.value.validate((valid: boolean) => {
        if (valid) {
            loading.value = true;
            const savePromise = editMode.value
                ? updateCategoryApi(form.code, form)
                : createCategoryApi(form);

            savePromise.then(response => {
                if (response && response.success) {
                    ElMessage.success(response.message || `${editMode.value ? '更新' : '添加'}成功`);
                    dialogVisible.value = false;
                    getCategories();
                } else {
                    ElMessage.error(response?.message || `${editMode.value ? '更新' : '添加'}失败`);
                }
            }).catch(error => {
                console.error(`${editMode.value ? '更新' : '添加'}分类出错:`, error);
                if (error.response && error.response.data && error.response.data.message) {
                    ElMessage.error(error.response.data.message);
                } else {
                    ElMessage.error(`${editMode.value ? '更新' : '添加'}分类出错: ${error.message || '未知错误'}`);
                }
            }).finally(() => {
                loading.value = false;
            });
        } else {
            ElMessage.warning('请正确填写表单信息');
        }
    });
};

// 分页导航
const handlePageChange = (val: number) => {
    query.pageIndex = val;
    getCategories();
};

// 每页显示数量变化处理
const handleSizeChange = (val: number) => {
    query.pageSize = val;
    query.pageIndex = 1; // 重置到第一页
    getCategories();
};

// 选中的行数据
const selectedRows = ref<Category[]>([]);

// 处理表格选择变化
const handleSelectionChange = (selection: Category[]) => {
    selectedRows.value = selection;
};

// 批量删除操作
const handleBatchDelete = () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请先选择要删除的分类');
        return;
    }

    ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 个分类吗？删除分类将会影响到关联的设施。`, '批量删除提示', {
        type: 'warning'
    }).then(() => {
        loading.value = true;
        // 获取所有选中的分类code
        const codes = selectedRows.value.map(item => item.code);

        batchDeleteCategoriesApi(codes).then((response: any) => {
            if (response && response.success) {
                ElMessage.success(response.message || '批量删除成功');
                getCategories();
            } else {
                ElMessage.error(response?.message || '批量删除失败');
            }
        }).catch((error: any) => {
            console.error('批量删除分类出错:', error);
            if (error.response && error.response.data && error.response.data.message) {
                ElMessage.error(error.response.data.message);
                // 判断是否是被使用中的分类
                if (error.response.data.message.includes('使用')) {
                    ElMessageBox.alert(
                        '选中的分类中有分类下还有设施正在使用中，无法删除。您可以：<br>' +
                        '1. 先将该分类下的设施移动到其他分类<br>' +
                        '2. 或删除该分类下的所有设施',
                        '操作提示',
                        { dangerouslyUseHTMLString: true, type: 'warning' }
                    );
                }
            } else {
                ElMessage.error(`批量删除分类出错: ${error.message || '未知错误'}`);
            }
        }).finally(() => {
            loading.value = false;
        });
    }).catch(() => { });
};
</script>

<style scoped>
.category-manage-container {
    padding: 0 10px;
}

.mb20 {
    margin-bottom: 20px;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.header-title {
    font-size: 18px;
    font-weight: bold;
    color: #303133;
}

.header-actions {
    display: flex;
    gap: 10px;
    align-items: center;
}

.filter-section {
    margin-bottom: 10px;
}

.stats-col {
    display: flex;
    align-items: center;
    justify-content: flex-end;
}

.help-text {
    color: #909399;
    display: flex;
    align-items: center;
    gap: 5px;
}

.category-table {
    width: 100%;
    margin-bottom: 20px;
}

.icon-preview {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 40px;
    color: var(--el-color-primary);
}

.icon-preview-large {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100%;
    font-size: 30px;
    color: var(--el-color-primary);
    border: 1px dashed #d9d9d9;
    border-radius: 4px;
    padding: 10px 0;
}

.icon-placeholder {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100%;
    font-size: 24px;
    color: #909399;
    border: 1px dashed #d9d9d9;
    border-radius: 4px;
    padding: 10px 0;
}

.no-icon {
    color: #909399;
}

.pagination-container {
    margin: 20px 0 10px;
    display: flex;
    justify-content: flex-end;
}

.dialog-footer {
    text-align: right;
    padding-top: 10px;
}

.form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 5px;
    line-height: 1.5;
}

.icon-link {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    margin-top: 10px;
    font-size: 13px;
}

@media screen and (max-width: 768px) {
    .stats-col {
        margin-top: 10px;
        justify-content: flex-start;
    }

    .header-actions {
        flex-wrap: wrap;
    }

    .filter-section {
        margin-bottom: 15px;
    }

    .pagination-container {
        overflow-x: auto;
    }
}

/* 图标选择器样式 */
.icon-selector-container {
    padding: 10px;
}

.icon-search {
    margin-bottom: 15px;
}

.icon-categories {
    margin-bottom: 15px;
}

.icon-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
    gap: 10px;
    padding: 10px 0;
}

.icon-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 10px;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.3s;
}

.icon-item:hover {
    background-color: #f5f7fa;
    transform: translateY(-2px);
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.icon-item.is-selected {
    background-color: #ecf5ff;
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
}

.icon-item .el-icon {
    font-size: 24px;
    margin-bottom: 5px;
}

.icon-name {
    font-size: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    width: 100%;
    text-align: center;
}

.no-icons {
    grid-column: 1 / -1;
    text-align: center;
    padding: 20px;
    color: #909399;
}

/* 移除排序相关样式 */
.sort-controls,
.sort-buttons {
    /* 这些样式可以删除 */
}
</style>