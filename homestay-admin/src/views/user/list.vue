<template>
    <div class="user-list">
        <!-- 统计卡片 -->
        <div class="stats-container">
            <el-row :gutter="20">
                <el-col :span="6">
                    <el-card class="stats-card">
                        <div class="stats-content">
                            <div class="stats-icon" style="background-color: #f0f9ff; color: #3b82f6;">
                                <el-icon size="24">
                                    <User />
                                </el-icon>
                            </div>
                            <div class="stats-info">
                                <div class="stats-title">总用户数</div>
                                <div class="stats-value">{{ totalUsers }}</div>
                            </div>
                        </div>
                    </el-card>
                </el-col>
                <el-col :span="6">
                    <el-card class="stats-card">
                        <div class="stats-content">
                            <div class="stats-icon" style="background-color: #f0fdf4; color: #22c55e;">
                                <el-icon size="24">
                                    <CircleCheck />
                                </el-icon>
                            </div>
                            <div class="stats-info">
                                <div class="stats-title">活跃用户</div>
                                <div class="stats-value">{{ activeUsers }}</div>
                            </div>
                        </div>
                    </el-card>
                </el-col>
                <el-col :span="6">
                    <el-card class="stats-card">
                        <div class="stats-content">
                            <div class="stats-icon" style="background-color: #fef3c7; color: #f59e0b;">
                                <el-icon size="24">
                                    <Warning />
                                </el-icon>
                            </div>
                            <div class="stats-info">
                                <div class="stats-title">待审核</div>
                                <div class="stats-value">{{ pendingUsers }}</div>
                            </div>
                        </div>
                    </el-card>
                </el-col>
                <el-col :span="6">
                    <el-card class="stats-card">
                        <div class="stats-content">
                            <div class="stats-icon" style="background-color: #fee2e2; color: #ef4444;">
                                <el-icon size="24">
                                    <CircleClose />
                                </el-icon>
                            </div>
                            <div class="stats-info">
                                <div class="stats-title">禁用用户</div>
                                <div class="stats-value">{{ disabledUsers }}</div>
                            </div>
                        </div>
                    </el-card>
                </el-col>
            </el-row>
        </div>

        <!-- 搜索筛选区域 -->
        <div class="search-box">
            <table-search :query="searchForm" @update:query="Object.assign(searchForm, $event)" :options="searchOptions" :search="handleSearch">
                <template #actions>
                    <el-button type="success" @click="handleAdd" :icon="Plus">新增用户</el-button>
                    <el-button type="info" @click="handleExport" :loading="exportLoading" :icon="Download">导出</el-button>
                </template>
            </table-search>
        </div>

        <!-- 批量操作区域 -->
        <div class="batch-operation" v-if="selectedRows.length > 0">
            <el-alert title="批量操作" type="info" :closable="false" show-icon>
                <template #default>
                    已选择 <strong>{{ selectedRows.length }}</strong> 项
                    <div class="batch-buttons">
                        <el-button size="small" type="success" @click="handleBatchEnable">
                            <el-icon>
                                <CircleCheck />
                            </el-icon>
                            批量启用
                        </el-button>
                        <el-button size="small" type="danger" @click="handleBatchDisable">
                            <el-icon>
                                <CircleClose />
                            </el-icon>
                            批量禁用
                        </el-button>
                        <el-button size="small" type="warning" @click="handleBatchResetPassword">
                            <el-icon>
                                <Key />
                            </el-icon>
                            批量重置密码
                        </el-button>
                        <el-button size="small" type="info" @click="handleBatchDelete">
                            <el-icon>
                                <Delete />
                            </el-icon>
                            批量删除
                        </el-button>
                    </div>
                </template>
            </el-alert>
        </div>

        <!-- 用户列表 -->
        <el-table :data="tableData" border style="width: 100%" v-loading="loading"
            @selection-change="handleSelectionChange" @sort-change="handleSortChange"
            :header-cell-style="{ background: '#f5f7fa', color: '#606266' }">
            <el-table-column type="selection" width="55" />
            <el-table-column prop="id" label="ID" width="80" sortable="custom" />

            <!-- 头像和基本信息 -->
            <el-table-column label="用户信息" width="200">
                <template #default="scope">
                    <div class="user-info">
                        <el-avatar :size="40" :src="scope.row.avatar || ''" :icon="User" class="user-avatar" />
                        <div class="user-details">
                            <div class="username">{{ scope.row.username }}</div>
                            <div class="nickname">{{ scope.row.nickname || '-' }}</div>
                        </div>
                    </div>
                </template>
            </el-table-column>

            <el-table-column prop="phone" label="手机号" width="120" />
            <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />

            <!-- 用户类型 -->
            <el-table-column prop="userType" label="用户类型" width="100">
                <template #default="scope">
                    <el-tag :type="getUserTypeTagType(scope.row.userType)" size="small">
                        {{ getUserTypeText(scope.row.userType) }}
                    </el-tag>
                </template>
            </el-table-column>

            <!-- 身份验证状态 -->
            <el-table-column prop="verificationStatus" label="身份验证" width="100">
                <template #default="scope">
                    <el-tag :type="getVerificationTagType(scope.row.verificationStatus)" size="small">
                        {{ getVerificationText(scope.row.verificationStatus) }}
                    </el-tag>
                </template>
            </el-table-column>

            <!-- 账户状态 -->
            <el-table-column prop="status" label="账户状态" width="100">
                <template #default="scope">
                    <el-tag :type="scope.row.status === '1' ? 'success' : 'info'" size="small">
                        {{ scope.row.status === '1' ? '正常' : '禁用' }}
                    </el-tag>
                </template>
            </el-table-column>

            <el-table-column prop="createTime" label="注册时间" width="180" sortable="custom" />

            <!-- 最后登录时间 -->
            <el-table-column prop="lastLoginTime" label="最后登录" width="180" sortable="custom">
                <template #default="scope">
                    <span v-if="scope.row.lastLoginTime">
                        {{ formatDateTime(scope.row.lastLoginTime) }}
                    </span>
                    <span v-else style="color: #909399;">从未登录</span>
                </template>
            </el-table-column>

            <!-- 操作列 -->
            <el-table-column label="操作" width="280" fixed="right">
                <template #default="scope">
                    <el-button type="primary" link @click="handleView(scope.row)">
                        <el-icon>
                            <View />
                        </el-icon>
                        查看
                    </el-button>
                    <el-button type="primary" link @click="handleEdit(scope.row)">
                        <el-icon>
                            <Edit />
                        </el-icon>
                        编辑
                    </el-button>
                    <el-button :type="scope.row.status === '1' ? 'danger' : 'success'" link
                        @click="handleToggleStatus(scope.row)">
                        <el-icon v-if="scope.row.status === '1'">
                            <CircleClose />
                        </el-icon>
                        <el-icon v-else>
                            <CircleCheck />
                        </el-icon>
                        {{ scope.row.status === '1' ? '禁用' : '启用' }}
                    </el-button>
                    <el-button type="warning" link @click="handleResetPassword(scope.row)">
                        <el-icon>
                            <Key />
                        </el-icon>
                        重置密码
                    </el-button>
                    <el-dropdown>
                        <el-button type="info" link>
                            更多<el-icon class="el-icon--right">
                                <ArrowDown />
                            </el-icon>
                        </el-button>
                        <template #dropdown>
                            <el-dropdown-menu>
                                <el-dropdown-item @click="handleViewLogs(scope.row)">
                                    <el-icon>
                                        <Document />
                                    </el-icon>
                                    操作日志
                                </el-dropdown-item>
                                <el-dropdown-item @click="handleDelete(scope.row)" divided>
                                    <el-icon>
                                        <Delete />
                                    </el-icon>
                                    <span style="color: #f56c6c;">删除用户</span>
                                </el-dropdown-item>
                            </el-dropdown-menu>
                        </template>
                    </el-dropdown>
                </template>
            </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination">
            <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleSizeChange" @current-change="handleCurrentChange" />
        </div>

        <!-- 用户详情对话框 -->
        <el-dialog v-model="detailDialogVisible" title="用户详情" width="800px">
            <div v-if="currentUser" class="user-detail">
                <el-descriptions :column="2" border>
                    <el-descriptions-item label="头像">
                        <el-avatar :size="60" :src="currentUser.avatar || ''" :icon="User" />
                    </el-descriptions-item>
                    <el-descriptions-item label="用户ID">{{ currentUser.id }}</el-descriptions-item>
                    <el-descriptions-item label="用户名">{{ currentUser.username }}</el-descriptions-item>
                    <el-descriptions-item label="昵称">{{ currentUser.nickname || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="手机号">{{ currentUser.phone || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="邮箱">{{ currentUser.email || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="用户类型">
                        <el-tag :type="getUserTypeTagType(currentUser.userType)">
                            {{ getUserTypeText(currentUser.userType) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="身份验证">
                        <el-tag :type="getVerificationTagType(currentUser.verificationStatus)">
                            {{ getVerificationText(currentUser.verificationStatus) }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="账户状态">
                        <el-tag :type="currentUser.status === '1' ? 'success' : 'danger'">
                            {{ currentUser.status === '1' ? '正常' : '禁用' }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="注册时间">{{ currentUser.createTime }}</el-descriptions-item>
                    <el-descriptions-item label="最后登录">
                        {{ currentUser.lastLoginTime || '从未登录' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="实名姓名">{{ currentUser.realName || '-' }}</el-descriptions-item>
                </el-descriptions>
            </div>
            <template #footer>
                <el-button @click="detailDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>

        <!-- 编辑用户对话框 -->
        <el-dialog v-model="dialogVisible" :title="form.id === 0 ? '新增用户' : '编辑用户'" width="500px"
            :close-on-click-modal="false">
            <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="form.username" :disabled="form.id !== 0" />
                </el-form-item>
                <el-form-item label="密码" prop="password" v-if="form.id === 0">
                    <el-input v-model="form.password" type="password" show-password />
                </el-form-item>
                <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="form.nickname" />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="form.phone" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="form.email" />
                </el-form-item>
                <el-form-item label="用户类型" prop="userType">
                    <el-select v-model="form.userType" placeholder="请选择用户类型">
                        <el-option label="普通用户" value="ROLE_USER" />
                        <el-option label="房东" value="ROLE_HOST" />
                        <el-option label="管理员" value="ROLE_ADMIN" />
                    </el-select>
                </el-form-item>
                <el-form-item label="实名姓名" prop="realName">
                    <el-input v-model="form.realName" />
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="dialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
                        确定
                    </el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 重置密码结果对话框 -->
        <el-dialog v-model="passwordDialogVisible" title="重置密码结果" width="500px">
            <div v-if="resetPasswordResults.length === 0">
                无可重置的密码
            </div>
            <div v-else>
                <p>已重置以下用户的密码：</p>
                <el-table :data="resetPasswordResults" border style="width: 100%">
                    <el-table-column prop="username" label="用户名" />
                    <el-table-column prop="password" label="新密码" />
                </el-table>
                <div class="password-tips">
                    <el-alert title="请妥善保管新密码，关闭此窗口后将无法再次查看！" type="warning" :closable="false" />
                </div>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button type="primary" @click="passwordDialogVisible = false">
                        确定
                    </el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
    User,
    Plus,
    Download,
    CircleCheck,
    CircleClose,
    Key,
    Delete,
    View,
    Edit,
    ArrowDown,
    Document,
    Warning
} from '@element-plus/icons-vue'
import type { User as UserType, UserSearchParams } from '@/types'
import {
    getUserList,
    updateUser,
    updateUserStatus,
    resetUserPassword,
    batchUpdateUserStatus,
    batchResetUserPasswords,
    batchDeleteUsers,
    createUser,
    getUserDetail
} from '@/api/user'
import TableSearch from '@/components/table-search.vue'
import type { FormOptionList } from '@/types/form-option'

// 搜索表单
const searchForm = reactive<UserSearchParams & { dateRange?: string[], sort?: string }>({
    page: 1,
    pageSize: 10,
    username: '',
    phone: '',
    email: '',
    status: '',
    userType: '',
    dateRange: [],
    sort: 'createdAt,desc'
})

// 筛选配置
const searchOptions: FormOptionList[] = [
    { label: '用户名', prop: 'username', type: 'input', placeholder: '请输入用户名' },
    { label: '邮箱', prop: 'email', type: 'input', placeholder: '请输入邮箱' },
    { label: '用户类型', prop: 'userType', type: 'select', placeholder: '请选择用户类型', opts: [
        { label: '普通用户', value: 'ROLE_USER' },
        { label: '房东', value: 'ROLE_HOST' },
        { label: '管理员', value: 'ROLE_ADMIN' }
    ]},
]

// 统计数据
const totalUsers = ref(0)
const activeUsers = ref(0)
const pendingUsers = ref(0)
const disabledUsers = ref(0)

// 表格数据
const tableData = ref<UserType[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedRows = ref<UserType[]>([])
const exportLoading = ref(false)
const submitLoading = ref(false)

// 用户详情
const detailDialogVisible = ref(false)
const currentUser = ref<UserType | null>(null)

// 处理表格选择变化
const handleSelectionChange = (selection: UserType[]) => {
    selectedRows.value = selection
}

// 编辑表单
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Partial<UserType> & { password?: string }>({
    id: 0,
    username: '',
    nickname: '',
    phone: '',
    email: '',
    userType: '',
    realName: '',
    password: ''
})

// 重置密码结果
const passwordDialogVisible = ref(false)
const resetPasswordResults = ref<{ username: string, password: string }[]>([])

// 表单校验规则
const rules: FormRules = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' }
    ],
    nickname: [
        { required: true, message: '请输入昵称', trigger: 'blur' }
    ],
    phone: [
        { required: true, message: '请输入手机号', trigger: 'blur' },
        { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
    ],
    email: [
        { required: true, message: '请输入邮箱', trigger: 'blur' },
        { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
    ],
    userType: [
        { required: true, message: '请选择用户类型', trigger: 'change' }
    ]
}

// 辅助方法
const getUserTypeText = (type?: string) => {
    if (!type) return '未知'
    const typeMap: Record<string, string> = {
        'ROLE_USER': '普通用户',
        'ROLE_ADMIN': '管理员',
        'ROLE_HOST': '房东',
        // 兼容旧的格式
        'USER': '普通用户',
        'ADMIN': '管理员',
        'LANDLORD': '房东',
        'TENANT': '租客'
    }
    return typeMap[type] || '未知'
}

const getUserTypeTagType = (type?: string) => {
    if (!type) return 'info'
    const typeMap: Record<string, string> = {
        'ROLE_USER': 'info',
        'ROLE_ADMIN': 'danger',
        'ROLE_HOST': 'success',
        // 兼容旧的格式
        'USER': 'info',
        'ADMIN': 'danger',
        'LANDLORD': 'success',
        'TENANT': 'primary'
    }
    return typeMap[type] || 'info'
}

const getVerificationText = (status?: string) => {
    if (!status) return '未认证'
    const statusMap: Record<string, string> = {
        'VERIFIED': '已认证',
        'PENDING': '待审核',
        'REJECTED': '已拒绝',
        'UNVERIFIED': '未认证'
    }
    return statusMap[status] || '未认证'
}

const getVerificationTagType = (status?: string) => {
    if (!status) return 'info'
    const statusMap: Record<string, string> = {
        'VERIFIED': 'success',
        'PENDING': 'warning',
        'REJECTED': 'danger',
        'UNVERIFIED': 'info'
    }
    return statusMap[status] || 'info'
}

const formatDateTime = (dateTime: string) => {
    if (!dateTime) return '-'
    return new Date(dateTime).toLocaleString('zh-CN')
}

// 计算统计数据
const calculateStats = () => {
    totalUsers.value = tableData.value.length
    activeUsers.value = tableData.value.filter(user => user.status === '1').length
    pendingUsers.value = tableData.value.filter(user => user.verificationStatus === 'PENDING').length
    disabledUsers.value = tableData.value.filter(user => user.status === '0').length
}

// 搜索方法
const handleSearch = () => {
    currentPage.value = 1
    fetchData()
}

// 获取数据
const fetchData = async () => {
    loading.value = true
    try {
        // 处理日期范围
        const params: any = { ...searchForm }
        if (params.dateRange && params.dateRange.length === 2) {
            params.startTime = params.dateRange[0]
            params.endTime = params.dateRange[1]
        }
        delete params.dateRange

        const res = await getUserList({
            page: currentPage.value,
            pageSize: pageSize.value,
            username: params.username,
            email: params.email,
            userType: params.userType as string,
            sort: params.sort || 'createdAt,desc',
        } as UserSearchParams)
        tableData.value = res.list
        total.value = res.total
        calculateStats()
    } catch (error) {
        console.error('获取用户列表失败:', error)
        ElMessage.error('获取用户列表失败')
    } finally {
        loading.value = false
    }
}

// 分页方法
const handleSizeChange = (val: number) => {
    pageSize.value = val
    fetchData()
}

const handleCurrentChange = (val: number) => {
    currentPage.value = val
    fetchData()
}

// 查看用户详情
const handleView = async (row: UserType) => {
    try {
        currentUser.value = await getUserDetail(row.id)
        detailDialogVisible.value = true
    } catch (error) {
        console.error('获取用户详情失败:', error)
        ElMessage.error('获取用户详情失败')
    }
}

// 新增用户
const handleAdd = () => {
    Object.assign(form, {
        id: 0,
        username: '',
        nickname: '',
        phone: '',
        email: '',
        userType: '',
        realName: '',
        password: ''
    })
    dialogVisible.value = true
}

// 编辑用户
const handleEdit = (row: UserType) => {
    Object.assign(form, {
        id: row.id,
        username: row.username,
        nickname: row.nickname,
        phone: row.phone,
        email: row.email,
        userType: row.userType,
        realName: row.realName
    })
    dialogVisible.value = true
}

// 切换用户状态
const handleToggleStatus = async (row: UserType) => {
    try {
        await ElMessageBox.confirm(
            `确定要${row.status === '1' ? '禁用' : '启用'}该用户吗？`,
            '提示',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }
        )
        await updateUserStatus(row.id, row.status !== '1')
        ElMessage.success(`${row.status === '1' ? '禁用' : '启用'}成功`)
        row.status = row.status === '1' ? '0' : '1'
        calculateStats()
    } catch (error) {
        console.error('操作失败:', error)
    }
}

// 重置用户密码
const handleResetPassword = async (row: UserType) => {
    try {
        await ElMessageBox.confirm(
            '确定要重置该用户的密码吗？',
            '提示',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }
        )

        const result = await resetUserPassword(row.id)
        resetPasswordResults.value = [{
            username: row.username,
            password: result.newPassword
        }]

        passwordDialogVisible.value = true
    } catch (error) {
        console.error('重置密码失败:', error)
        ElMessage.error('重置密码失败')
    }
}

// 删除用户
const handleDelete = async (row: UserType) => {
    try {
        await ElMessageBox.confirm(
            `确定要删除用户"${row.username}"吗？此操作不可恢复！`,
            '危险操作',
            {
                confirmButtonText: '确定删除',
                cancelButtonText: '取消',
                type: 'error'
            }
        )

        await batchDeleteUsers([row.id])
        ElMessage.success('删除成功')
        fetchData()
    } catch (error) {
        console.error('删除用户失败:', error)
        if (error !== 'cancel') {
            ElMessage.error('删除用户失败')
        }
    }
}

// 排序变化
const handleSortChange = ({ prop, order }: any) => {
    if (!prop || !order) {
        searchForm.sort = 'createdAt,desc'
    } else {
        const direction = order === 'descending' ? 'desc' : 'asc'
        searchForm.sort = `${prop},${direction}`
    }
    currentPage.value = 1
    fetchData()
}

// 查看操作日志
const handleViewLogs = (_row: UserType) => {
    ElMessage.info('操作日志功能开发中...')
}

// 批量启用用户
const handleBatchEnable = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }

    try {
        await ElMessageBox.confirm('确认要批量启用选中的用户吗？', '批量操作', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })

        const ids = selectedRows.value.map(item => item.id)
        await batchUpdateUserStatus(ids, true)
        ElMessage.success('批量启用成功')
        fetchData()
    } catch (error) {
        console.error('批量启用失败:', error)
    }
}

// 批量禁用用户
const handleBatchDisable = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }

    try {
        await ElMessageBox.confirm('确认要批量禁用选中的用户吗？', '批量操作', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })

        const ids = selectedRows.value.map(item => item.id)
        await batchUpdateUserStatus(ids, false)
        ElMessage.success('批量禁用成功')
        fetchData()
    } catch (error) {
        console.error('批量禁用失败:', error)
    }
}

// 批量重置密码
const handleBatchResetPassword = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }

    try {
        await ElMessageBox.confirm(`确认要重置选中的 ${selectedRows.value.length} 个用户的密码吗？`, '批量操作', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })

        const ids = selectedRows.value.map(item => item.id)
        const result = await batchResetUserPasswords(ids)

        // 处理返回结果
        resetPasswordResults.value = []
        for (const id in result) {
            const user = selectedRows.value.find(item => item.id.toString() === id)
            if (user) {
                resetPasswordResults.value.push({
                    username: user.username,
                    password: result[id]
                })
            }
        }

        passwordDialogVisible.value = true
    } catch (error) {
        console.error('批量重置密码失败:', error)
        ElMessage.error('批量重置密码失败')
    }
}

// 批量删除用户
const handleBatchDelete = async () => {
    if (selectedRows.value.length === 0) {
        ElMessage.warning('请至少选择一项')
        return
    }

    try {
        await ElMessageBox.confirm(
            `确认要删除选中的 ${selectedRows.value.length} 个用户吗？此操作不可恢复！`,
            '危险操作',
            {
                confirmButtonText: '确定删除',
                cancelButtonText: '取消',
                type: 'error'
            }
        )

        const ids = selectedRows.value.map(item => item.id)
        await batchDeleteUsers(ids)
        ElMessage.success('批量删除成功')
        fetchData()
    } catch (error) {
        console.error('批量删除失败:', error)
        ElMessage.error('批量删除失败')
    }
}

// 导出功能
const handleExport = async () => {
    exportLoading.value = true
    try {
        // 这里可以调用导出API
        ElMessage.info('导出功能开发中...')
    } catch (error) {
        console.error('导出失败:', error)
        ElMessage.error('导出失败')
    } finally {
        exportLoading.value = false
    }
}

// 提交表单
const handleSubmit = async () => {
    if (!formRef.value) return

    try {
        await formRef.value.validate()
        submitLoading.value = true

        if (form.id === 0) {
            // 新增用户
            await createUser({
                username: form.username!,
                nickname: form.nickname!,
                phone: form.phone!,
                email: form.email!,
                userType: form.userType!,
                realName: form.realName || '',
                password: form.password!,
                status: '1',
                createTime: new Date().toISOString()
            } as any)
            ElMessage.success('创建成功')
        } else {
            // 更新用户
            await updateUser(form.id!, {
                nickname: form.nickname,
                phone: form.phone,
                email: form.email,
                userType: form.userType,
                realName: form.realName
            })
            ElMessage.success('更新成功')
        }

        dialogVisible.value = false
        fetchData()
    } catch (error) {
        console.error('提交失败:', error)
        ElMessage.error('提交失败')
    } finally {
        submitLoading.value = false
    }
}

// 初始化
onMounted(() => {
    fetchData()
})
</script>

<style scoped lang="scss">
.user-list {
    .stats-container {
        margin-bottom: 20px;

        .stats-card {
            .stats-content {
                display: flex;
                align-items: center;

                .stats-icon {
                    width: 50px;
                    height: 50px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin-right: 15px;
                }

                .stats-info {
                    .stats-title {
                        font-size: 14px;
                        color: #666;
                        margin-bottom: 5px;
                    }

                    .stats-value {
                        font-size: 24px;
                        font-weight: bold;
                        color: #333;
                    }
                }
            }
        }
    }

    .search-box {
        margin-bottom: 20px;
        padding: 20px;
        background-color: #fff;
        border-radius: 8px;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    }

    .batch-operation {
        margin-bottom: 20px;

        .batch-buttons {
            display: inline-block;
            margin-left: 15px;

            .el-button {
                margin-left: 8px;
            }
        }
    }

    .user-info {
        display: flex;
        align-items: center;

        .user-avatar {
            margin-right: 12px;
        }

        .user-details {
            .username {
                font-weight: 500;
                color: #303133;
                margin-bottom: 2px;
            }

            .nickname {
                font-size: 12px;
                color: #909399;
            }
        }
    }

    .pagination {
        margin-top: 20px;
        display: flex;
        justify-content: flex-end;
    }

    .password-tips {
        margin-top: 15px;
    }

    .user-detail {
        .el-descriptions {
            margin-top: 20px;
        }
    }
}

// 响应式设计
@media (max-width: 768px) {
    .user-list {
        .stats-container {
            .el-col {
                margin-bottom: 15px;
            }
        }

        .search-box {
            .el-form-item {
                margin-bottom: 15px;
            }
        }
    }
}
</style>