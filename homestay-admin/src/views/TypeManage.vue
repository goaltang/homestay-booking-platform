<template>
    <div class="type-manage-container">
        <el-card class="filter-card">
            <template #header>
                <div class="card-header">
                    <span>房源类型管理</span>
                    <div class="card-actions">
                        <el-button type="success" @click="batchAddTestData" :loading="batchLoading">一键添加测试数据</el-button>
                        <el-button type="primary" @click="openTypeForm()">添加新类型</el-button>
                    </div>
                </div>
            </template>

            <el-tabs v-model="activeTab">
                <el-tab-pane label="类型管理" name="types">
                    <el-table :data="types" border style="width: 100%" v-loading="loading">
                        <el-table-column type="expand">
                            <template #default="props">
                                <div class="type-detail">
                                    <div class="detail-item">
                                        <span class="label">描述：</span>
                                        <span>{{ props.row.description || '无' }}</span>
                                    </div>
                                    <div class="detail-item">
                                        <span class="label">创建时间：</span>
                                        <span>{{ formatDateTime(props.row.createdAt) }}</span>
                                    </div>
                                    <div class="detail-item">
                                        <span class="label">更新时间：</span>
                                        <span>{{ formatDateTime(props.row.updatedAt) }}</span>
                                    </div>
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column prop="id" label="ID" width="70"></el-table-column>
                        <!-- 注释掉图标列 -->
                        <!-- 
                        <el-table-column label="图标" width="80">
                            <template #default="scope">
                                <el-image v-if="scope.row.icon" :src="scope.row.icon" fit="cover"
                                    style="width: 50px; height: 50px;">
                                    <template #error>
                                        <div class="image-placeholder">
                                            <i class="el-icon-picture"></i>
                                        </div>
                                    </template>
                                </el-image>
                                <div v-else class="image-placeholder">
                                    <i class="el-icon-picture"></i>
                                </div>
                            </template>
                        </el-table-column>
                        -->
                        <el-table-column prop="name" label="名称"></el-table-column>
                        <el-table-column prop="code" label="代码"></el-table-column>
                        <el-table-column prop="categoryName" label="分类"></el-table-column>
                        <el-table-column prop="sortOrder" label="排序" width="80"></el-table-column>
                        <el-table-column label="状态" width="100">
                            <template #default="scope">
                                <el-switch v-model="scope.row.active" @change="updateTypeStatus(scope.row)"
                                    active-color="#13ce66" inactive-color="#ff4949"></el-switch>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" width="150">
                            <template #default="scope">
                                <el-button size="small" type="primary" @click="openTypeForm(scope.row)">编辑</el-button>
                                <el-button size="small" type="danger" @click="deleteType(scope.row)">删除</el-button>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-tab-pane>

                <el-tab-pane label="分类管理" name="categories">
                    <el-table :data="categories" border style="width: 100%" v-loading="loadingCategories">
                        <el-table-column type="expand">
                            <template #default="props">
                                <div class="category-types">
                                    <div class="detail-title">该分类下的房源类型：</div>
                                    <el-tag v-for="type in getCategoryTypes(props.row.id)" :key="type.id"
                                        style="margin-right: 5px; margin-bottom: 5px;">
                                        {{ type.name }}
                                    </el-tag>
                                    <div v-if="getCategoryTypes(props.row.id).length === 0">该分类下暂无房源类型</div>
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column prop="id" label="ID" width="70"></el-table-column>
                        <el-table-column prop="name" label="名称" width="150"></el-table-column>
                        <el-table-column prop="description" label="描述"></el-table-column>
                        <el-table-column prop="sortOrder" label="排序" width="80"></el-table-column>
                        <el-table-column label="操作" width="150">
                            <template #default="scope">
                                <el-button size="small" type="primary"
                                    @click="openCategoryForm(scope.row)">编辑</el-button>
                                <el-button size="small" type="danger" @click="deleteCategory(scope.row)">删除</el-button>
                            </template>
                        </el-table-column>
                    </el-table>

                    <div class="add-category-btn">
                        <el-button type="primary" @click="openCategoryForm()">添加新分类</el-button>
                    </div>
                </el-tab-pane>
            </el-tabs>
        </el-card>

        <!-- 类型表单对话框 -->
        <el-dialog :title="typeForm.id ? '编辑房源类型' : '添加房源类型'" v-model="typeDialogVisible" width="500px">
            <el-form :model="typeForm" :rules="typeRules" ref="typeFormRef" label-width="100px">
                <el-form-item label="类型名称" prop="name">
                    <el-input v-model="typeForm.name" placeholder="请输入类型名称"></el-input>
                </el-form-item>

                <el-form-item label="类型代码" prop="code">
                    <el-input v-model="typeForm.code" placeholder="请输入类型代码（英文大写）"></el-input>
                </el-form-item>

                <el-form-item label="所属分类" prop="categoryId">
                    <el-select v-model="typeForm.categoryId" placeholder="请选择分类" style="width: 100%">
                        <el-option v-for="category in categories" :key="category.id" :label="category.name"
                            :value="category.id"></el-option>
                    </el-select>
                </el-form-item>

                <el-form-item label="类型描述">
                    <el-input type="textarea" v-model="typeForm.description" placeholder="请输入类型描述" :rows="3"></el-input>
                </el-form-item>

                <!-- 注释掉类型图标的表单项 -->
                <!-- 
                <el-form-item label="类型图标">
                    <div class="icon-upload">
                        <el-image v-if="typeForm.icon" :src="typeForm.icon" fit="cover" class="icon-preview"></el-image>
                        <div v-else class="icon-placeholder">
                            <i class="el-icon-picture"></i>
                        </div>
                        <el-upload class="upload-btn" :action="UPLOAD_URL" :headers="uploadHeaders"
                            :show-file-list="false" :on-success="handleIconSuccess" :before-upload="beforeIconUpload">
                            <el-button size="small" type="primary">上传图标</el-button>
                        </el-upload>
                    </div>
                </el-form-item>
                -->

                <el-form-item label="排序顺序" prop="sortOrder">
                    <el-input-number v-model="typeForm.sortOrder" :min="1" :max="999"></el-input-number>
                </el-form-item>

                <el-form-item label="是否激活">
                    <el-switch v-model="typeForm.active"></el-switch>
                </el-form-item>
            </el-form>

            <template #footer>
                <el-button @click="typeDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitTypeForm" :loading="submitting">保存</el-button>
            </template>
        </el-dialog>

        <!-- 分类表单对话框 -->
        <el-dialog :title="categoryForm.id ? '编辑分类' : '添加分类'" v-model="categoryDialogVisible" width="500px">
            <el-form :model="categoryForm" :rules="categoryRules" ref="categoryFormRef" label-width="100px">
                <el-form-item label="分类名称" prop="name">
                    <el-input v-model="categoryForm.name" placeholder="请输入分类名称"></el-input>
                </el-form-item>

                <el-form-item label="分类描述">
                    <el-input type="textarea" v-model="categoryForm.description" placeholder="请输入分类描述"
                        :rows="3"></el-input>
                </el-form-item>

                <el-form-item label="排序顺序" prop="sortOrder">
                    <el-input-number v-model="categoryForm.sortOrder" :min="1" :max="999"></el-input-number>
                </el-form-item>
            </el-form>

            <template #footer>
                <el-button @click="categoryDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitCategoryForm" :loading="submitting">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { format } from 'date-fns'
import {
    getAllTypesApi,
    getAllCategoriesApi,
    createTypeApi,
    updateTypeApi,
    deleteTypeApi,
    createCategoryApi,
    updateCategoryApi,
    deleteCategoryApi
} from '@/api/type'

// 数据
const types = ref<any[]>([])
const categories = ref<any[]>([])
const loading = ref(false)
const loadingCategories = ref(false)
const submitting = ref(false)
const batchLoading = ref(false)
const activeTab = ref('types')

// 表单相关
const typeDialogVisible = ref(false)
const categoryDialogVisible = ref(false)
const typeFormRef = ref<FormInstance>()
const categoryFormRef = ref<FormInstance>()

const typeForm = reactive({
    id: null as number | null,
    name: '',
    code: '',
    description: '',
    icon: '',
    active: true,
    sortOrder: 100,
    categoryId: null as number | null,
    categoryName: ''
})

const categoryForm = reactive({
    id: null as number | null,
    name: '',
    description: '',
    sortOrder: 100
})

// 表单验证规则
const typeRules = {
    name: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
    code: [{ required: true, message: '请输入类型代码', trigger: 'blur' }],
    categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }]
}

const categoryRules = {
    name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

// 根据分类ID获取分类下的房源类型
const getCategoryTypes = (categoryId: number) => {
    return types.value.filter(type => type.categoryId === categoryId)
}

// 格式化日期时间
const formatDateTime = (dateStr: string | null) => {
    if (!dateStr) return '未知'
    try {
        return format(new Date(dateStr), 'yyyy-MM-dd HH:mm:ss')
    } catch {
        return dateStr
    }
}

// 打开类型表单
const openTypeForm = (row?: any) => {
    resetTypeForm()

    if (row) {
        typeForm.id = row.id
        typeForm.name = row.name
        typeForm.code = row.code
        typeForm.description = row.description || ''
        typeForm.icon = row.icon || ''
        typeForm.active = row.active
        typeForm.sortOrder = row.sortOrder
        typeForm.categoryId = row.categoryId
        typeForm.categoryName = row.categoryName
    }

    typeDialogVisible.value = true
}

// 打开分类表单
const openCategoryForm = (row?: any) => {
    resetCategoryForm()

    if (row) {
        categoryForm.id = row.id
        categoryForm.name = row.name
        categoryForm.description = row.description || ''
        categoryForm.sortOrder = row.sortOrder
    }

    categoryDialogVisible.value = true
}

// 重置类型表单
const resetTypeForm = () => {
    typeForm.id = null
    typeForm.name = ''
    typeForm.code = ''
    typeForm.description = ''
    typeForm.icon = ''
    typeForm.active = true
    typeForm.sortOrder = 100
    typeForm.categoryId = null
    typeForm.categoryName = ''

    if (typeFormRef.value) {
        typeFormRef.value.resetFields()
    }
}

// 重置分类表单
const resetCategoryForm = () => {
    categoryForm.id = null
    categoryForm.name = ''
    categoryForm.description = ''
    categoryForm.sortOrder = 100

    if (categoryFormRef.value) {
        categoryFormRef.value.resetFields()
    }
}

// 提交类型表单
const submitTypeForm = async () => {
    if (!typeFormRef.value) return

    await typeFormRef.value.validate(async (valid) => {
        if (!valid) return

        submitting.value = true

        try {
            let response;

            if (typeForm.id) {
                response = await updateTypeApi(typeForm.id, typeForm);
            } else {
                response = await createTypeApi(typeForm);
            }

            if (response.data.success) {
                ElMessage.success(typeForm.id ? '更新成功' : '创建成功')
                typeDialogVisible.value = false
                fetchTypes()
            } else {
                ElMessage.error(response.data.message || '操作失败')
            }
        } catch (error: any) {
            console.error('提交表单失败:', error)
            ElMessage.error(error.response?.data?.message || '操作失败')
        } finally {
            submitting.value = false
        }
    })
}

// 提交分类表单
const submitCategoryForm = async () => {
    if (!categoryFormRef.value) return

    await categoryFormRef.value.validate(async (valid) => {
        if (!valid) return

        submitting.value = true

        try {
            let response;

            if (categoryForm.id) {
                response = await updateCategoryApi(categoryForm.id, categoryForm);
            } else {
                response = await createCategoryApi(categoryForm);
            }

            if (response.data.success) {
                ElMessage.success(categoryForm.id ? '更新成功' : '创建成功')
                categoryDialogVisible.value = false
                fetchCategories()
            } else {
                ElMessage.error(response.data.message || '操作失败')
            }
        } catch (error: any) {
            console.error('提交表单失败:', error)
            ElMessage.error(error.response?.data?.message || '操作失败')
        } finally {
            submitting.value = false
        }
    })
}

// 更新类型状态
const updateTypeStatus = async (row: any) => {
    try {
        const response = await updateTypeApi(row.id, {
            ...row,
            active: row.active
        });

        if (response.data.success) {
            ElMessage.success(`已${row.active ? '激活' : '停用'}该房源类型`)
        } else {
            row.active = !row.active // 恢复状态
            ElMessage.error(response.data.message || '操作失败')
        }
    } catch (error: any) {
        row.active = !row.active // 恢复状态
        console.error('更新状态失败:', error)
        ElMessage.error(error.response?.data?.message || '操作失败')
    }
}

// 删除类型
const deleteType = (row: any) => {
    ElMessageBox.confirm(`确定要删除房源类型"${row.name}"吗？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            const response = await deleteTypeApi(row.id);

            if (response.data.success) {
                ElMessage.success('删除成功')
                fetchTypes()
            } else {
                ElMessage.error(response.data.message || '删除失败')
            }
        } catch (error: any) {
            console.error('删除失败:', error)
            ElMessage.error(error.response?.data?.message || '删除失败')
        }
    }).catch(() => { })
}

// 删除分类
const deleteCategory = (row: any) => {
    // 检查分类下是否有房源类型
    const typesInCategory = getCategoryTypes(row.id)

    if (typesInCategory.length > 0) {
        ElMessage.warning(`该分类下有${typesInCategory.length}个房源类型，请先删除或修改这些类型的分类`)
        return
    }

    ElMessageBox.confirm(`确定要删除分类"${row.name}"吗？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(async () => {
        try {
            const response = await deleteCategoryApi(row.id);

            if (response.data.success) {
                ElMessage.success('删除成功')
                fetchCategories()
            } else {
                ElMessage.error(response.data.message || '删除失败')
            }
        } catch (error: any) {
            console.error('删除失败:', error)
            ElMessage.error(error.response?.data?.message || '删除失败')
        }
    }).catch(() => { })
}

// 获取所有房源类型
const fetchTypes = async () => {
    loading.value = true

    try {
        const response = await getAllTypesApi();

        if (response.data.success) {
            types.value = response.data.data
        } else {
            ElMessage.error(response.data.message || '获取房源类型失败')
        }
    } catch (error: any) {
        console.error('获取房源类型失败:', error)
        ElMessage.error(error.response?.data?.message || '获取房源类型失败')
    } finally {
        loading.value = false
    }
}

// 获取所有分类
const fetchCategories = async () => {
    loadingCategories.value = true

    try {
        const response = await getAllCategoriesApi();

        if (response.data.success) {
            categories.value = response.data.data
        } else {
            ElMessage.error(response.data.message || '获取分类失败')
        }
    } catch (error: any) {
        console.error('获取分类失败:', error)
        ElMessage.error(error.response?.data?.message || '获取分类失败')
    } finally {
        loadingCategories.value = false
    }
}

// 初始化
onMounted(() => {
    fetchTypes()
    fetchCategories()
})

// 批量添加测试数据
const batchAddTestData = async () => {
    batchLoading.value = true

    try {
        // 先获取现有分类和类型，避免重复添加
        await Promise.all([fetchCategories(), fetchTypes()])

        // 测试分类数据
        const testCategories = [
            { name: '热门推荐', description: '最受欢迎的房源类型', sortOrder: 10 },
            { name: '特色住宿', description: '独特的住宿体验', sortOrder: 20 },
            { name: '度假别墅', description: '豪华度假别墅', sortOrder: 30 }
        ]

        // 依次添加分类
        const categoryPromises = testCategories.map(async (category) => {
            // 检查分类是否已存在
            const existingCategory = categories.value.find(c => c.name === category.name)
            if (existingCategory) {
                return existingCategory // 已存在则返回现有分类
            }

            // 不存在则创建新分类
            const response = await createCategoryApi(category)
            if (response.data.success) {
                return response.data.data
            }
            return null
        })

        // 等待所有分类创建完成
        const createdCategories = await Promise.all(categoryPromises)
        const validCategories = createdCategories.filter(c => c !== null)

        // 测试房源类型数据
        const testTypes = [
            // 热门推荐分类下的类型
            {
                name: '精品公寓',
                code: 'LUXURY_APT',
                description: '市中心精品装修公寓',
                icon: '/icons/luxury_apartment.png',
                active: true,
                sortOrder: 10,
                categoryId: validCategories[0]?.id
            },
            {
                name: '海景房',
                code: 'OCEAN_VIEW',
                description: '无敌海景房，适合度假',
                icon: '/icons/ocean_view.png',
                active: true,
                sortOrder: 20,
                categoryId: validCategories[0]?.id
            },

            // 特色住宿分类下的类型
            {
                name: '古风民宿',
                code: 'TRADITIONAL',
                description: '传统古风装修的特色民宿',
                icon: '/icons/traditional.png',
                active: true,
                sortOrder: 10,
                categoryId: validCategories[1]?.id
            },
            {
                name: '集装箱屋',
                code: 'CONTAINER',
                description: '创意集装箱改造住宿',
                icon: '/icons/container.png',
                active: true,
                sortOrder: 20,
                categoryId: validCategories[1]?.id
            },

            // 度假别墅分类下的类型
            {
                name: '泳池别墅',
                code: 'POOL_VILLA',
                description: '带私人泳池的豪华别墅',
                icon: '/icons/pool_villa.png',
                active: true,
                sortOrder: 10,
                categoryId: validCategories[2]?.id
            },
            {
                name: '花园洋房',
                code: 'GARDEN_HOUSE',
                description: '带私家花园的独栋别墅',
                icon: '/icons/garden_house.png',
                active: true,
                sortOrder: 20,
                categoryId: validCategories[2]?.id
            }
        ]

        // 依次添加房源类型
        const typePromises = testTypes.map(async (type) => {
            // 检查类型是否已存在
            const existingType = types.value.find(t => t.code === type.code)
            if (existingType) {
                return existingType // 已存在则返回现有类型
            }

            // 不存在则创建新类型
            if (type.categoryId) { // 确保分类ID存在
                const response = await createTypeApi(type)
                if (response.data.success) {
                    return response.data.data
                }
            }
            return null
        })

        // 等待所有类型创建完成
        await Promise.all(typePromises)

        // 刷新数据
        await Promise.all([fetchCategories(), fetchTypes()])

        ElMessage.success('成功批量添加测试数据')
    } catch (error: any) {
        console.error('批量添加失败:', error)
        ElMessage.error('批量添加测试数据失败，请查看控制台')
    } finally {
        batchLoading.value = false
    }
}
</script>

<style scoped>
.type-manage-container {
    padding: 20px;
}

.filter-card {
    margin-bottom: 20px;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.card-actions {
    display: flex;
    gap: 10px;
}

.type-detail {
    padding: 10px 20px;
}

.detail-item {
    margin-bottom: 8px;
    display: flex;
}

.detail-item .label {
    font-weight: bold;
    width: 100px;
}

.category-types {
    padding: 10px 20px;
}

.detail-title {
    font-weight: bold;
    margin-bottom: 10px;
}

.image-placeholder {
    width: 50px;
    height: 50px;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #f5f7fa;
    color: #909399;
    font-size: 20px;
}

.icon-upload {
    display: flex;
    align-items: center;
    gap: 15px;
}

.icon-preview {
    width: 60px;
    height: 60px;
    object-fit: cover;
    border-radius: 4px;
}

.icon-placeholder {
    width: 60px;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #f5f7fa;
    color: #909399;
    border-radius: 4px;
    font-size: 24px;
}

.add-category-btn {
    margin-top: 20px;
    display: flex;
    justify-content: center;
}
</style>