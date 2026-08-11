<template>
    <div class="audit-workbench">


        <!-- 工作台操作栏 -->
        <el-card shadow="never" class="workbench-toolbar">
            <div class="toolbar-content">
                <!-- 左侧：主要操作 -->
                <div class="main-actions">
                    <el-button type="warning" size="default" @click="startBatchReview" :disabled="!hasSelectedItems"
                        class="batch-btn">
                        <el-icon>
                            <List />
                        </el-icon>
                        批量审核 ({{ selectedHomestays.length }})
                    </el-button>
                </div>

                <!-- 中间：搜索筛选 -->
                <div class="search-filters">
                    <el-input v-model="searchKeyword" placeholder="搜索房源标题..." size="default" clearable
                        style="width: 300px;">
                        <template #prefix>
                            <el-icon>
                                <Search />
                            </el-icon>
                        </template>
                    </el-input>
                </div>

                <!-- 右侧：系统操作 -->
                <div class="system-actions">
                    <el-button-group>
                        <el-button size="default" @click="refreshData">
                            <el-icon>
                                <Refresh />
                            </el-icon>
                            刷新
                        </el-button>
                        <el-button size="default" @click="viewStatistics" type="primary" plain>
                            <el-icon>
                                <DataAnalysis />
                            </el-icon>
                            统计
                        </el-button>
                        <el-button size="default" @click="viewAuditHistory" plain>
                            <el-icon>
                                <Document />
                            </el-icon>
                            历史
                        </el-button>
                        <el-button size="default" @click="openViolationManagement" type="danger" plain>
                            <el-icon>
                                <Warning />
                            </el-icon>
                            违规管理
                        </el-button>
                    </el-button-group>

                    <el-switch v-model="autoRefreshEnabled" @change="toggleAutoRefresh" active-text="自动刷新"
                        style="margin-left: 12px;" />
                </div>
            </div>
        </el-card>

        <!-- 待审核列表 -->
        <el-card shadow="never" class="pending-list">
            <template #header>
                <div class="list-header">
                    <div class="header-left">
                        <h3>待审核房源</h3>
                        <span class="count-badge">{{ filteredHomestays.length }} 个</span>
                    </div>
                    <div class="header-right">
                        <!-- 快捷选择功能 -->
                        <el-dropdown trigger="click" @command="handleQuickSelect" style="margin-right: 12px;">
                            <el-button size="default" plain>
                                <el-icon>
                                    <List />
                                </el-icon>
                                快捷选择
                                <el-icon class="el-icon--right">
                                    <ArrowDown />
                                </el-icon>
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <el-dropdown-item command="all">全选本页</el-dropdown-item>
                                    <el-dropdown-item command="none">清空选择</el-dropdown-item>
                                    <el-dropdown-item divided command="violation">选择疑似违规</el-dropdown-item>
                                    <el-dropdown-item command="improvement">选择待完善</el-dropdown-item>
                                    <el-dropdown-item command="normal">选择正常房源</el-dropdown-item>
                                    <el-dropdown-item divided command="recent">选择最近提交</el-dropdown-item>
                                    <el-dropdown-item command="older">选择较早提交</el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>

                        <!-- 批量操作按钮 -->
                        <el-button v-if="hasSelectedItems" type="primary" @click="startBatchReview"
                            :disabled="!hasSelectedItems" style="margin-right: 16px;">
                            <el-icon>
                                <Check />
                            </el-icon>
                            批量审核 ({{ selectedHomestays.length }})
                        </el-button>

                        <!-- 全选复选框 -->
                        <el-checkbox v-model="selectAll" @change="handleSelectAll"
                            :indeterminate="selectedHomestays.length > 0 && selectedHomestays.length < paginatedHomestays.length">
                            <span>全选 ({{ selectedHomestays.length }})</span>
                        </el-checkbox>
                    </div>
                </div>
            </template>

            <div v-if="filteredHomestays.length === 0" class="empty-state">
                <el-empty description="暂无待审核房源" />
            </div>

            <div v-else class="homestay-grid">
                <div v-for="homestay in paginatedHomestays" :key="homestay.id" class="homestay-card">
                    <div class="selection-checkbox">
                        <el-checkbox :model-value="selectedHomestays.includes(homestay.id)"
                            @update:model-value="(checked: boolean) => toggleHomestaySelection(homestay.id, checked)" />
                    </div>
                    <el-card shadow="hover" @click="openReviewDialog(homestay)"
                        :class="{ 'selected': selectedHomestays.includes(homestay.id) }">
                        <div class="homestay-content">
                            <div class="homestay-image">
                                <el-image :src="getHomestayImage(homestay)" fit="cover" lazy>
                                    <template #placeholder>
                                        <div class="image-placeholder">
                                            <el-icon>
                                                <Picture />
                                            </el-icon>
                                        </div>
                                    </template>
                                    <template #error>
                                        <div class="image-placeholder">
                                            <el-icon>
                                                <Picture />
                                            </el-icon>
                                            <span>暂无图片</span>
                                        </div>
                                    </template>
                                </el-image>
                                <div class="status-badge">
                                    <el-tag :type="getStatusTagType(homestay.status)" size="small">
                                        {{ formatStatus(homestay.status) }}
                                    </el-tag>
                                </div>
                                <!-- 违规警告标识 -->
                                <div v-if="checkViolation(homestay)" class="violation-badge">
                                    <el-tag type="danger" size="small" effect="dark">
                                        <el-icon>
                                            <Warning />
                                        </el-icon>
                                        疑似违规
                                    </el-tag>
                                </div>
                                <!-- 改善建议标识 -->
                                <div v-else-if="checkNeedsImprovement(homestay)" class="improvement-badge">
                                    <el-tag type="warning" size="small" effect="plain">
                                        <el-icon>
                                            <InfoFilled />
                                        </el-icon>
                                        待完善
                                    </el-tag>
                                </div>
                            </div>
                            <div class="homestay-info">
                                <div class="title">{{ homestay.title || homestay.name }}</div>
                                <div class="location">
                                    <el-icon>
                                        <Location />
                                    </el-icon>
                                    {{ getLocationText(homestay) }}
                                </div>
                                <div class="price">
                                    <span class="price-label">¥</span>
                                    <span class="price-value">{{ homestay.price }}</span>
                                    <span class="price-unit">/晚</span>
                                </div>
                                <div class="meta-info">
                                    <span class="submit-time">
                                        <el-icon>
                                            <Calendar />
                                        </el-icon>
                                        {{ formatDateTime(homestay.updatedAt) }}
                                    </span>
                                </div>
                            </div>
                        </div>
                        <div class="card-actions" @click.stop>
                            <el-button type="primary" size="default" @click="openReviewDialog(homestay)"
                                class="review-btn">
                                <el-icon>
                                    <View />
                                </el-icon>
                                开始审核
                            </el-button>
                        </div>
                    </el-card>
                </div>
            </div>

            <!-- 分页 -->
            <div class="pagination" v-if="filteredHomestays.length > pageSize">
                <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize"
                    :page-sizes="[12, 24, 48]" :total="filteredHomestays.length"
                    layout="total, sizes, prev, pager, next, jumper" />
            </div>
        </el-card>

        <!-- 详细审核对话框 -->
        <el-dialog v-model="reviewDialogVisible" title="房源详细审核" width="90%" top="2vh" :close-on-click-modal="false"
            :before-close="handleDialogClose">
            <div v-if="currentReviewItem" class="review-dialog-content">
                <el-row :gutter="20">
                    <!-- 左侧：房源信息 -->
                    <el-col :span="14">
                        <div class="review-sections">
                            <!-- 基本信息 -->
                            <el-card class="review-section" shadow="never">
                                <template #header>
                                    <div class="section-header">
                                        <el-icon>
                                            <House />
                                        </el-icon>
                                        <span>基本信息</span>
                                        <el-tag :type="getStatusTagType(currentReviewItem.status)" size="small">
                                            {{ formatStatus(currentReviewItem.status) }}
                                        </el-tag>
                                    </div>
                                </template>
                                <el-descriptions :column="2" border>
                                    <el-descriptions-item label="房源ID">{{ currentReviewItem.id }}</el-descriptions-item>
                                    <el-descriptions-item label="房源名称">
                                        <strong>{{ currentReviewItem.title || currentReviewItem.name }}</strong>
                                    </el-descriptions-item>
                                    <el-descriptions-item label="房源类型">
                                        {{ formatHomestayType(currentReviewItem.type || '未指定') }}
                                    </el-descriptions-item>
                                    <el-descriptions-item label="价格">
                                        <strong style="color: #e6a23c;">¥{{ currentReviewItem.price }}/晚</strong>
                                    </el-descriptions-item>
                                    <el-descriptions-item label="最大入住">{{ currentReviewItem.maxGuests || 'N/A' }}
                                        人</el-descriptions-item>
                                    <el-descriptions-item label="最少入住">{{ currentReviewItem.minNights || 'N/A' }}
                                        晚</el-descriptions-item>
                                    <el-descriptions-item label="创建时间">
                                        {{ formatDateTime(currentReviewItem.createdAt) }}
                                    </el-descriptions-item>
                                    <el-descriptions-item label="更新时间">
                                        {{ formatDateTime(currentReviewItem.updatedAt) }}
                                    </el-descriptions-item>
                                </el-descriptions>
                            </el-card>

                            <!-- 封面图片 -->
                            <el-card class="review-section" shadow="never" v-if="currentReviewItem.coverImage">
                                <template #header>
                                    <div class="section-header">
                                        <el-icon>
                                            <Picture />
                                        </el-icon>
                                        <span>封面图片</span>
                                    </div>
                                </template>
                                <div class="cover-image-container">
                                    <el-image :src="currentReviewItem.coverImage"
                                        :preview-src-list="[currentReviewItem.coverImage]" fit="cover"
                                        class="cover-image">
                                        <template #error>
                                            <div class="image-error">
                                                <el-icon>
                                                    <Picture />
                                                </el-icon>
                                                <span>封面加载失败</span>
                                            </div>
                                        </template>
                                    </el-image>
                                </div>
                            </el-card>

                            <!-- 房源图片集 -->
                            <el-card class="review-section" shadow="never">
                                <template #header>
                                    <div class="section-header">
                                        <el-icon>
                                            <Picture />
                                        </el-icon>
                                        <span>房源图片集</span>
                                        <span class="image-count">({{ getHomestayDisplayImages(currentReviewItem).length
                                            }} 张)</span>
                                    </div>
                                </template>
                                <div v-if="getHomestayDisplayImages(currentReviewItem).length > 0"
                                    class="image-review-grid">
                                    <div v-for="(image, index) in getHomestayDisplayImages(currentReviewItem)"
                                        :key="index" class="image-item">
                                        <el-image :src="image"
                                            :preview-src-list="getHomestayDisplayImages(currentReviewItem)"
                                            :initial-index="index" fit="cover" class="review-image">
                                            <template #placeholder>
                                                <div class="image-placeholder">
                                                    <el-icon>
                                                        <Picture />
                                                    </el-icon>
                                                    <span>加载中...</span>
                                                </div>
                                            </template>
                                            <template #error>
                                                <div class="image-error">
                                                    <el-icon>
                                                        <Picture />
                                                    </el-icon>
                                                    <span>加载失败</span>
                                                </div>
                                            </template>
                                        </el-image>
                                        <div v-if="image === (currentReviewItem.coverImage && ensureImageUrl(currentReviewItem.coverImage))"
                                            class="cover-badge">封面</div>
                                        <div v-else-if="index === 0" class="primary-badge">首图</div>
                                    </div>
                                </div>
                                <div v-else class="no-images">
                                    <el-icon>
                                        <Picture />
                                    </el-icon>
                                    <span>暂无图片</span>
                                </div>
                            </el-card>

                            <!-- 房东信息 -->
                            <el-card class="review-section" shadow="never">
                                <template #header>
                                    <div class="section-header">
                                        <el-icon>
                                            <User />
                                        </el-icon>
                                        <span>房东信息</span>
                                        <el-button v-if="!currentReviewItem.ownerName" type="warning" size="small" plain
                                            @click="loadOwnerInfo">
                                            <el-icon>
                                                <Refresh />
                                            </el-icon>
                                            重新获取
                                        </el-button>
                                    </div>
                                </template>
                                <div v-if="loadingOwnerInfo" style="text-align: center; padding: 20px;">
                                    <el-icon class="is-loading">
                                        <Loading />
                                    </el-icon>
                                    <span style="margin-left: 8px;">正在获取房东信息...</span>
                                </div>
                                <el-descriptions v-else :column="2" border>
                                    <el-descriptions-item label="房东姓名">
                                        <span v-if="currentReviewItem.ownerName">{{ currentReviewItem.ownerName
                                        }}</span>
                                        <el-tag v-else type="warning" size="small">信息缺失</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item label="用户名">
                                        <span v-if="currentReviewItem.ownerUsername">{{ currentReviewItem.ownerUsername
                                        }}</span>
                                        <el-tag v-else type="info" size="small">暂无</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item label="联系方式">
                                        <span v-if="currentReviewItem.ownerPhone">{{ currentReviewItem.ownerPhone
                                        }}</span>
                                        <el-tag v-else type="info" size="small">未提供</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item label="房东ID">
                                        <span v-if="(currentReviewItem as any).ownerId">{{ (currentReviewItem as
                                            any).ownerId
                                        }}</span>
                                        <el-tag v-else type="warning" size="small">缺失</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item label="注册时间">
                                        <span v-if="(currentReviewItem as any).ownerJoinDate">
                                            {{ formatDateTime((currentReviewItem as any).ownerJoinDate) }}
                                        </span>
                                        <el-tag v-else type="info" size="small">暂无</el-tag>
                                    </el-descriptions-item>
                                    <el-descriptions-item label="房源数量">
                                        <span v-if="(currentReviewItem as any).ownerHomestayCount">
                                            {{ (currentReviewItem as any).ownerHomestayCount }} 个
                                        </span>
                                        <el-tag v-else type="info" size="small">暂无</el-tag>
                                    </el-descriptions-item>
                                </el-descriptions>

                                <!-- 房东信息缺失警告 -->
                                <el-alert v-if="!currentReviewItem.ownerName || !(currentReviewItem as any).ownerId"
                                    title="房东信息不完整" type="warning" style="margin-top: 16px;" :closable="false">
                                    <template #default>
                                        <p>检测到房东信息不完整，可能原因：</p>
                                        <ul style="margin: 8px 0; padding-left: 20px;">
                                            <li>房东账户已被删除或禁用</li>
                                            <li>数据同步异常</li>
                                            <li>房源的关联关系有误</li>
                                        </ul>
                                        <p>建议联系技术人员检查数据完整性。</p>
                                    </template>
                                </el-alert>
                            </el-card>
                        </div>
                    </el-col>

                    <!-- 右侧：审核操作 -->
                    <el-col :span="10">
                        <div class="review-actions-panel">
                            <!-- 审核表单 -->
                            <el-card class="review-section" shadow="never">
                                <template #header>
                                    <div class="section-header">
                                        <el-icon>
                                            <Edit />
                                        </el-icon>
                                        <span>审核操作</span>
                                    </div>
                                </template>

                                <!-- 违规检测警告 -->
                                <el-alert v-if="currentReviewItem && checkViolation(currentReviewItem)"
                                    title="⚠️ 违规检测警告" type="error" :closable="false" style="margin-bottom: 20px;">
                                    <template #default>
                                        <p><strong>系统检测到该房源存在严重违规问题：</strong></p>
                                        <ul style="margin: 8px 0; padding-left: 20px;">
                                            <li v-for="reason in getViolationReasons(currentReviewItem)" :key="reason">
                                                {{ getViolationText(reason) }}
                                            </li>
                                        </ul>
                                        <p style="color: #f56c6c; font-weight: 500;">建议拒绝该房源并要求整改！</p>
                                    </template>
                                </el-alert>

                                <!-- 改善建议提示 -->
                                <el-alert v-else-if="currentReviewItem && checkNeedsImprovement(currentReviewItem)"
                                    title="💡 改善建议" type="warning" :closable="false" style="margin-bottom: 20px;">
                                    <template #default>
                                        <p><strong>房源基本符合要求，但建议完善以下内容：</strong></p>
                                        <ul style="margin: 8px 0; padding-left: 20px;">
                                            <li v-for="reason in getImprovementReasons(currentReviewItem)"
                                                :key="reason">
                                                {{ getImprovementText(reason) }}
                                            </li>
                                        </ul>
                                        <p style="color: #e6a23c; font-weight: 500;">可以通过审核，但建议联系房东优化内容。</p>
                                    </template>
                                </el-alert>

                                <el-form :model="reviewForm" label-width="80px" class="review-form">
                                    <el-form-item label="审核决定" required>
                                        <el-radio-group v-model="reviewForm.decision" class="decision-group">
                                            <el-radio value="approve" class="approve-radio">
                                                <el-icon>
                                                    <Check />
                                                </el-icon>
                                                批准上架
                                            </el-radio>
                                            <el-radio value="reject" class="reject-radio">
                                                <el-icon>
                                                    <Close />
                                                </el-icon>
                                                拒绝申请
                                            </el-radio>
                                        </el-radio-group>
                                    </el-form-item>

                                    <el-form-item label="审核原因" required>
                                        <el-input v-model="reviewForm.reason" type="textarea" :rows="4"
                                            placeholder="请详细说明审核原因..." />
                                    </el-form-item>

                                    <el-form-item label="备注说明">
                                        <el-input v-model="reviewForm.notes" type="textarea" :rows="3"
                                            placeholder="可选：添加额外说明..." />
                                    </el-form-item>

                                    <el-form-item>
                                        <el-button type="primary" @click="submitReview" :loading="reviewSubmitting"
                                            size="large">
                                            <el-icon>
                                                <Check />
                                            </el-icon>
                                            提交审核结果
                                        </el-button>
                                        <el-button @click="reviewDialogVisible = false" size="large">
                                            取消
                                        </el-button>
                                    </el-form-item>
                                </el-form>
                            </el-card>

                            <!-- 审核历史 -->
                            <el-card class="review-section" shadow="never">
                                <template #header>
                                    <div class="section-header">
                                        <el-icon>
                                            <Document />
                                        </el-icon>
                                        <span>审核历史</span>
                                        <div style="margin-left: auto; display: flex; gap: 8px;">
                                            <el-button link size="small" @click="refreshAuditHistory">
                                                <el-icon>
                                                    <Refresh />
                                                </el-icon>
                                                刷新
                                            </el-button>
                                            <el-button link size="small"
                                                @click="showDataQualityCheck = !showDataQualityCheck">
                                                <el-icon>
                                                    <Warning />
                                                </el-icon>
                                                数据检查
                                            </el-button>
                                            <el-button link size="small" @click="showRawData = !showRawData">
                                                <el-icon>
                                                    <DataAnalysis />
                                                </el-icon>
                                                原始数据
                                            </el-button>
                                        </div>
                                    </div>
                                </template>

                                <!-- 数据质量检查警告 -->
                                <el-alert v-if="showDataQualityCheck" title="数据质量检查" type="info" :closable="false"
                                    style="margin-bottom: 16px;">
                                    <template #default>
                                        <p>系统已自动过滤以下类型的记录：</p>
                                        <ul style="margin: 8px 0; padding-left: 20px;">
                                            <li>✅ 系统数据迁移记录（操作人：tang）</li>
                                            <li>✅ 测试账户的操作记录</li>
                                            <li>✅ 无效的历史数据</li>
                                        </ul>
                                        <p style="color: #909399; font-size: 12px;">如需查看完整记录，请联系系统管理员。</p>
                                    </template>
                                </el-alert>

                                <!-- 原始数据调试面板 -->
                                <el-alert v-if="showRawData && currentReviewItem" title="🔍 后端原始数据调试" type="warning"
                                    :closable="false" style="margin-bottom: 16px;">
                                    <template #default>
                                        <p style="margin-bottom: 8px;"><strong>房东信息字段检查：</strong></p>
                                        <div
                                            style="background: #f5f5f5; padding: 12px; border-radius: 4px; font-family: monospace; font-size: 12px; max-height: 200px; overflow-y: auto;">
                                            <div><strong>当前获取到的房东信息：</strong></div>
                                            <div>• ownerId: {{ (currentReviewItem as any).ownerId || '❌ 缺失' }}</div>
                                            <div>• ownerName: {{ currentReviewItem.ownerName || '❌ 缺失' }}</div>
                                            <div>• ownerUsername: {{ currentReviewItem.ownerUsername || '❌ 缺失' }}</div>
                                            <div>• ownerPhone: {{ currentReviewItem.ownerPhone || '❌ 缺失' }}</div>
                                            <div>• hostId: {{ currentReviewItem.hostId || '❌ 缺失' }}</div>
                                            <div>• hostName: {{ currentReviewItem.hostName || '❌ 缺失' }}</div>
                                            <div style="margin-top: 8px;"><strong>请检查浏览器控制台日志，查看完整的原始数据。</strong></div>
                                        </div>
                                        <p style="margin-top: 8px; color: #e6a23c; font-size: 12px;">
                                            💡 如果大部分字段显示"❌ 缺失"，说明后端API没有返回完整的房东信息。
                                        </p>
                                    </template>
                                </el-alert>
                                <div class="audit-history" v-if="auditHistory.length > 0">
                                    <el-timeline size="small">
                                        <el-timeline-item v-for="record in auditHistory" :key="record.id"
                                            :type="getTimelineType(record.actionType)">
                                            <div class="history-item">
                                                <div class="history-header">
                                                    <div class="action-info">
                                                        <strong>{{ getActionText(record.actionType) }}</strong>
                                                        <el-tag v-if="record.actionType === 'APPROVE'" type="success"
                                                            size="small">
                                                            已通过
                                                        </el-tag>
                                                        <el-tag v-else-if="record.actionType === 'REJECT'" type="danger"
                                                            size="small">
                                                            已拒绝
                                                        </el-tag>
                                                        <el-tag v-else type="info" size="small">
                                                            {{ record.actionType }}
                                                        </el-tag>
                                                    </div>
                                                    <span class="history-time">{{ formatDateTime(record.createdAt)
                                                        }}</span>
                                                </div>
                                                <div class="history-content">
                                                    <div class="reviewer-info">
                                                        <el-icon>
                                                            <User />
                                                        </el-icon>
                                                        <span><strong>操作人：</strong>{{ record.reviewerName || '未知'
                                                        }}</span>
                                                        <span v-if="record.reviewerId" class="reviewer-id">
                                                            (ID: {{ record.reviewerId }})
                                                        </span>
                                                    </div>
                                                    <div v-if="record.reviewReason" class="reason-info">
                                                        <el-icon>
                                                            <InfoFilled />
                                                        </el-icon>
                                                        <span><strong>原因：</strong>{{ record.reviewReason }}</span>
                                                    </div>
                                                    <div v-if="record.reviewNotes" class="notes-info">
                                                        <el-icon>
                                                            <Document />
                                                        </el-icon>
                                                        <span><strong>备注：</strong>{{ record.reviewNotes }}</span>
                                                    </div>
                                                    <div v-if="record.oldStatus && record.newStatus"
                                                        class="status-change">
                                                        <el-icon>
                                                            <TrendCharts />
                                                        </el-icon>
                                                        <span>状态变化：{{ record.oldStatus }} → {{ record.newStatus
                                                        }}</span>
                                                    </div>
                                                </div>
                                            </div>
                                        </el-timeline-item>
                                    </el-timeline>
                                </div>
                                <div v-else class="no-history">
                                    <div class="empty-state">
                                        <el-icon size="48" color="#c0c4cc">
                                            <Document />
                                        </el-icon>
                                        <p style="margin: 12px 0 4px;">暂无有效审核记录</p>
                                        <p style="color: #909399; font-size: 12px;">
                                            该房源尚未进行过审核，或历史记录已被系统清理
                                        </p>
                                        <el-button link size="small" @click="showDataQualityCheck = true">
                                            查看数据说明
                                        </el-button>
                                    </div>
                                </div>
                            </el-card>
                        </div>
                    </el-col>
                </el-row>
            </div>
        </el-dialog>

        <!-- 快速拒绝对话框 -->
        <el-dialog v-model="quickRejectDialogVisible" title="快速拒绝" width="500px">
            <el-form :model="quickRejectForm" label-width="80px">
                <el-form-item label="拒绝原因" required>
                    <el-select v-model="quickRejectForm.reason" placeholder="选择拒绝原因" style="width: 100%;">
                        <el-option label="图片质量不佳" value="图片质量不佳" />
                        <el-option label="信息不完整" value="信息不完整" />
                        <el-option label="价格不合理" value="价格不合理" />
                        <el-option label="描述与实际不符" value="描述与实际不符" />
                        <el-option label="违反平台规定" value="违反平台规定" />
                        <el-option label="其他原因" value="其他原因" />
                    </el-select>
                </el-form-item>
                <el-form-item label="详细说明">
                    <el-input v-model="quickRejectForm.notes" type="textarea" :rows="3" placeholder="请提供详细说明..." />
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="quickRejectDialogVisible = false">取消</el-button>
                    <el-button type="danger" @click="confirmQuickReject" :loading="quickRejecting">
                        确认拒绝
                    </el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 批量审核对话框 -->
        <el-dialog v-model="batchDialogVisible" title="批量审核" width="800px" :close-on-click-modal="false">
            <div class="batch-review-content">
                <!-- 操作信息展示 -->
                <el-alert title="批量操作提醒" type="warning" :closable="false" style="margin-bottom: 20px;">
                    <template #default>
                        <p>您正在对 <strong>{{ selectedHomestays.length }}</strong> 个房源执行批量审核操作</p>
                        <p>请仔细确认操作类型和审核原因，批量操作不可撤销</p>
                    </template>
                </el-alert>

                <!-- 已选择房源预览 -->
                <el-card shadow="never" style="margin-bottom: 20px;" v-if="!batchSubmitting">
                    <template #header>
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span>已选择房源 ({{ selectedHomestays.length }})</span>
                            <el-button size="small" @click="clearSelection">清空选择</el-button>
                        </div>
                    </template>
                    <div class="selected-homestays-preview">
                        <el-tag v-for="homestay in selectedHomestaysData.slice(0, 10)" :key="homestay.id" closable
                            @close="removeFromSelection(homestay.id)" style="margin: 4px;">
                            {{ homestay.title || homestay.name }} (ID: {{ homestay.id }})
                        </el-tag>
                        <span v-if="selectedHomestaysData.length > 10" style="color: #909399; margin-left: 8px;">
                            等 {{ selectedHomestaysData.length }} 个房源...
                        </span>
                    </div>
                </el-card>

                <!-- 批量操作进度 -->
                <el-card shadow="never" style="margin-bottom: 20px;" v-if="batchSubmitting">
                    <template #header>
                        <span>批量操作进度</span>
                    </template>
                    <div class="batch-progress">
                        <el-progress :percentage="batchProgress.percentage" :status="batchProgress.status"
                            style="margin-bottom: 15px;" />
                        <div class="progress-info">
                            <span>进度：{{ batchProgress.current }} / {{ batchProgress.total }}</span>
                            <span style="margin-left: 20px;">
                                成功：<el-tag type="success" size="small">{{ batchProgress.success }}</el-tag>
                            </span>
                            <span style="margin-left: 10px;">
                                失败：<el-tag type="danger" size="small">{{ batchProgress.failed }}</el-tag>
                            </span>
                        </div>
                        <div v-if="batchProgress.currentItem" class="current-item">
                            正在处理：{{ batchProgress.currentItem }}
                        </div>
                    </div>
                </el-card>

                <!-- 操作结果展示 -->
                <el-card shadow="never" style="margin-bottom: 20px;" v-if="batchResults.length > 0">
                    <template #header>
                        <span>操作结果详情</span>
                    </template>
                    <el-table :data="batchResults" max-height="300" size="small">
                        <el-table-column prop="homestayId" label="房源ID" width="80" />
                        <el-table-column prop="title" label="房源标题" min-width="200" />
                        <el-table-column prop="status" label="处理状态" width="100">
                            <template #default="{ row }">
                                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                                    {{ row.success ? '成功' : '失败' }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="message" label="处理信息" min-width="200" />
                    </el-table>
                </el-card>

                <!-- 表单区域 -->
                <el-form :model="batchForm" label-width="100px" class="batch-form"
                    v-if="!batchSubmitting && batchResults.length === 0">
                    <el-form-item label="批量操作" required>
                        <el-radio-group v-model="batchForm.action" class="batch-action-group">
                            <el-radio value="APPROVE" class="approve-radio">
                                <el-icon>
                                    <Check />
                                </el-icon>
                                批量通过
                            </el-radio>
                            <el-radio value="REJECT" class="reject-radio">
                                <el-icon>
                                    <Close />
                                </el-icon>
                                批量拒绝
                            </el-radio>
                        </el-radio-group>
                    </el-form-item>

                    <el-form-item label="审核原因" required>
                        <div style="margin-bottom: 10px;">
                            <el-button-group size="small">
                                <el-button v-for="reason in commonReasons[batchForm.action] || []" :key="reason"
                                    @click="batchForm.reason = reason" plain>
                                    {{ reason }}
                                </el-button>
                            </el-button-group>
                        </div>
                        <el-input v-model="batchForm.reason" type="textarea" :rows="4" placeholder="请详细说明批量操作的原因..." />
                    </el-form-item>

                    <el-form-item label="备注说明">
                        <el-input v-model="batchForm.notes" type="textarea" :rows="3" placeholder="可选：添加额外说明..." />
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="closeBatchDialog" :disabled="batchSubmitting">
                        {{ batchSubmitting ? '处理中...' : (batchResults.length > 0 ? '关闭' : '取消') }}
                    </el-button>
                    <el-button v-if="!batchSubmitting && batchResults.length === 0" type="primary"
                        @click="submitBatchReview">
                        <el-icon>
                            <Check />
                        </el-icon>
                        确认批量操作
                    </el-button>
                    <el-button v-if="batchResults.length > 0" type="primary" @click="refreshAfterBatch">
                        <el-icon>
                            <Refresh />
                        </el-icon>
                        刷新页面
                    </el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 违规管理对话框 -->
        <el-dialog v-model="violationManagementVisible" title="违规房源管理" width="90%" top="5vh"
            :close-on-click-modal="false">
            <div class="violation-management">
                <!-- 操作工具栏 -->
                <el-card shadow="never" style="margin-bottom: 20px;">
                    <div class="violation-toolbar">
                        <div class="toolbar-left">
                            <el-input v-model="violationSearch" placeholder="搜索房源标题、房东姓名..." style="width: 300px;"
                                clearable @keyup.enter="loadViolationList">
                                <template #prefix>
                                    <el-icon>
                                        <Search />
                                    </el-icon>
                                </template>
                            </el-input>
                            <el-select v-model="violationStatusFilter" placeholder="选择状态" clearable
                                style="width: 120px; margin-left: 10px;" @change="loadViolationList">
                                <el-option label="待处理" value="PENDING" />
                                <el-option label="处理中" value="PROCESSING" />
                                <el-option label="已核实" value="VERIFIED" />
                                <el-option label="已忽略" value="DISMISSED" />
                                <el-option label="已解决" value="RESOLVED" />
                            </el-select>
                            <el-select v-model="violationTypeFilter" placeholder="违规类型" clearable
                                style="width: 120px; margin-left: 10px;" @change="loadViolationList">
                                <el-option label="价格欺诈" value="PRICE_FRAUD" />
                                <el-option label="内容违规" value="CONTENT_VIOLATION" />
                                <el-option label="描述不实" value="DESCRIPTION_VIOLATION" />
                                <el-option label="图片违规" value="IMAGE_VIOLATION" />
                                <el-option label="身份造假" value="IDENTITY_FRAUD" />
                                <el-option label="服务违规" value="SERVICE_VIOLATION" />
                                <el-option label="安全违规" value="SAFETY_VIOLATION" />
                                <el-option label="其他" value="OTHER" />
                            </el-select>
                        </div>
                        <div class="toolbar-right">
                            <el-button type="info" @click="showViolationStatistics">
                                <el-icon>
                                    <DataAnalysis />
                                </el-icon>
                                统计数据
                            </el-button>
                            <el-button type="primary" @click="scanAllHomestays" :loading="scanningViolations">
                                <el-icon>
                                    <Search />
                                </el-icon>
                                全平台违规扫描
                            </el-button>
                            <el-button @click="refreshViolationList">
                                <el-icon>
                                    <Refresh />
                                </el-icon>
                                刷新列表
                            </el-button>
                        </div>
                    </div>
                </el-card>

                <!-- 违规房源列表 -->
                <el-card shadow="never">
                    <template #header>
                        <div class="list-header">
                            <h3>运营中房源违规检测</h3>
                            <span class="count-badge">发现 {{ violationTotal }} 个违规房源</span>
                        </div>
                    </template>

                    <el-table :data="paginatedViolationList" v-loading="loadingViolations" style="width: 100%">
                        <el-table-column prop="id" label="房源ID" width="80" />
                        <el-table-column prop="title" label="房源标题" min-width="200">
                            <template #default="{ row }">
                                <div class="homestay-title">
                                    <span>{{ row.title }}</span>
                                    <el-tag v-if="row.violationType" type="danger" size="small"
                                        style="margin-left: 8px;">
                                        {{ getViolationTypeText(row.violationType) }}
                                    </el-tag>
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column prop="ownerName" label="房东" width="120" />
                        <el-table-column prop="status" label="当前状态" width="100">
                            <template #default="{ row }">
                                <el-tag :type="getStatusTagType(row.status)" size="small">
                                    {{ formatStatus(row.status) }}
                                </el-tag>
                            </template>
                        </el-table-column>
                        <el-table-column prop="violationReason" label="违规原因" min-width="150">
                            <template #default="{ row }">
                                <el-tooltip :content="row.violationDetails" placement="top">
                                    <span>{{ row.violationReason }}</span>
                                </el-tooltip>
                            </template>
                        </el-table-column>
                        <el-table-column prop="reportCount" label="举报次数" width="100">
                            <template #default="{ row }">
                                <el-tag v-if="row.reportCount > 0" type="warning" size="small">
                                    {{ row.reportCount }} 次
                                </el-tag>
                                <span v-else>-</span>
                            </template>
                        </el-table-column>
                        <el-table-column prop="updatedAt" label="最后更新" width="120">
                            <template #default="{ row }">
                                {{ formatDateTime(row.updatedAt) }}
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" width="300" fixed="right">
                            <template #default="{ row }">
                                <el-button-group size="small">
                                    <el-button type="primary" @click="viewHomestayDetail(row)" plain>
                                        <el-icon>
                                            <View />
                                        </el-icon>
                                        查看详情
                                    </el-button>
                                    <el-button type="warning" @click="handleViolation(row, 'suspend')" plain>
                                        <el-icon>
                                            <Warning />
                                        </el-icon>
                                        暂停
                                    </el-button>
                                    <el-button type="danger" @click="handleViolation(row, 'ban')" plain>
                                        <el-icon>
                                            <Close />
                                        </el-icon>
                                        下架
                                    </el-button>
                                    <el-button @click="dismissViolation(row)" plain>
                                        <el-icon>
                                            <Check />
                                        </el-icon>
                                        忽略
                                    </el-button>
                                </el-button-group>
                            </template>
                        </el-table-column>
                    </el-table>

                    <!-- 分页 -->
                    <div class="pagination" style="margin-top: 20px;">
                        <el-pagination v-model:current-page="violationCurrentPage" v-model:page-size="violationPageSize"
                            :page-sizes="[10, 20, 50]" :total="violationTotal"
                            layout="total, sizes, prev, pager, next, jumper" @current-change="loadViolationList"
                            @size-change="loadViolationList" />
                    </div>
                </el-card>
            </div>
        </el-dialog>

        <!-- 违规处理确认对话框 -->
        <el-dialog v-model="violationActionVisible" title="违规处理" width="500px">
            <div v-if="currentViolationItem">
                <el-alert
                    :title="`确认对房源 ${currentViolationItem.title} 执行${getViolationActionText(violationActionType)}操作？`"
                    type="warning" :closable="false" style="margin-bottom: 20px;">
                </el-alert>

                <el-form :model="violationActionForm" label-width="100px">
                    <el-form-item label="处理原因" required>
                        <el-select v-model="violationActionForm.reason" placeholder="选择处理原因" style="width: 100%;">
                            <el-option label="虚假房源信息" value="虚假房源信息" />
                            <el-option label="违规内容描述" value="违规内容描述" />
                            <el-option label="价格欺诈" value="价格欺诈" />
                            <el-option label="图片不实" value="图片不实" />
                            <el-option label="用户举报属实" value="用户举报属实" />
                            <el-option label="违反平台规定" value="违反平台规定" />
                            <el-option label="其他原因" value="其他原因" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="详细说明">
                        <el-input v-model="violationActionForm.notes" type="textarea" :rows="3"
                            placeholder="请详细说明处理原因..." />
                    </el-form-item>
                    <el-form-item v-if="violationActionType === 'suspend'" label="暂停时长">
                        <el-select v-model="violationActionForm.suspendDays" placeholder="选择暂停时长">
                            <el-option label="3天" :value="3" />
                            <el-option label="7天" :value="7" />
                            <el-option label="15天" :value="15" />
                            <el-option label="30天" :value="30" />
                            <el-option label="永久暂停" :value="-1" />
                        </el-select>
                    </el-form-item>
                </el-form>
            </div>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="violationActionVisible = false">取消</el-button>
                    <el-button type="danger" @click="confirmViolationAction" :loading="processingViolation">
                        确认{{ getViolationActionText(violationActionType) }}
                    </el-button>
                </span>
            </template>
        </el-dialog>

        <!-- 违规统计数据对话框 -->
        <el-dialog v-model="violationStatisticsVisible" title="违规统计数据" width="600px">
            <div class="violation-statistics">
                <el-row :gutter="20">
                    <el-col :span="8">
                        <el-card class="stats-card">
                            <template #header>
                                <div class="card-header">
                                    <span>总举报数</span>
                                </div>
                            </template>
                            <div class="stats-number">{{ violationStats.totalReports }}</div>
                        </el-card>
                    </el-col>
                    <el-col :span="8">
                        <el-card class="stats-card">
                            <template #header>
                                <div class="card-header">
                                    <span>待处理</span>
                                </div>
                            </template>
                            <div class="stats-number pending">{{ violationStats.pendingReports }}</div>
                        </el-card>
                    </el-col>
                    <el-col :span="8">
                        <el-card class="stats-card">
                            <template #header>
                                <div class="card-header">
                                    <span>已处理</span>
                                </div>
                            </template>
                            <div class="stats-number processed">{{ violationStats.processedReports }}</div>
                        </el-card>
                    </el-col>
                </el-row>

                <el-row :gutter="20" style="margin-top: 20px;">
                    <el-col :span="12">
                        <el-card>
                            <template #header>
                                <div class="card-header">
                                    <span>举报状态分布</span>
                                </div>
                            </template>
                            <div class="status-distribution">
                                <div v-for="(count, status) in violationStats.statusCounts" :key="status"
                                    class="status-item">
                                    <span class="status-label">{{ getStatusText(String(status)) }}:</span>
                                    <span class="status-count">{{ count }}</span>
                                </div>
                            </div>
                        </el-card>
                    </el-col>
                    <el-col :span="12">
                        <el-card>
                            <template #header>
                                <div class="card-header">
                                    <span>违规类型分布</span>
                                </div>
                            </template>
                            <div class="type-distribution">
                                <div v-for="(count, type) in violationStats.typeCounts" :key="type" class="type-item">
                                    <span class="type-label">{{ getViolationTypeText(String(type)) }}:</span>
                                    <span class="type-count">{{ count }}</span>
                                </div>
                            </div>
                        </el-card>
                    </el-col>
                </el-row>
            </div>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import {
    Edit, Check, TrendCharts, Refresh, List, User, DataAnalysis, Document,
    Search, Picture, Location, Calendar, View, Close, House, Warning, InfoFilled, Loading, ArrowDown
} from '@element-plus/icons-vue'
import {
    getPendingReviewHomestays,
    reviewHomestay,
    getHomestayAuditLogs,
    getHomestayDetailWithOwner,
    batchReviewHomestays,
    getActiveHomestayTypes,
    type AuditLog,
    type ReviewRequest
} from '@/api/homestay'
import {
    getViolationReports,
    scanForViolations,
    getViolationStatistics,
    dismissReport,
    processReport
} from '@/api/violation'
import type { Homestay } from '@/types'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getHomestayMainImage, getHomestayAllImages, ensureImageUrl } from '@/utils/image'

const router = useRouter()
const userStore = useUserStore()



// 待审核房源列表
const pendingHomestays = ref<Homestay[]>([])
const loading = ref(false)

// 搜索和筛选
const searchKeyword = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(12)

// 审核对话框
const reviewDialogVisible = ref(false)
const currentReviewItem = ref<Homestay | null>(null)
const reviewSubmitting = ref(false)

// 审核表单
const reviewForm = reactive({
    decision: '',
    reason: '',
    notes: ''
})

// 审核历史
const auditHistory = ref<AuditLog[]>([])

// 房东信息加载
const loadingOwnerInfo = ref(false)

// 数据质量检查
const showDataQualityCheck = ref(false)

// 原始数据调试
const showRawData = ref(false)

// 快速拒绝
const quickRejectDialogVisible = ref(false)
const quickRejecting = ref(false)
const currentQuickRejectItem = ref<Homestay | null>(null)
const quickRejectForm = reactive({
    reason: '',
    notes: ''
})

// 批量操作
const selectedHomestays = ref<number[]>([])
const selectAll = ref(false)
const batchDialogVisible = ref(false)
const batchSubmitting = ref(false)
const batchForm = reactive({
    action: '',
    reason: '',
    notes: ''
})

// 新增：批量操作扩展功能
const batchProgress = ref({
    current: 0,
    total: 0,
    success: 0,
    failed: 0,
    percentage: 0,
    status: '' as '' | 'success' | 'exception' | 'warning',
    currentItem: ''
})

const batchResults = ref<Array<{
    homestayId: number
    title: string
    success: boolean
    message: string
}>>([])

// 常用审核原因
const commonReasons: Record<string, string[]> = {
    APPROVE: [
        '房源信息完整，符合平台标准',
        '房源品质良好，图片真实',
        '房东信息完善，信誉良好',
        '位置优越，设施齐全'
    ],
    REJECT: [
        '房源信息不完整',
        '图片质量不符合要求',
        '房源描述不准确',
        '位置信息有误',
        '价格设置不合理',
        '不符合安全标准'
    ]
}

// 已选择房源的详细数据
const selectedHomestaysData = computed(() => {
    return pendingHomestays.value.filter(h => selectedHomestays.value.includes(h.id))
})

// 房源类型数据
const homestayTypes = ref<any[]>([])
const homestayTypeMap = ref<Record<string, string>>({})

// 计算属性
const hasSelectedItems = computed(() => selectedHomestays.value.length > 0)

const filteredHomestays = computed(() => {
    let filtered = pendingHomestays.value

    // 状态筛选
    if (filterStatus.value) {
        filtered = filtered.filter(item => item.status === filterStatus.value)
    }

    // 关键词搜索
    if (searchKeyword.value.trim()) {
        const keyword = searchKeyword.value.toLowerCase()
        filtered = filtered.filter(item =>
            (item.title || '').toLowerCase().includes(keyword) ||
            (item.description || '').toLowerCase().includes(keyword) ||
            getLocationText(item).toLowerCase().includes(keyword)
        )
    }

    return filtered
})

const paginatedHomestays = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    const end = start + pageSize.value
    return filteredHomestays.value.slice(start, end)
})

// 方法实现
const refreshData = async () => {
    const loadingInstance = ElLoading.service({
        lock: true,
        text: '正在刷新数据...',
        background: 'rgba(0, 0, 0, 0.3)'
    })
    try {
        await loadPendingHomestays()
        loadingInstance.close()
        ElMessage.success('数据已刷新')
    } catch (error) {
        loadingInstance.close()
        console.error('数据刷新失败:', error)
        ElMessage.error('数据刷新失败')
    }
}

// 加载房源类型数据
const loadHomestayTypes = async () => {
    try {
        console.log('开始加载房源类型数据...')
        const types = await getActiveHomestayTypes()
        homestayTypes.value = types

        // 构建类型映射表
        const typeMap: Record<string, string> = {}
        types.forEach(type => {
            typeMap[type.code] = type.name
        })
        homestayTypeMap.value = typeMap

        console.log('房源类型数据加载完成:', types)
        console.log('类型映射表:', typeMap)
    } catch (error) {
        console.error('加载房源类型失败:', error)
        // 使用默认映射作为后备
        homestayTypeMap.value = {
            'ENTIRE': '整套房源',
            'PRIVATE': '独立房间',
            'SHARED': '合住房间',
            'APARTMENT': '公寓',
            'HOUSE': '别墅',
            'GARDEN_HOUSE': '花园别墅',
            'VILLA': '豪华别墅',
            'STUDIO': '单间公寓',
            'LOFT': '阁楼',
            'TOWNHOUSE': '联排别墅',
            'COTTAGE': '小屋',
            'CABIN': '木屋',
            'BUNGALOW': '平房',
            'PENTHOUSE': '顶层公寓'
        }
    }
}

const loadPendingHomestays = async () => {
    try {
        loading.value = true
        console.log('开始获取待审核房源...')
        const result = await getPendingReviewHomestays(1, 100) // 获取更多数据用于前端筛选
        console.log('获取到的结果:', result)
        pendingHomestays.value = result.list || []
        console.log('待审核房源数量:', pendingHomestays.value.length)
    } catch (error) {
        console.error('获取待审核房源失败:', error)
        ElMessage.error(`获取待审核房源失败: ${(error as Error).message || String(error)}`)
        pendingHomestays.value = []
    } finally {
        loading.value = false
    }
}



const openReviewDialog = async (homestay: Homestay) => {
    try {
        loading.value = true
        // 尝试获取包含完整房东信息的房源详情
        const homestayDetail = await getHomestayDetailWithOwner(homestay.id)
        currentReviewItem.value = homestayDetail

        reviewForm.decision = ''
        reviewForm.reason = ''
        reviewForm.notes = ''

        // 加载审核历史
        await loadAuditHistory(homestay.id)

        reviewDialogVisible.value = true
    } catch (error) {
        console.error('获取房源详情失败:', error)
        ElMessage.error('获取房源详情失败')
    } finally {
        loading.value = false
    }
}

const loadAuditHistory = async (homestayId: number) => {
    try {
        const result = await getHomestayAuditLogs(homestayId, 1, 10)
        // 过滤掉系统自动生成的迁移数据
        auditHistory.value = (result.list || []).filter(record => {
            // 过滤条件：排除明显的测试/迁移数据
            const isSystemMigration =
                record.reviewerName === 'tang' &&
                record.reviewReason === '系统数据迁移' &&
                record.reviewNotes === '从现有数据自动生成的审核记录'

            const isTestData = record.reviewerName?.includes('test') ||
                record.reviewerName?.includes('测试')

            return !isSystemMigration && !isTestData
        })

        console.log('审核历史已加载，条目数:', auditHistory.value.length)
    } catch (error) {
        console.error('获取审核历史失败:', error)
        auditHistory.value = []
    }
}

const refreshAuditHistory = () => {
    if (currentReviewItem.value) {
        loadAuditHistory(currentReviewItem.value.id)
    }
}

// 重新加载房东信息
const loadOwnerInfo = async () => {
    if (!currentReviewItem.value) return

    try {
        loadingOwnerInfo.value = true
        // 尝试使用专用API获取包含房东信息的详情
        const homestayDetail = await getHomestayDetailWithOwner(currentReviewItem.value.id)
        currentReviewItem.value = homestayDetail
        ElMessage.success('房东信息已刷新')
    } catch (error) {
        console.error('获取房东信息失败:', error)
        ElMessage.error('获取房东信息失败')
    } finally {
        loadingOwnerInfo.value = false
    }
}

const submitReview = async () => {
    if (!currentReviewItem.value) return

    if (!reviewForm.decision) {
        ElMessage.warning('请选择审核决定')
        return
    }

    if (!reviewForm.reason.trim()) {
        ElMessage.warning('请填写审核原因')
        return
    }

    try {
        reviewSubmitting.value = true

        const reviewData: ReviewRequest = {
            actionType: reviewForm.decision === 'approve' ? 'APPROVE' : 'REJECT',
            reviewReason: reviewForm.reason,
            reviewNotes: reviewForm.notes
        }

        await reviewHomestay(currentReviewItem.value.id, reviewData)

        ElMessage.success(reviewForm.decision === 'approve' ? '房源已批准' : '房源已拒绝')
        reviewDialogVisible.value = false

        // 刷新数据
        await refreshData()
    } catch (error) {
        console.error('提交审核失败:', error)
        ElMessage.error('提交审核失败')
    } finally {
        reviewSubmitting.value = false
    }
}

const confirmQuickReject = async () => {
    if (!currentQuickRejectItem.value) return

    if (!quickRejectForm.reason) {
        ElMessage.warning('请选择拒绝原因')
        return
    }

    try {
        quickRejecting.value = true

        const reviewData: ReviewRequest = {
            actionType: 'REJECT',
            reviewReason: quickRejectForm.reason,
            reviewNotes: quickRejectForm.notes
        }

        await reviewHomestay(currentQuickRejectItem.value.id, reviewData)
        ElMessage.success('房源已快速拒绝')
        quickRejectDialogVisible.value = false
        await refreshData()
    } catch (error) {
        console.error('快速拒绝失败:', error)
        ElMessage.error('快速拒绝失败')
    } finally {
        quickRejecting.value = false
    }
}

const handleDialogClose = (done: () => void) => {
    if (reviewForm.reason || reviewForm.notes) {
        ElMessageBox.confirm('有未保存的内容，确认关闭？', '提示', {
            confirmButtonText: '确认关闭',
            cancelButtonText: '继续编辑',
            type: 'warning'
        }).then(() => {
            done()
        }).catch(() => {
            // 用户取消关闭
        })
    } else {
        done()
    }
}

// 批量操作方法
const handleSelectAll = (checked: boolean) => {
    if (checked) {
        selectedHomestays.value = paginatedHomestays.value.map(h => h.id)
    } else {
        selectedHomestays.value = []
    }
}

const toggleHomestaySelection = (homestayId: number, checked: boolean) => {
    if (checked) {
        if (!selectedHomestays.value.includes(homestayId)) {
            selectedHomestays.value.push(homestayId)
        }
    } else {
        const index = selectedHomestays.value.indexOf(homestayId)
        if (index > -1) {
            selectedHomestays.value.splice(index, 1)
        }
    }
    handleSelectionChange()
}

const handleSelectionChange = () => {
    selectAll.value = selectedHomestays.value.length === paginatedHomestays.value.length && paginatedHomestays.value.length > 0
}

const startBatchReview = () => {
    if (!hasSelectedItems.value) {
        ElMessage.warning('请先选择要批量审核的房源')
        return
    }
    batchForm.action = ''
    batchForm.reason = ''
    batchForm.notes = ''
    batchDialogVisible.value = true
}

const submitBatchReview = async () => {
    if (!batchForm.action) {
        ElMessage.warning('请选择批量操作')
        return
    }
    if (!batchForm.reason.trim()) {
        ElMessage.warning('请填写审核原因')
        return
    }

    try {
        batchSubmitting.value = true

        // 初始化进度
        batchProgress.value = {
            current: 0,
            total: selectedHomestays.value.length,
            success: 0,
            failed: 0,
            percentage: 0,
            status: '',
            currentItem: ''
        }
        batchResults.value = []

        const reviewData: ReviewRequest = {
            actionType: batchForm.action as 'APPROVE' | 'REJECT',
            reviewReason: batchForm.reason,
            reviewNotes: batchForm.notes
        }

        // 使用后端批量API
        batchProgress.value.current = 1
        batchProgress.value.currentItem = '正在批量处理...'
        batchProgress.value.percentage = 50

        const result = await batchReviewHomestays(selectedHomestays.value, reviewData)

        // 解析后端返回的详细结果
        batchProgress.value.success = result.successCount
        batchProgress.value.failed = result.failureCount
        batchProgress.value.current = selectedHomestays.value.length
        batchProgress.value.percentage = 100

        // 构建详细结果
        for (const resultItem of result.results) {
            const homestay = pendingHomestays.value.find(h => h.id === resultItem.homestayId)
            batchResults.value.push({
                homestayId: resultItem.homestayId,
                title: homestay?.title || `房源 ${resultItem.homestayId}`,
                success: resultItem.success,
                message: resultItem.success
                    ? (batchForm.action === 'APPROVE' ? '审核通过' : '审核拒绝')
                    : (resultItem.error || '处理失败')
            })
        }

        // 设置完成状态
        batchProgress.value.status = batchProgress.value.failed > 0 ? 'exception' : 'success'
        batchProgress.value.currentItem = ''

        ElMessage.success(`批量操作完成：成功 ${batchProgress.value.success} 个，失败 ${batchProgress.value.failed} 个`)

        // 刷新数据但不关闭对话框，让用户查看结果
        await refreshData()

    } catch (error) {
        console.error('批量审核失败:', error)
        ElMessage.error('批量审核失败')
        batchProgress.value.status = 'exception'
    } finally {
        batchSubmitting.value = false
    }
}

// 新增：批量操作相关方法
const clearSelection = () => {
    selectedHomestays.value = []
    selectAll.value = false
}

const removeFromSelection = (homestayId: number) => {
    const index = selectedHomestays.value.indexOf(homestayId)
    if (index > -1) {
        selectedHomestays.value.splice(index, 1)
    }
    handleSelectionChange()
}

const closeBatchDialog = () => {
    if (batchSubmitting.value) {
        ElMessage.warning('批量操作正在进行中，请稍候...')
        return
    }

    batchDialogVisible.value = false

    // 重置状态
    if (batchResults.value.length === 0) {
        batchForm.action = ''
        batchForm.reason = ''
        batchForm.notes = ''
        batchProgress.value = {
            current: 0,
            total: 0,
            success: 0,
            failed: 0,
            percentage: 0,
            status: '',
            currentItem: ''
        }
    }
}

const refreshAfterBatch = () => {
    // 清空选择和重置状态
    selectedHomestays.value = []
    selectAll.value = false
    batchResults.value = []
    batchForm.action = ''
    batchForm.reason = ''
    batchForm.notes = ''
    batchProgress.value = {
        current: 0,
        total: 0,
        success: 0,
        failed: 0,
        percentage: 0,
        status: '',
        currentItem: ''
    }

    batchDialogVisible.value = false
    refreshData()
}

// 快捷选择功能
const handleQuickSelect = (command: string) => {
    const currentHomestays = paginatedHomestays.value

    switch (command) {
        case 'all':
            selectedHomestays.value = currentHomestays.map(h => h.id)
            ElMessage.success(`已选择本页所有 ${currentHomestays.length} 个房源`)
            break

        case 'none':
            selectedHomestays.value = []
            ElMessage.info('已清空选择')
            break

        case 'violation':
            selectedHomestays.value = currentHomestays
                .filter(h => checkViolation(h))
                .map(h => h.id)
            ElMessage.success(`已选择 ${selectedHomestays.value.length} 个疑似违规房源`)
            break

        case 'improvement':
            selectedHomestays.value = currentHomestays
                .filter(h => checkNeedsImprovement(h))
                .map(h => h.id)
            ElMessage.success(`已选择 ${selectedHomestays.value.length} 个待完善房源`)
            break

        case 'normal':
            selectedHomestays.value = currentHomestays
                .filter(h => !checkViolation(h) && !checkNeedsImprovement(h))
                .map(h => h.id)
            ElMessage.success(`已选择 ${selectedHomestays.value.length} 个正常房源`)
            break

        case 'recent':
            // 选择最近24小时提交的房源
            const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000)
            selectedHomestays.value = currentHomestays
                .filter(h => h.updatedAt && new Date(h.updatedAt) > oneDayAgo)
                .map(h => h.id)
            ElMessage.success(`已选择 ${selectedHomestays.value.length} 个最近提交的房源`)
            break

        case 'older':
            // 选择7天前提交的房源
            const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)
            selectedHomestays.value = currentHomestays
                .filter(h => h.updatedAt && new Date(h.updatedAt) < sevenDaysAgo)
                .map(h => h.id)
            ElMessage.success(`已选择 ${selectedHomestays.value.length} 个较早提交的房源`)
            break

        default:
            ElMessage.warning('无效的选择命令')
    }

    // 更新全选状态
    handleSelectionChange()
}

// 导航方法

const viewStatistics = () => {
    router.push('/audit/statistics')
}

const viewAuditHistory = () => {
    router.push('/audit/history')
}

// 违规管理相关数据
const violationManagementVisible = ref(false)
const violationList = ref<any[]>([])
const violationTotal = ref(0)
const loadingViolations = ref(false)
const scanningViolations = ref(false)
const violationSearch = ref('')
const violationStatusFilter = ref('')
const violationTypeFilter = ref('')
const violationCurrentPage = ref(1)
const violationPageSize = ref(20)
const violationStatisticsVisible = ref(false)
const violationStats = ref<any>({
    totalReports: 0,
    pendingReports: 0,
    processedReports: 0,
    statusCounts: {},
    typeCounts: {}
})

// 违规处理对话框
const violationActionVisible = ref(false)
const currentViolationItem = ref<any>(null)
const violationActionType = ref<'suspend' | 'ban' | 'dismiss'>('suspend')
const processingViolation = ref(false)
const violationActionForm = reactive({
    reason: '',
    notes: '',
    suspendDays: 7
})

// 违规列表计算属性
const filteredViolationList = computed(() => {
    if (!violationSearch.value) return violationList.value
    const keyword = violationSearch.value.toLowerCase()
    return violationList.value.filter(item =>
        item.title?.toLowerCase().includes(keyword) ||
        item.ownerName?.toLowerCase().includes(keyword)
    )
})

const paginatedViolationList = computed(() => {
    // 由于我们现在从API获取分页数据，直接返回过滤后的列表
    return filteredViolationList.value
})

// 打开违规管理对话框
const openViolationManagement = () => {
    violationManagementVisible.value = true
    loadViolationList()
}

// 加载违规房源列表
const loadViolationList = async () => {
    try {
        loadingViolations.value = true

        // 调用真实的违规举报API
        const response = await getViolationReports({
            page: violationCurrentPage.value - 1,
            size: violationPageSize.value,
            keyword: violationSearch.value,
            status: violationStatusFilter.value,
            violationType: violationTypeFilter.value
        })

        if (response.list.length > 0) {
            // 转换数据格式以匹配前端组件
            violationList.value = response.list.map((item: any) => ({
                id: item.id || item.homestayId,
                title: item.homestayTitle || item.title,
                ownerName: item.ownerName,
                ownerUsername: item.ownerUsername,
                status: item.homestayStatus || item.status,
                violationType: item.violationType,
                violationReason: item.violationTypeName || item.reason,
                violationDetails: item.details,
                reportCount: item.reportCount || 0,
                updatedAt: item.updatedAt || item.createdAt,
                reporterName: item.reporterName,
                processorName: item.processorName,
                processedAt: item.processedAt,
                reportStatus: item.status
            }))

            // 更新总数
            violationTotal.value = response.total || 0
        } else {
            violationList.value = []
            violationTotal.value = 0
        }

        console.log('违规房源列表加载完成:', violationList.value)

    } catch (error) {
        console.error('加载违规房源列表失败:', error)
        ElMessage.error('加载违规房源列表失败')

        // 如果API失败，使用模拟数据作为备用
        const mockViolationData = [
            {
                id: 1001,
                title: '豪华海景房 - 限时特惠仅需1元！',
                ownerName: '张三',
                status: 'ACTIVE',
                violationType: 'PRICE_FRAUD',
                violationReason: '价格异常',
                violationDetails: '房源价格设置为1元，明显低于市场价格，疑似价格欺诈',
                reportCount: 3,
                updatedAt: new Date().toISOString()
            },
            {
                id: 1002,
                title: '市中心精装公寓',
                ownerName: '李四',
                status: 'ACTIVE',
                violationType: 'CONTENT_VIOLATION',
                violationReason: '图片不实',
                violationDetails: '用户举报房源图片与实际不符，存在虚假宣传',
                reportCount: 2,
                updatedAt: new Date().toISOString()
            },
            {
                id: 1003,
                title: '温馨家庭房',
                ownerName: '王五',
                status: 'ACTIVE',
                violationType: 'DESCRIPTION_VIOLATION',
                violationReason: '描述违规',
                violationDetails: '房源描述中包含违规关键词',
                reportCount: 1,
                updatedAt: new Date().toISOString()
            }
        ]
        violationList.value = mockViolationData
        violationTotal.value = mockViolationData.length
    } finally {
        loadingViolations.value = false
    }
}

// 全平台违规扫描
const scanAllHomestays = async () => {
    try {
        scanningViolations.value = true
        ElMessage.info('开始全平台违规扫描...')

        // 调用真实的违规扫描API
        const response = await scanForViolations()

        if (response.length > 0) {
            ElMessage.success(`违规扫描完成，发现 ${response.length} 个潜在违规房源`)

            // 将扫描结果添加到违规列表中
            const scanResults = response.map((item: any) => ({
                id: item.id,
                title: item.title,
                ownerName: item.ownerName,
                status: 'ACTIVE',
                violationType: item.violationType,
                violationReason: item.reason,
                violationDetails: item.details,
                reportCount: 1, // 系统扫描发现的标记为1次
                updatedAt: new Date().toISOString(),
                isSystemDetected: true // 标记为系统检测
            }))

            // 合并到现有列表
            violationList.value = [...scanResults, ...violationList.value]
            violationTotal.value += scanResults.length
        } else {
            ElMessage.info('扫描完成，未发现新的违规房源')
        }

        // 刷新违规列表以获取最新数据
        await loadViolationList()

    } catch (error) {
        console.error('违规扫描失败:', error)
        ElMessage.error('违规扫描失败')
    } finally {
        scanningViolations.value = false
    }
}

// 刷新违规列表
const refreshViolationList = () => {
    loadViolationList()
}

// 显示违规统计数据
const showViolationStatistics = async () => {
    try {
        const response = await getViolationStatistics()

        if (response) {
            violationStats.value = response
            violationStatisticsVisible.value = true
        }
    } catch (error) {
        console.error('获取违规统计数据失败:', error)
        ElMessage.error('获取统计数据失败')
    }
}

// 查看房源详情
const viewHomestayDetail = (violation: any) => {
    // 打开房源详情页面或对话框
    window.open(`/homestay/detail/${violation.id}`, '_blank')
}

// 处理违规房源
const handleViolation = (violation: any, actionType: 'suspend' | 'ban') => {
    currentViolationItem.value = violation
    violationActionType.value = actionType
    violationActionForm.reason = ''
    violationActionForm.notes = ''
    violationActionForm.suspendDays = 7
    violationActionVisible.value = true
}

// 忽略违规
const dismissViolation = async (violation: any) => {
    try {
        await ElMessageBox.confirm('确认忽略该违规举报？此操作不可撤销。', '确认忽略', {
            confirmButtonText: '确认忽略',
            cancelButtonText: '取消',
            type: 'warning'
        })

        // 调用真实的忽略违规API
        await dismissReport(violation.id, {
            reason: '管理员判断为误报或不属于违规'
        })

        ElMessage.success('已忽略该违规举报')
        await loadViolationList()

    } catch (error) {
        if (error !== 'cancel') {
            console.error('忽略违规失败:', error)
            ElMessage.error('忽略违规失败')
        }
    }
}

// 确认违规处理
const confirmViolationAction = async () => {
    if (!violationActionForm.reason) {
        ElMessage.warning('请选择处理原因')
        return
    }

    try {
        processingViolation.value = true

        // 调用真实的违规处理API

        // 映射前端动作类型到后端枚举
        const actionTypeMap: Record<string, string> = {
            'suspend': 'SUSPEND',
            'ban': 'BAN',
            'dismiss': 'DISMISS'
        }

        const actionData = {
            actionType: actionTypeMap[violationActionType.value] || 'WARNING',
            reason: violationActionForm.reason,
            notes: violationActionForm.notes,
            suspendDays: violationActionForm.suspendDays
        }

        await processReport(currentViolationItem.value.id, actionData)

        const actionText = getViolationActionText(violationActionType.value)
        ElMessage.success(`违规举报已${actionText}`)

        violationActionVisible.value = false
        await loadViolationList()

    } catch (error) {
        console.error('处理违规失败:', error)
        ElMessage.error('处理违规失败')
    } finally {
        processingViolation.value = false
    }
}

// 获取违规类型文本
const getViolationTypeText = (type: string): string => {
    const typeMap: Record<string, string> = {
        'PRICE_FRAUD': '价格欺诈',
        'CONTENT_VIOLATION': '内容违规',
        'DESCRIPTION_VIOLATION': '描述违规',
        'IMAGE_VIOLATION': '图片违规',
        'USER_REPORT': '用户举报'
    }
    return typeMap[type] || type
}

// 获取违规操作文本
const getViolationActionText = (actionType: string): string => {
    const actionMap: Record<string, string> = {
        'suspend': '暂停',
        'ban': '下架',
        'dismiss': '忽略'
    }
    return actionMap[actionType] || actionType
}

// 获取状态文本
const getStatusText = (status: string): string => {
    const statusMap: Record<string, string> = {
        'PENDING': '待处理',
        'PROCESSING': '处理中',
        'VERIFIED': '已核实',
        'DISMISSED': '已忽略',
        'RESOLVED': '已解决'
    }
    return statusMap[status] || status
}

// 违规检测方法 - 优化版本
const checkViolation = (homestay: Homestay): boolean => {
    // 只检测严重违规问题
    const violations = []

    // 1. 检查标题是否包含严重违规关键词
    const seriousViolations = ['欺诈', '假冒', '色情', '暴力', '毒品', '赌博', '黄赌毒']
    if (homestay.title && seriousViolations.some(word => homestay.title!.includes(word))) {
        violations.push('标题严重违规')
    }

    // 2. 检查价格是否明显异常（可能是欺诈）
    if (homestay.price && (homestay.price < 1 || homestay.price > 100000)) {
        violations.push('价格明显异常')
    }

    // 3. 检查描述是否包含严重违规内容
    if (homestay.description) {
        const seriousDescViolations = ['假证', '代开发票', '洗钱', '政治敏感', '反动', '分裂']
        if (seriousDescViolations.some(word => homestay.description!.includes(word))) {
            violations.push('描述严重违规')
        }
    }

    // 4. 检查是否完全没有图片（严重问题）
    const hasCover = homestay.coverImage && homestay.coverImage.trim() !== ''
    const hasGallery = homestay.images && homestay.images.length > 0

    if (!hasCover && !hasGallery) {
        violations.push('完全没有图片')
    }

    return violations.length > 0
}

// 检查需要完善的内容
const checkNeedsImprovement = (homestay: Homestay): boolean => {
    const improvements = []

    // 1. 检查图片完整性
    const hasCover = homestay.coverImage && homestay.coverImage.trim() !== ''
    const hasGallery = homestay.images && homestay.images.length > 0
    const galleryCount = homestay.images ? homestay.images.length : 0

    if (hasCover && !hasGallery) {
        improvements.push('建议添加房源照片集')
    } else if (!hasCover && hasGallery) {
        improvements.push('建议设置封面图片')
    } else if (hasCover && galleryCount < 2) {
        improvements.push('建议添加更多房源照片')
    }

    // 2. 检查基本信息完整性
    if (!homestay.description || homestay.description.length < 20) {
        improvements.push('建议完善房源描述')
    }

    // 3. 检查价格合理性（温和提醒）
    if (homestay.price && (homestay.price < 10 || homestay.price > 50000)) {
        improvements.push('建议核实价格设置')
    }

    return improvements.length > 0
}

// 获取违规原因列表
const getViolationReasons = (homestay: Homestay): string[] => {
    const violations = []

    // 1. 检查标题是否包含严重违规关键词
    const seriousViolations = ['欺诈', '假冒', '色情', '暴力', '毒品', '赌博', '黄赌毒']
    if (homestay.title && seriousViolations.some(word => homestay.title!.includes(word))) {
        violations.push('标题严重违规')
    }

    // 2. 检查价格是否明显异常
    if (homestay.price && (homestay.price < 1 || homestay.price > 100000)) {
        violations.push('价格明显异常')
    }

    // 3. 检查描述是否包含严重违规内容
    if (homestay.description) {
        const seriousDescViolations = ['假证', '代开发票', '洗钱', '政治敏感', '反动', '分裂']
        if (seriousDescViolations.some(word => homestay.description!.includes(word))) {
            violations.push('描述严重违规')
        }
    }

    // 4. 检查是否完全没有图片
    const hasCover = homestay.coverImage && homestay.coverImage.trim() !== ''
    const hasGallery = homestay.images && homestay.images.length > 0

    if (!hasCover && !hasGallery) {
        violations.push('完全没有图片')
    }

    return violations
}

// 获取改善建议列表
const getImprovementReasons = (homestay: Homestay): string[] => {
    const improvements = []

    // 1. 检查图片完整性
    const hasCover = homestay.coverImage && homestay.coverImage.trim() !== ''
    const hasGallery = homestay.images && homestay.images.length > 0
    const galleryCount = homestay.images ? homestay.images.length : 0

    if (hasCover && !hasGallery) {
        improvements.push('建议添加房源照片集')
    } else if (!hasCover && hasGallery) {
        improvements.push('建议设置封面图片')
    } else if (hasCover && galleryCount < 2) {
        improvements.push('建议添加更多房源照片')
    }

    // 2. 检查基本信息完整性
    if (!homestay.description || homestay.description.length < 20) {
        improvements.push('建议完善房源描述')
    }

    // 3. 检查价格合理性
    if (homestay.price && (homestay.price < 10 || homestay.price > 50000)) {
        improvements.push('建议核实价格设置')
    }

    return improvements
}

// 获取违规原因的友好文本
const getViolationText = (reason: string): string => {
    const textMap: Record<string, string> = {
        '标题严重违规': '标题包含严重违规关键词',
        '价格明显异常': '价格设置明显异常（可能存在欺诈）',
        '描述严重违规': '描述包含严重违规内容',
        '完全没有图片': '房源完全没有图片（严重影响可信度）'
    }
    return textMap[reason] || reason
}

// 获取改善建议的友好文本
const getImprovementText = (reason: string): string => {
    const textMap: Record<string, string> = {
        '建议添加房源照片集': '已有封面图，建议增加房源照片集展示更多角度',
        '建议设置封面图片': '已有照片集，建议设置一张主要封面图片',
        '建议添加更多房源照片': '建议增加房源照片，让用户更全面了解房源',
        '建议完善房源描述': '房源描述过于简单，建议添加更详细的介绍',
        '建议核实价格设置': '价格设置可能不够合理，建议核实确认'
    }
    return textMap[reason] || reason
}

// 工具方法 - 优化的图片处理
const getHomestayImage = (homestay: Homestay): string => {
    const imageUrl = getHomestayMainImage(homestay)
    // 确保图片URL是完整的
    return ensureImageUrl(imageUrl)
}

const getHomestayDisplayImages = (homestay: Homestay): string[] => {
    return getHomestayAllImages(homestay).map(img => ensureImageUrl(img))
}

const getLocationText = (homestay: Homestay): string => {
    // 简化的地址显示
    const parts = []
    if (homestay.provinceName) parts.push(homestay.provinceName)
    if (homestay.cityName) parts.push(homestay.cityName)
    if (homestay.districtName) parts.push(homestay.districtName)
    if (homestay.addressDetail) parts.push(homestay.addressDetail)
    return parts.join(' ') || '位置信息不完整'
}

const formatStatus = (status: string): string => {
    const statusMap: Record<string, string> = {
        'DRAFT': '草稿',
        'PENDING': '待审核',
        'ACTIVE': '已上架',
        'INACTIVE': '已下架',
        'REJECTED': '已拒绝',
        'SUSPENDED': '已暂停'
    }
    return statusMap[status] || status
}

// 房源类型转换（使用动态数据）
const formatHomestayType = (type: string): string => {
    return homestayTypeMap.value[type] || type || '未指定'
}

const getStatusTagType = (status: string): 'success' | 'info' | 'warning' | 'danger' | '' => {
    const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger' | ''> = {
        'PENDING': 'warning',
        'ACTIVE': 'success',
        'REJECTED': 'danger',
        'SUSPENDED': 'info'
    }
    return typeMap[status] || ''
}

const formatDateTime = (dateTime: string | null | undefined): string => {
    if (!dateTime) return '未知时间'
    try {
        const date = new Date(dateTime)
        return date.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        })
    } catch (error) {
        return '时间格式错误'
    }
}

const getTimelineType = (actionType: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
    const typeMap: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
        'SUBMIT': 'primary',
        'APPROVE': 'success',
        'REJECT': 'danger',
        'REVIEW': 'warning'
    }
    return typeMap[actionType] || 'info'
}

const getActionText = (actionType: string): string => {
    const textMap: Record<string, string> = {
        'SUBMIT': '提交审核',
        'APPROVE': '批准上架',
        'REJECT': '拒绝申请',
        'REVIEW': '开始审核',
        'RESUBMIT': '重新提交'
    }
    return textMap[actionType] || actionType
}

// 自动刷新定时器
const autoRefreshInterval = ref<NodeJS.Timeout | null>(null)
const autoRefreshEnabled = ref(true)

// 启动自动刷新
const startAutoRefresh = () => {
    if (autoRefreshInterval.value) {
        clearInterval(autoRefreshInterval.value)
    }

    if (autoRefreshEnabled.value) {
        autoRefreshInterval.value = setInterval(() => {
            console.log('自动刷新房源数据...')
            loadPendingHomestays()
        }, 300000) // 每5分钟刷新一次房源数据
    }
}

// 停止自动刷新
const stopAutoRefresh = () => {
    if (autoRefreshInterval.value) {
        clearInterval(autoRefreshInterval.value)
        autoRefreshInterval.value = null
    }
}

// 切换自动刷新
const toggleAutoRefresh = () => {
    autoRefreshEnabled.value = !autoRefreshEnabled.value
    if (autoRefreshEnabled.value) {
        startAutoRefresh()
        ElMessage.success('已开启自动刷新')
    } else {
        stopAutoRefresh()
        ElMessage.info('已关闭自动刷新')
    }
}

// 初始化
onMounted(() => {
    console.log('审核工作台页面已挂载')

    // 检查用户登录状态
    console.log('用户登录状态:', userStore.isLoggedIn)
    console.log('用户token:', userStore.token ? '有token' : '无token')
    console.log('用户信息:', userStore.userInfo)

    if (!userStore.isLoggedIn) {
        ElMessage.error('请先登录')
        router.push('/login')
        return
    }

    // 加载基础数据
    loadHomestayTypes() // 加载房源类型数据
    loadPendingHomestays() // 加载待审核房源
    startAutoRefresh() // 启动自动刷新
})

// 页面卸载时清理定时器
import { onUnmounted } from 'vue'
onUnmounted(() => {
    stopAutoRefresh()
})
</script>

<style scoped lang="scss">
.audit-workbench {
    padding: 20px;
    min-height: 100vh;
    background-color: #f5f7fa;



    .workbench-toolbar {
        margin-bottom: 24px;
        border-radius: 12px;
        border: none;

        .toolbar-content {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 20px;
            padding: 8px 0;
        }

        .main-actions {
            .batch-btn {
                font-weight: 600;
                height: 42px;
                border-radius: 8px;
            }
        }

        .search-filters {
            flex: 1;
            display: flex;
            justify-content: center;

            .el-input {
                max-width: 400px;
            }
        }

        .system-actions {
            display: flex;
            align-items: center;
            gap: 12px;

            .el-button {
                height: 36px;
                border-radius: 6px;
            }
        }
    }

    .pending-list {
        border-radius: 12px;
        border: none;

        .list-header {
            display: flex;
            justify-content: space-between;
            align-items: center;

            .header-left {
                display: flex;
                align-items: center;
                gap: 12px;

                h3 {
                    margin: 0;
                    font-size: 18px;
                    font-weight: 600;
                    color: #303133;
                }

                .count-badge {
                    background: #e6f7ff;
                    color: #1890ff;
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 12px;
                    font-weight: 500;
                }
            }

            .header-right {
                .el-checkbox {
                    font-weight: 500;
                }
            }
        }

        .empty-state {
            padding: 60px 0;
        }

        .homestay-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
            gap: 16px;
            padding: 16px 0;

            .homestay-card {
                position: relative;

                .selection-checkbox {
                    position: absolute;
                    top: 8px;
                    right: 8px;
                    z-index: 10;
                    background: rgba(255, 255, 255, 0.9);
                    border-radius: 4px;
                    padding: 4px;
                    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
                }

                .el-card {
                    border-radius: 12px;
                    border: 1px solid #e4e7ed;
                    transition: all 0.3s ease;
                    cursor: pointer;

                    &:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
                        border-color: var(--el-color-primary);
                    }

                    &.selected {
                        border-color: #67c23a;
                        box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
                    }
                }

                .homestay-content {
                    padding: 8px;

                    .homestay-image {
                        position: relative;
                        height: 140px;
                        margin-bottom: 12px;

                        .el-image {
                            width: 100%;
                            height: 100%;
                            border-radius: 8px;
                        }

                        .image-placeholder,
                        .image-error {
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            justify-content: center;
                            height: 100%;
                            background-color: #f5f7fa;
                            color: #909399;
                            border-radius: 8px;

                            .el-icon {
                                font-size: 32px;
                                margin-bottom: 8px;
                            }
                        }

                        .status-badge {
                            position: absolute;
                            bottom: 8px;
                            right: 8px;
                        }

                        .violation-badge {
                            position: absolute;
                            top: 8px;
                            left: 8px;
                            animation: pulse 2s infinite;
                            z-index: 9;
                        }

                        .improvement-badge {
                            position: absolute;
                            top: 8px;
                            left: 8px;
                            z-index: 9;
                        }
                    }

                    .homestay-info {
                        .title {
                            font-size: 15px;
                            font-weight: 600;
                            color: #303133;
                            margin-bottom: 6px;
                            display: -webkit-box;
                            -webkit-line-clamp: 1;
                            -webkit-box-orient: vertical;
                            overflow: hidden;
                        }

                        .location {
                            display: flex;
                            align-items: center;
                            color: #909399;
                            font-size: 13px;
                            margin-bottom: 6px;

                            .el-icon {
                                margin-right: 4px;
                            }
                        }

                        .price {
                            margin-bottom: 6px;

                            .price-label {
                                color: #e6a23c;
                                font-size: 13px;
                            }

                            .price-value {
                                color: #e6a23c;
                                font-size: 18px;
                                font-weight: bold;
                            }

                            .price-unit {
                                color: #909399;
                                font-size: 11px;
                            }
                        }

                        .meta-info {
                            .submit-time {
                                display: flex;
                                align-items: center;
                                color: #909399;
                                font-size: 11px;

                                .el-icon {
                                    margin-right: 4px;
                                }
                            }
                        }
                    }
                }

                .card-actions {
                    margin-top: 12px;
                    padding: 0 8px 8px 8px;
                    display: flex;
                    justify-content: center;

                    .review-btn {
                        width: 100%;
                        height: 36px;
                        font-weight: 600;
                        border-radius: 6px;
                    }
                }
            }
        }

        .pagination {
            display: flex;
            justify-content: center;
            margin-top: 32px;
        }
    }
}

// 审核对话框样式
.review-dialog-content {
    .review-sections {
        .review-section {
            margin-bottom: 20px;
            border-radius: 8px;

            .section-header {
                display: flex;
                align-items: center;
                font-weight: 600;

                .el-icon {
                    margin-right: 8px;
                    color: var(--el-color-primary);
                }

                .el-tag {
                    margin-left: auto;
                }

                .image-count {
                    margin-left: 8px;
                    color: #909399;
                    font-size: 12px;
                    font-weight: normal;
                }
            }

            .cover-image-container {
                .cover-image {
                    width: 100%;
                    height: 160px;
                    border-radius: 8px;
                    border: 2px solid var(--el-color-primary);
                    object-fit: cover;
                }

                .image-error {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    height: 160px;
                    background-color: #f5f7fa;
                    color: #909399;
                    border-radius: 8px;
                    border: 2px dashed #d3d3d3;
                    font-size: 14px;

                    .el-icon {
                        font-size: 32px;
                        margin-bottom: 8px;
                    }
                }
            }

            .image-review-grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
                gap: 12px;

                .image-item {
                    position: relative;

                    .review-image {
                        width: 100%;
                        height: 100px;
                        border-radius: 6px;
                    }

                    .primary-badge {
                        position: absolute;
                        top: 4px;
                        left: 4px;
                        background: rgba(0, 0, 0, 0.7);
                        color: white;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-size: 10px;
                    }

                    .cover-badge {
                        position: absolute;
                        top: 4px;
                        left: 4px;
                        background: #67c23a;
                        color: white;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-size: 10px;
                        font-weight: bold;
                    }

                    .image-error {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100px;
                        background-color: #f5f7fa;
                        color: #909399;
                        border-radius: 6px;
                        font-size: 12px;

                        .el-icon {
                            font-size: 24px;
                            margin-bottom: 4px;
                        }
                    }
                }
            }

            .no-images {
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                height: 120px;
                color: #909399;

                .el-icon {
                    font-size: 32px;
                    margin-bottom: 8px;
                }
            }
        }
    }

    .review-actions-panel {
        .review-form {
            .decision-group {
                display: flex;
                flex-direction: column;
                gap: 12px;

                .el-radio {
                    margin: 0;
                    padding: 12px;
                    border: 2px solid #e4e7ed;
                    border-radius: 8px;
                    transition: all 0.3s ease;

                    &:hover {
                        border-color: var(--el-color-primary);
                    }

                    &.is-checked {
                        border-color: #67c23a;
                        background-color: #f0f9ff;

                        &.approve-radio.is-checked {
                            border-color: #67c23a;
                            background-color: #f0f9ff;
                        }

                        &.reject-radio.is-checked {
                            border-color: #f56c6c;
                            background-color: #fef0f0;
                        }
                    }

                    .el-icon {
                        margin-right: 8px;
                    }
                }
            }
        }

        .audit-history {
            max-height: 300px;
            overflow-y: auto;

            .history-item {
                .history-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 8px;

                    .action-info {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }

                    .history-time {
                        font-size: 12px;
                        color: #909399;
                    }
                }

                .history-content {
                    >div {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                        margin: 6px 0;
                        padding: 4px 0;
                    }

                    .reviewer-info,
                    .reason-info,
                    .notes-info,
                    .status-change {
                        border-left: 3px solid #e4e7ed;
                        padding-left: 8px;
                        margin-left: 8px;
                    }

                    .reviewer-info {
                        border-left-color: var(--el-color-primary);
                    }

                    .reason-info {
                        border-left-color: #e6a23c;
                    }

                    .notes-info {
                        border-left-color: #909399;
                    }

                    .status-change {
                        border-left-color: #67c23a;
                    }

                    .reviewer-id {
                        color: #909399;
                        font-size: 12px;
                    }
                }
            }
        }

        .no-history {
            .empty-state {
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                padding: 32px 20px;
                color: #909399;

                p {
                    margin: 8px 0;
                }
            }
        }
    }
}

// 批量操作样式
.batch-review-content {
    .selected-homestays-preview {
        max-height: 120px;
        overflow-y: auto;
        padding: 8px 0;
    }

    .batch-progress {
        .progress-info {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 10px;
            font-size: 14px;
            color: #606266;
        }

        .current-item {
            color: var(--el-color-primary);
            font-size: 13px;
            margin-top: 8px;
            padding: 8px 12px;
            background: #f0f9ff;
            border-radius: 4px;
            border-left: 3px solid var(--el-color-primary);
        }
    }

    .batch-form {
        .batch-action-group {
            display: flex;
            flex-direction: column;
            gap: 12px;

            .el-radio {
                margin: 0;
                padding: 12px;
                border: 2px solid #e4e7ed;
                border-radius: 8px;
                transition: all 0.3s ease;

                &:hover {
                    border-color: var(--el-color-primary);
                }

                &.is-checked {
                    &.approve-radio {
                        border-color: #67c23a;
                        background-color: #f0f9ff;
                    }

                    &.reject-radio {
                        border-color: #f56c6c;
                        background-color: #fef0f0;
                    }
                }

                .el-icon {
                    margin-right: 8px;
                }
            }
        }
    }
}

// 选中状态样式
.homestay-card {
    position: relative;

    .selection-checkbox {
        position: absolute;
        top: 8px;
        left: 8px;
        z-index: 10;
        background: rgba(255, 255, 255, 0.9);
        border-radius: 4px;
        padding: 4px;
    }

    .el-card {
        &.selected {
            border: 2px solid var(--el-color-primary);
            box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
        }
    }
}

// 违规管理样式
.violation-management {
    .violation-toolbar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 20px;

        .toolbar-left {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .toolbar-right {
            display: flex;
            align-items: center;
            gap: 12px;
        }
    }

    .homestay-title {
        display: flex;
        align-items: center;
        gap: 8px;
    }

    .list-header {
        display: flex;
        justify-content: space-between;
        align-items: center;

        h3 {
            margin: 0;
            font-size: 16px;
            font-weight: 600;
            color: #303133;
        }

        .count-badge {
            background: #fff2e8;
            color: #e6a23c;
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 500;
        }
    }

    .pagination {
        display: flex;
        justify-content: center;
        margin-top: 20px;
    }
}

// 违规警告动画
@keyframes pulse {
    0% {
        transform: scale(1);
        opacity: 1;
    }

    50% {
        transform: scale(1.05);
        opacity: 0.7;
    }

    100% {
        transform: scale(1);
        opacity: 1;
    }
}
</style>
