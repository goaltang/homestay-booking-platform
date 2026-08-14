<template>
  <el-dialog
    :model-value="modelValue"
    title="房源审核记录"
    width="70%"
    top="5vh"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @open="refresh"
  >
    <div v-if="homestay" class="audit-history-content">
      <!-- 房源基本信息 -->
      <el-card class="homestay-info-card" shadow="never" style="margin-bottom: 20px;">
        <template #header>
          <div class="card-header">
            <el-icon><House /></el-icon>
            <span>房源信息</span>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="房源ID">{{ homestay.id }}</el-descriptions-item>
          <el-descriptions-item label="房源名称">
            <strong>{{ homestay.title }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusType(homestay.status)">
              {{ getStatusText(homestay.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="房源类型">{{ typeLabel }}</el-descriptions-item>
          <el-descriptions-item label="价格">¥{{ homestay.price }}/晚</el-descriptions-item>
          <el-descriptions-item label="最大入住">{{ homestay.maxGuests }}人</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 审核记录 -->
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><Document /></el-icon>
            <span>审核历史记录</span>
            <div style="margin-left: auto; display: flex; gap: 8px;">
              <el-button type="text" size="small" @click="refresh">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
              <el-button type="text" size="small" @click="showDataQualityInfo = !showDataQualityInfo">
                <el-icon><InfoFilled /></el-icon>
                数据说明
              </el-button>
            </div>
          </div>
        </template>

        <el-alert v-if="showDataQualityInfo" title="数据说明" type="info" :closable="false"
          style="margin-bottom: 16px;">
          <template #default>
            <p>系统已自动过滤以下类型的记录：</p>
            <ul style="margin: 8px 0; padding-left: 20px;">
              <li>✅ 系统数据迁移记录</li>
              <li>✅ 测试账户的操作记录</li>
              <li>✅ 无效的历史数据</li>
            </ul>
            <p style="color: #909399; font-size: 12px;">只显示真实有效的审核操作记录。</p>
          </template>
        </el-alert>

        <div v-loading="loadingAuditHistory">
          <div v-if="auditRecords.length > 0" class="audit-timeline">
            <el-timeline>
              <el-timeline-item v-for="record in auditRecords" :key="record.id"
                :type="getTimelineType(record.actionType)"
                :timestamp="formatDateTime(record.createdAt)" placement="top">
                <div class="timeline-item">
                  <div class="timeline-header">
                    <div class="action-info">
                      <strong>{{ getActionText(record.actionType) }}</strong>
                      <el-tag v-if="record.actionType === 'APPROVE'" type="success" size="small">已通过</el-tag>
                      <el-tag v-else-if="record.actionType === 'REJECT'" type="danger" size="small">已拒绝</el-tag>
                      <el-tag v-else-if="record.actionType === 'SUBMIT'" type="primary" size="small">已提交</el-tag>
                      <el-tag v-else-if="record.actionType === 'RESUBMIT'" type="primary" size="small">重新提交</el-tag>
                      <el-tag v-else-if="record.actionType === 'WITHDRAW'" type="warning" size="small">已撤回</el-tag>
                      <el-tag v-else type="info" size="small">{{ record.actionType }}</el-tag>
                    </div>
                  </div>
                  <div class="timeline-content">
                    <div class="reviewer-info" v-if="record.reviewerName">
                      <el-icon><User /></el-icon>
                      <span><strong>操作人：</strong>{{ record.reviewerName }}</span>
                      <span v-if="record.reviewerId" class="reviewer-id">(ID: {{ record.reviewerId }})</span>
                    </div>
                    <div v-if="record.reviewReason" class="reason-info">
                      <el-icon><InfoFilled /></el-icon>
                      <span><strong>原因：</strong>{{ record.reviewReason }}</span>
                    </div>
                    <div v-if="record.reviewNotes" class="notes-info">
                      <el-icon><Document /></el-icon>
                      <span><strong>备注：</strong>{{ record.reviewNotes }}</span>
                    </div>
                    <div v-if="record.oldStatus && record.newStatus" class="status-change">
                      <el-icon><TrendCharts /></el-icon>
                      <span><strong>状态变化：</strong>{{ getStatusText(record.oldStatus) }} → {{ getStatusText(record.newStatus) }}</span>
                    </div>
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
          <div v-else class="no-audit-history">
            <div class="empty-state">
              <el-icon size="48" color="#c0c4cc"><Document /></el-icon>
              <p style="margin: 12px 0 4px;">暂无审核记录</p>
              <p style="color: #909399; font-size: 12px;">
                该房源尚未进行过审核，或审核记录已被系统清理
              </p>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { House, Document, Refresh, InfoFilled, User, TrendCharts } from "@element-plus/icons-vue";
import { getHomestayAuditHistory } from "@/api/homestay";
import { getStatusType, getStatusText, getTimelineType, getActionText, formatDateTime } from "@/utils/homestayStatus";
import type { HomestayStatus } from "@/types";

interface AuditHomestay {
  id: number;
  title: string;
  type: string;
  price: number;
  maxGuests: number;
  status: HomestayStatus;
}

const props = defineProps<{
  modelValue: boolean;
  homestay: AuditHomestay | null;
  typeLabel?: string;
}>();

const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();

const auditRecords = ref<any[]>([]);
const loadingAuditHistory = ref(false);
const showDataQualityInfo = ref(false);

const refresh = async () => {
  if (!props.homestay?.id) return;
  try {
    loadingAuditHistory.value = true;
    const response = await getHomestayAuditHistory(props.homestay.id, 0, 10);

    if (response.data && response.data.content) {
      auditRecords.value = response.data.content;
    } else {
      auditRecords.value = [];
    }
  } catch (error: any) {
    console.error("刷新审核历史失败", error);
    auditRecords.value = [];
  } finally {
    loadingAuditHistory.value = false;
  }
};
</script>
