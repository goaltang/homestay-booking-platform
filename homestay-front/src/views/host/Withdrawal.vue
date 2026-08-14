<template>
    <div class="withdrawal-container">
        <h2>提现管理</h2>

        <!-- 余额信息卡片 -->
        <el-card class="balance-card" shadow="never">
            <el-row :gutter="20">
                <el-col :span="8">
                    <div class="balance-info">
                        <div class="label">可提现余额</div>
                        <div class="value">¥{{ balanceInfo.available.toFixed(2) }}</div>
                        <el-button type="primary" @click="showWithdrawalDialog"
                            :disabled="!hasAccounts || balanceInfo.available <= 0">申请提现</el-button>
                    </div>
                </el-col>
                <el-col :span="8">
                    <div class="balance-info">
                        <div class="label">提现中金额</div>
                        <div class="value">¥{{ balanceInfo.pending.toFixed(2) }}</div>
                    </div>
                </el-col>
                <el-col :span="8">
                    <div class="balance-info">
                        <div class="label">累计已提现</div>
                        <div class="value">¥{{ balanceInfo.withdrawn.toFixed(2) }}</div>
                    </div>
                </el-col>
            </el-row>
        </el-card>

        <!-- 账户管理卡片 -->
        <el-card class="account-card" shadow="never">
            <div class="card-header">
                <h3>账户管理</h3>
                <el-button type="primary" size="small" @click="showAccountDialog(null)">添加账户</el-button>
            </div>

            <div v-if="accounts.length === 0" class="empty-tip">
                <el-empty description="暂无提现账户，请添加账户"></el-empty>
            </div>

            <el-table v-else :data="accounts" style="width: 100%">
                <el-table-column label="账户类型" width="120">
                    <template #default="{ row }">
                        <el-tag :type="getAccountTypeTag(row.accountType)">{{ getAccountTypeName(row.accountType)
                            }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="accountName" label="账户名" width="150" />
                <el-table-column prop="accountNumber" label="账号" min-width="180" />
                <el-table-column prop="bankName" label="银行名称" width="150" />
                <el-table-column label="默认账户" width="100">
                    <template #default="{ row }">
                        <el-tag v-if="row.isDefault" type="success">默认</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="200">
                    <template #default="{ row }">
                        <el-button type="primary" link @click="showAccountDialog(row)">编辑</el-button>
                        <el-button v-if="!row.isDefault" type="success" link
                            @click="setDefaultAccount(row.id)">设为默认</el-button>
                        <el-button type="danger" link @click="deleteAccount(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 提现记录卡片 -->
        <el-card class="history-card" shadow="never">
            <div class="card-header">
                <h3>提现记录</h3>
            </div>

            <el-table :data="withdrawals.content" style="width: 100%" v-loading="loading">
                <el-table-column prop="id" label="记录ID" width="100" />
                <el-table-column prop="createTime" label="申请时间" width="180" />
                <el-table-column prop="amount" label="金额" width="150">
                    <template #default="{ row }">
                        ¥{{ row.amount.toFixed(2) }}
                    </template>
                </el-table-column>
                <el-table-column prop="accountInfo" label="账户信息" min-width="200" />
                <el-table-column prop="status" label="状态" width="120">
                    <template #default="{ row }">
                        <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="completeTime" label="完成时间" width="180" />
                <el-table-column label="操作" width="100">
                    <template #default="{ row }">
                        <el-button v-if="row.status === 'PENDING'" type="danger" link
                            @click="cancelWithdrawal(row.id)">取消</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div class="pagination" v-if="withdrawals.totalElements > 0">
                <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
                    :total="withdrawals.totalElements" @size-change="handleSizeChange"
                    @current-change="handleCurrentChange" />
            </div>
        </el-card>

        <!-- 添加/编辑账户对话框 -->
        <el-dialog v-model="accountDialogVisible" :title="editingAccount ? '编辑账户' : '添加账户'" width="500px">
            <el-form ref="accountFormRef" :model="accountForm" :rules="accountRules" label-width="100px">
                <el-form-item label="账户类型" prop="accountType">
                    <el-select v-model="accountForm.accountType" placeholder="请选择账户类型" style="width: 100%">
                        <el-option label="银行卡" value="BANK" />
                        <el-option label="支付宝" value="ALIPAY" />
                        <el-option label="微信" value="WECHAT" />
                    </el-select>
                </el-form-item>

                <el-form-item label="账户名" prop="accountName">
                    <el-input v-model="accountForm.accountName" placeholder="请输入账户名/收款人姓名" />
                </el-form-item>

                <el-form-item label="账号" prop="accountNumber">
                    <el-input v-model="accountForm.accountNumber" placeholder="请输入银行卡号/支付宝账号/微信账号" />
                </el-form-item>

                <el-form-item label="银行名称" prop="bankName" v-if="accountForm.accountType === 'BANK'">
                    <el-input v-model="accountForm.bankName" placeholder="请输入开户银行" />
                </el-form-item>

                <el-form-item label="支行名称" prop="bankBranch" v-if="accountForm.accountType === 'BANK'">
                    <el-input v-model="accountForm.bankBranch" placeholder="请输入开户支行(选填)" />
                </el-form-item>

                <el-form-item>
                    <el-checkbox v-model="accountForm.isDefault">设为默认提现账户</el-checkbox>
                </el-form-item>
            </el-form>

            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="accountDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitAccount">确定</el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 申请提现对话框 -->
        <el-dialog v-model="withdrawalDialogVisible" title="申请提现" width="500px">
            <el-form ref="withdrawalFormRef" :model="withdrawalForm" :rules="withdrawalRules" label-width="100px">
                <el-form-item label="可提现余额">
                    <div class="available-balance">¥{{ balanceInfo.available.toFixed(2) }}</div>
                </el-form-item>

                <el-form-item label="提现金额" prop="amount">
                    <el-input-number v-model="withdrawalForm.amount" :min="100" :max="balanceInfo.available"
                        :precision="2" :step="100" style="width: 100%" />
                </el-form-item>

                <el-form-item label="提现账户" prop="accountId">
                    <el-select v-model="withdrawalForm.accountId" placeholder="请选择提现账户" style="width: 100%">
                        <el-option v-for="account in accounts" :key="account.id" :label="formatAccountLabel(account)"
                            :value="account.id" />
                    </el-select>
                </el-form-item>

                <el-form-item label="备注" prop="remark">
                    <el-input v-model="withdrawalForm.remark" type="textarea" placeholder="请输入提现备注(选填)" maxlength="100"
                        show-word-limit rows="3" />
                </el-form-item>
            </el-form>

            <div class="withdrawal-notice">
                <p><i class="el-icon-info"></i> 提现说明：</p>
                <ol>
                    <li>提现金额最低为 ¥100.00</li>
                    <li>提现申请审核通常需要1-3个工作日</li>
                    <li>提现到账时间取决于您的收款银行，一般为1-3个工作日</li>
                </ol>
            </div>

            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="withdrawalDialogVisible = false">取消</el-button>
                    <el-button type="primary" @click="submitWithdrawal">确定提现</el-button>
                </span>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import {
    getWithdrawalBalance,
    getBankAccounts,
    addBankAccount,
    updateBankAccount,
    deleteBankAccount,
    setDefaultBankAccount,
    requestWithdrawal,
    getWithdrawalHistory,
    cancelWithdrawal as cancelWithdrawalApi,
    BankAccount,
    Withdrawal,
    PaginationResponse
} from '@/api/earnings'

// 账户相关
const accountDialogVisible = ref(false)
const accountFormRef = ref<FormInstance>()
const editingAccount = ref<BankAccount | null>(null)
const accounts = ref<BankAccount[]>([])
const hasAccounts = computed(() => accounts.value.length > 0)

const accountForm = reactive({
    id: 0,
    accountName: '',
    accountNumber: '',
    bankName: '',
    bankBranch: '',
    accountType: 'BANK' as 'BANK' | 'ALIPAY' | 'WECHAT',
    isDefault: false
})

const accountRules: FormRules = {
    accountType: [{ required: true, message: '请选择账户类型', trigger: 'change' }],
    accountName: [{ required: true, message: '请输入账户名', trigger: 'blur' }],
    accountNumber: [{ required: true, message: '请输入账号', trigger: 'blur' }],
    bankName: [{ required: true, message: '请输入银行名称', trigger: 'blur' }]
}

// 余额信息
const balanceInfo = reactive({
    available: 0,
    pending: 0,
    withdrawn: 0
})

// 提现相关
const withdrawalDialogVisible = ref(false)
const withdrawalFormRef = ref<FormInstance>()
const withdrawalForm = reactive({
    amount: 100,
    accountId: 0,
    remark: ''
})
const withdrawalRules: FormRules = {
    amount: [
        { required: true, message: '请输入提现金额', trigger: 'blur' },
        { type: 'number', min: 100, message: '提现金额不能低于100元', trigger: 'blur' }
    ],
    accountId: [{ required: true, message: '请选择提现账户', trigger: 'change' }]
}

// 提现记录
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const withdrawals = ref<PaginationResponse<Withdrawal>>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 10,
    number: 0
})

// 初始化数据
onMounted(async () => {
    await Promise.all([
        fetchBalance(),
        fetchAccounts(),
        fetchWithdrawals()
    ])
})

// 获取余额信息
const fetchBalance = async () => {
    try {
        const balance = await getWithdrawalBalance()
        balanceInfo.available = balance

        // 计算提现中金额和已提现金额（实际应用中应从后端获取）
        const pending = withdrawals.value.content
            .filter(w => w.status === 'PENDING')
            .reduce((sum, w) => sum + w.amount, 0)

        const withdrawn = withdrawals.value.content
            .filter(w => w.status === 'COMPLETED')
            .reduce((sum, w) => sum + w.amount, 0)

        balanceInfo.pending = pending
        balanceInfo.withdrawn = withdrawn
    } catch (error) {
        console.error('获取余额失败:', error)
    }
}

// 获取账户列表
const fetchAccounts = async () => {
    try {
        accounts.value = await getBankAccounts()
    } catch (error) {
        console.error('获取账户列表失败:', error)
    }
}

// 获取提现记录
const fetchWithdrawals = async () => {
    try {
        loading.value = true
        withdrawals.value = await getWithdrawalHistory({
            page: currentPage.value - 1,
            size: pageSize.value
        })
    } catch (error) {
        console.error('获取提现记录失败:', error)
    } finally {
        loading.value = false
    }
}

// 页码变化
const handleCurrentChange = (page: number) => {
    currentPage.value = page
    fetchWithdrawals()
}

// 每页大小变化
const handleSizeChange = (size: number) => {
    pageSize.value = size
    fetchWithdrawals()
}

// 显示添加/编辑账户对话框
const showAccountDialog = (account: BankAccount | null) => {
    editingAccount.value = account

    if (account) {
        Object.assign(accountForm, {
            id: account.id,
            accountName: account.accountName,
            accountNumber: account.accountNumber,
            bankName: account.bankName || '',
            bankBranch: account.bankBranch || '',
            accountType: account.accountType,
            isDefault: account.isDefault
        })
    } else {
        Object.assign(accountForm, {
            id: 0,
            accountName: '',
            accountNumber: '',
            bankName: '',
            bankBranch: '',
            accountType: 'BANK',
            isDefault: accounts.value.length === 0 // 如果没有账户，默认设为默认账户
        })
    }

    accountDialogVisible.value = true
}

// 提交账户
const submitAccount = async () => {
    if (!accountFormRef.value) return

    try {
        await accountFormRef.value.validate()

        if (editingAccount.value) {
            // 更新账户
            await updateBankAccount(accountForm.id, {
                accountName: accountForm.accountName,
                accountNumber: accountForm.accountNumber,
                bankName: accountForm.bankName,
                bankBranch: accountForm.bankBranch,
                accountType: accountForm.accountType,
                isDefault: accountForm.isDefault
            })
            ElMessage.success('账户更新成功')
        } else {
            // 添加账户
            await addBankAccount({
                accountName: accountForm.accountName,
                accountNumber: accountForm.accountNumber,
                bankName: accountForm.bankName,
                bankBranch: accountForm.bankBranch,
                accountType: accountForm.accountType,
                isDefault: accountForm.isDefault
            })
            ElMessage.success('账户添加成功')
        }

        accountDialogVisible.value = false
        fetchAccounts()
    } catch (error) {
        console.error('提交账户失败:', error)
    }
}

// 设置默认账户
const setDefaultAccount = async (id: number) => {
    try {
        await setDefaultBankAccount(id)
        ElMessage.success('默认账户设置成功')
        fetchAccounts()
    } catch (error) {
        console.error('设置默认账户失败:', error)
    }
}

// 删除账户
const deleteAccount = async (account: BankAccount) => {
    try {
        await ElMessageBox.confirm(
            `确定要删除账户 ${account.accountName}(${account.accountNumber}) 吗？`,
            '删除账户',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }
        )

        await deleteBankAccount(account.id)
        ElMessage.success('账户删除成功')
        fetchAccounts()
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('删除账户失败:', error)
        }
    }
}

// 显示提现对话框
const showWithdrawalDialog = () => {
    // 设置默认选择的账户为默认账户
    const defaultAccount = accounts.value.find(a => a.isDefault)
    withdrawalForm.accountId = defaultAccount ? defaultAccount.id : (accounts.value[0]?.id || 0)
    withdrawalForm.amount = 100
    withdrawalForm.remark = ''

    withdrawalDialogVisible.value = true
}

// 提交提现申请
const submitWithdrawal = async () => {
    if (!withdrawalFormRef.value) return

    try {
        await withdrawalFormRef.value.validate()

        await requestWithdrawal({
            amount: withdrawalForm.amount,
            accountId: withdrawalForm.accountId,
            remark: withdrawalForm.remark || undefined
        })

        ElMessage.success('提现申请提交成功')
        withdrawalDialogVisible.value = false

        // 刷新数据
        await Promise.all([
            fetchBalance(),
            fetchWithdrawals()
        ])
    } catch (error) {
        console.error('提交提现申请失败:', error)
    }
}

// 取消提现
const cancelWithdrawal = async (id: number) => {
    try {
        await ElMessageBox.confirm(
            '确定要取消此提现申请吗？',
            '取消提现',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }
        )

        await cancelWithdrawalApi(id)
        ElMessage.success('提现申请已取消')

        // 刷新数据
        await Promise.all([
            fetchBalance(),
            fetchWithdrawals()
        ])
    } catch (error: any) {
        if (error !== 'cancel') {
            console.error('取消提现失败:', error)
        }
    }
}

// 格式化账户类型
const getAccountTypeTag = (type: string): string => {
    const types: Record<string, string> = {
        'BANK': 'primary',
        'ALIPAY': 'success',
        'WECHAT': 'info'
    }
    return types[type] || 'default'
}

const getAccountTypeName = (type: string): string => {
    const names: Record<string, string> = {
        'BANK': '银行卡',
        'ALIPAY': '支付宝',
        'WECHAT': '微信'
    }
    return names[type] || '未知'
}

// 格式化账户选项标签
const formatAccountLabel = (account: BankAccount): string => {
    const typeName = getAccountTypeName(account.accountType)
    return `${typeName} - ${account.accountName} (${account.accountNumber.substring(account.accountNumber.length - 4)})`
}

// 格式化状态
const getStatusType = (status: string): string => {
    const types: Record<string, string> = {
        'PENDING': 'warning',
        'COMPLETED': 'success',
        'REJECTED': 'danger'
    }
    return types[status] || 'info'
}

const getStatusText = (status: string): string => {
    const texts: Record<string, string> = {
        'PENDING': '处理中',
        'COMPLETED': '已完成',
        'REJECTED': '已拒绝'
    }
    return texts[status] || '未知'
}
</script>

<style scoped>
.withdrawal-container {
    padding: 20px;
}

.balance-card,
.account-card,
.history-card {
    margin-bottom: 20px;
}

.balance-info {
    text-align: center;
    padding: 20px 0;
}

.balance-info .label {
    font-size: 14px;
    color: #606266;
    margin-bottom: 10px;
}

.balance-info .value {
    font-size: 24px;
    font-weight: bold;
    color: #409EFF;
    margin-bottom: 15px;
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.card-header h3 {
    margin: 0;
    font-size: 18px;
}

.empty-tip {
    padding: 20px 0;
}

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}

.available-balance {
    font-size: 18px;
    font-weight: bold;
    color: #409EFF;
}

.withdrawal-notice {
    margin-top: 10px;
    padding: 10px;
    background-color: #f8f8f8;
    border-radius: 4px;
    font-size: 14px;
    color: #606266;
}

.withdrawal-notice p {
    margin-bottom: 10px;
    font-weight: bold;
}

.withdrawal-notice ol {
    padding-left: 20px;
    margin: 0;
}

.withdrawal-notice li {
    margin-bottom: 5px;
}
</style>