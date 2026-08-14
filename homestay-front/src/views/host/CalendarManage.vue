<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Calendar, User, Document, InfoFilled, Search } from "@element-plus/icons-vue";
import CalendarStats from "@/components/host-calendar/CalendarStats.vue";
import CalendarToolbar from "@/components/host-calendar/CalendarToolbar.vue";
import HostCalendarGrid from "@/components/host-calendar/HostCalendarGrid.vue";
import {
  addCalendarDays,
  useHostCalendar,
} from "@/composables/useHostCalendar";
import { exportHostCalendar, updateHostCalendarPrice } from "@/api/hostCalendar";
import type { HostCalendarDay, HostCalendarStatus } from "@/api/hostCalendar";

const router = useRouter();

type EditableStatus = "AVAILABLE" | "UNAVAILABLE";

const {
  currentMonth,
  selectedHomestayId,
  homestayOptions,
  days,
  summary,
  loading,
  saving,
  monthLabel,
  queryRange,
  initialize,
  loadCalendar,
  saveAvailability,
  goPrevMonth,
  goNextMonth,
  goToday,
} = useHostCalendar();

const drawerVisible = shallowRef(false);
const selectedDate = shallowRef("");
const selectedEntries = ref<HostCalendarDay[]>([]);
const activeTab = shallowRef<"status" | "price">("status");
const batchProgress = ref<{ current: number; total: number } | null>(null);

const form = reactive<{
  homestayIds: number[];
  dateRange: string[];
  status: EditableStatus;
  reason: string;
  note: string;
  customPrice?: number;
}>({
  homestayIds: [],
  dateRange: [],
  status: "UNAVAILABLE",
  reason: "",
  note: "",
  customPrice: undefined,
});

const statusLabels: Record<HostCalendarStatus, string> = {
  AVAILABLE: "可订",
  PENDING_CONFIRM: "待确认",
  BOOKED: "已订",
  CHECKED_IN: "入住中",
  CHECKED_OUT: "已退房",
  UNAVAILABLE: "不可订",
  LOCKED: "锁定中",
};

const statusTagType: Record<HostCalendarStatus, "success" | "warning" | "danger" | "info" | "primary"> = {
  AVAILABLE: "success",
  PENDING_CONFIRM: "warning",
  BOOKED: "primary",
  CHECKED_IN: "success",
  CHECKED_OUT: "info",
  UNAVAILABLE: "danger",
  LOCKED: "info",
};

const drawerTitle = computed(() => {
  if (selectedDate.value && form.dateRange.length === 2) {
    return form.dateRange[0] === form.dateRange[1]
      ? `${selectedDate.value} 日历调整`
      : `${form.dateRange[0]} 至 ${form.dateRange[1]} 日历调整`;
  }
  return selectedDate.value ? `${selectedDate.value} 日历调整` : "日历调整";
});

const selectedEntry = computed(() => {
  return selectedEntries.value.find((entry) => entry.homestayId === form.homestayIds[0]);
});

// 抽屉中只展示即将操作的房源卡片（避免全部房源模式下卡片过多）
const displayedEntries = computed(() => {
  const selectedIds = new Set(form.homestayIds);
  return selectedEntries.value.filter((entry) => selectedIds.has(entry.homestayId));
});

// 该日所有房源的状态统计（全部房源模式下展示）
const dayStatusSummary = computed(() => {
  if (selectedHomestayId.value !== null || selectedEntries.value.length === 0) return null;
  const counts: Record<string, number> = {};
  for (const entry of selectedEntries.value) {
    counts[entry.status] = (counts[entry.status] || 0) + 1;
  }
  const items = Object.entries(counts)
    .map(([status, count]) => ({ status: status as HostCalendarStatus, count }))
    .filter((item) => item.count > 0);
  return items;
});

const selectedDayCount = computed(() => {
  if (form.dateRange.length !== 2) return 0;
  return daysBetween(form.dateRange[0], form.dateRange[1]);
});

function getStatusLabel(status: HostCalendarStatus) {
  return statusLabels[status] ?? status;
}

function openDrawer(date: string, entries: HostCalendarDay[], range: [string, string]) {
  if (homestayOptions.value.length === 0) {
    ElMessage.warning("暂无可管理房源");
    return;
  }

  selectedDate.value = date;
  selectedEntries.value = entries;
  activeTab.value = "status";
  form.homestayIds = selectedHomestayId.value !== null
    ? [selectedHomestayId.value]
    : (entries.length > 0 ? [entries[0].homestayId] : (homestayOptions.value[0]?.id ? [homestayOptions.value[0].id] : []));
  form.dateRange = range;
  form.status = "UNAVAILABLE";
  form.reason = selectedEntry.value?.reason ?? "";
  form.note = selectedEntry.value?.note ?? "";
  form.customPrice = selectedEntry.value?.finalPrice ?? undefined;
  if (form.homestayIds.length === 0 && homestayOptions.value.length > 0) {
    form.homestayIds = [homestayOptions.value[0].id];
  }
  drawerVisible.value = true;
}

function handleDayClick(payload: { date: string; entries: HostCalendarDay[] }) {
  openDrawer(payload.date, payload.entries, [payload.date, payload.date]);
}

function handleRangeSelect(payload: { startDate: string; endDate: string; entries: HostCalendarDay[] }) {
  openDrawer(payload.startDate, payload.entries, [payload.startDate, payload.endDate]);
}

function handleOrderClick(orderId: number) {
  router.push(`/host/orders/${orderId}`);
}

async function handleExport() {
  const params = {
    startDate: queryRange.value.startDate,
    endDate: queryRange.value.endDate,
    ...(selectedHomestayId.value ? { homestayId: selectedHomestayId.value } : {}),
  };
  try {
    const response = await exportHostCalendar(params);
    const blob = new Blob([response as unknown as BlobPart], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `calendar_${params.startDate}_to_${params.endDate}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    ElMessage.success("导出成功");
  } catch {
  }
}

function daysBetween(start: string, end: string): number {
  const startDate = new Date(start + "T00:00:00");
  const endDate = new Date(end + "T00:00:00");
  return Math.round((endDate.getTime() - startDate.getTime()) / 86400000) + 1;
}

async function submitAvailability() {
  if (form.homestayIds.length === 0) {
    ElMessage.warning("请选择房源");
    return;
  }

  if (form.dateRange.length !== 2) {
    ElMessage.warning("请选择日期范围");
    return;
  }

  const dayCount = daysBetween(form.dateRange[0], form.dateRange[1]);
  const totalDays = dayCount * form.homestayIds.length;

  if (form.status === "UNAVAILABLE" && totalDays > 7) {
    try {
      await ElMessageBox.confirm(
        `即将把 ${form.homestayIds.length} 套房源的 ${dayCount} 天设为不可订（共 ${totalDays} 天），确认操作？`,
        "批量操作确认",
        { confirmButtonText: "确认", cancelButtonText: "取消", type: "warning" }
      );
    } catch {
      return;
    }
  }

  let successCount = 0;
  let failCount = 0;
  batchProgress.value = { current: 0, total: form.homestayIds.length };

  for (let i = 0; i < form.homestayIds.length; i++) {
    const homestayId = form.homestayIds[i];
    batchProgress.value.current = i + 1;
    const ok = await saveAvailability({
      homestayId,
      startDate: form.dateRange[0],
      endDate: addCalendarDays(form.dateRange[1], 1),
      status: form.status,
      reason: form.status === "UNAVAILABLE" ? form.reason.trim() : undefined,
      note: form.status === "UNAVAILABLE" ? form.note.trim() : undefined,
    });
    if (ok) {
      successCount++;
    } else {
      failCount++;
    }
  }

  batchProgress.value = null;

  if (failCount === 0) {
    ElMessage.success(`已成功更新 ${successCount} 套房源`);
    drawerVisible.value = false;
  } else if (successCount === 0) {
    ElMessage.error(`更新失败，共 ${failCount} 套房源`);
  } else {
    ElMessage.warning(`部分成功：${successCount} 套成功，${failCount} 套失败`);
  }
}

async function doPriceUpdate(clearMode: boolean) {
  if (form.homestayIds.length === 0) {
    ElMessage.warning("请选择房源");
    return;
  }
  if (form.dateRange.length !== 2) {
    ElMessage.warning("请选择日期范围");
    return;
  }
  if (!clearMode && (form.customPrice === undefined || form.customPrice === null || Number.isNaN(form.customPrice))) {
    ElMessage.warning("请输入价格");
    return;
  }

  let successCount = 0;
  let failCount = 0;
  batchProgress.value = { current: 0, total: form.homestayIds.length };

  for (let i = 0; i < form.homestayIds.length; i++) {
    const homestayId = form.homestayIds[i];
    batchProgress.value.current = i + 1;
    try {
      await updateHostCalendarPrice({
        homestayId,
        startDate: form.dateRange[0],
        endDate: addCalendarDays(form.dateRange[1], 1),
        customPrice: clearMode ? null : Number(form.customPrice),
      });
      successCount++;
    } catch {
      failCount++;
    }
  }

  batchProgress.value = null;

  if (failCount === 0) {
    ElMessage.success(clearMode ? `已成功清除 ${successCount} 套房源的自定义价格` : `已成功更新 ${successCount} 套房源的价格`);
    await loadCalendar();
    drawerVisible.value = false;
  } else if (successCount === 0) {
    ElMessage.error(clearMode ? `清除价格失败，共 ${failCount} 套房源` : `价格更新失败，共 ${failCount} 套房源`);
  } else {
    ElMessage.warning(`部分成功：${successCount} 套成功，${failCount} 套失败`);
    await loadCalendar();
  }
}

async function submitPrice() {
  await doPriceUpdate(false);
}

async function submitClearPrice() {
  await doPriceUpdate(true);
}

onMounted(() => {
  void initialize();
});
</script>

<template>
  <div class="calendar-page">
    <div class="page-heading">
      <h1>房东日历</h1>
    </div>

    <section class="calendar-shell">
      <CalendarToolbar
        v-model:selected-homestay-id="selectedHomestayId"
        :month-label="monthLabel"
        :homestay-options="homestayOptions"
        :loading="loading"
        @prev="goPrevMonth"
        @next="goNextMonth"
        @today="goToday"
        @refresh="loadCalendar"
        @export="handleExport"
      />

      <CalendarStats :summary="summary" />

      <div class="legend-row">
        <span class="legend available">可订</span>
        <span class="legend pending">待确认</span>
        <span class="legend booked">已订</span>
        <span class="legend unavailable">不可订</span>
        <span class="legend locked">锁定中</span>
      </div>

      <div v-if="homestayOptions.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无可管理房源" />
      </div>
      <div v-else class="grid-scroll">
        <HostCalendarGrid
          :current-month="currentMonth"
          :days="days"
          :loading="loading"
          @day-click="handleDayClick"
          @range-select="handleRangeSelect"
          @order-click="handleOrderClick"
          @escape="drawerVisible = false"
        />
      </div>
    </section>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="480px">
      <div class="drawer-content">
        <!-- 日期范围摘要 -->
        <div class="date-header">
          <el-icon><Calendar /></el-icon>
          <span v-if="form.dateRange.length === 2 && form.dateRange[0] === form.dateRange[1]" class="date-text">
            {{ form.dateRange[0] }}
          </span>
          <span v-else-if="form.dateRange.length === 2" class="date-text">
            {{ form.dateRange[0] }} 至 {{ form.dateRange[1] }}
          </span>
          <span class="day-badge">共 {{ selectedDayCount }} 天</span>
        </div>

        <!-- 全部房源模式下的状态统计 -->
        <div v-if="dayStatusSummary && dayStatusSummary.length > 0" class="day-summary-bar">
          <span class="summary-label">该日共 {{ selectedEntries.length }} 套房源</span>
          <div class="summary-tags">
            <el-tag
              v-for="item in dayStatusSummary"
              :key="item.status"
              :type="statusTagType[item.status]"
              size="small"
              effect="light"
            >
              {{ getStatusLabel(item.status) }} {{ item.count }}
            </el-tag>
          </div>
        </div>

        <!-- 房源状态卡片（只展示选中的房源） -->
        <div v-if="displayedEntries.length === 0" class="entry-empty">
          <el-empty description="选中的房源在当前日期暂无日程数据" :image-size="60" />
        </div>
        <div v-else class="entry-cards">
          <div
            v-for="entry in displayedEntries"
            :key="entry.homestayId"
            class="entry-card"
            :class="`status-${entry.status.toLowerCase().replace(/_/g, '-')}`"
          >
            <div class="card-header-row">
              <span class="card-title">{{ entry.homestayTitle }}</span>
              <el-tag :type="statusTagType[entry.status]" size="small" effect="light">
                {{ getStatusLabel(entry.status) }}
              </el-tag>
            </div>
            <div class="card-body">
              <div v-if="entry.finalPrice" class="card-price">
                ¥{{ entry.finalPrice }}
                <span v-if="entry.basePrice && entry.finalPrice !== entry.basePrice" class="price-original">
                  原价 ¥{{ entry.basePrice }}
                </span>
              </div>
              <div v-if="entry.guestName" class="card-guest">
                <el-icon><User /></el-icon> {{ entry.guestName }}
              </div>
              <a
                v-if="entry.orderId"
                class="card-order"
                :href="`/host/orders/${entry.orderId}`"
                @click.prevent="handleOrderClick(entry.orderId)"
              >
                <el-icon><Document /></el-icon> {{ entry.orderNumber || `订单#${entry.orderId}` }}
              </a>
              <div v-else-if="entry.reason" class="card-reason">
                <el-icon><InfoFilled /></el-icon> {{ entry.reason }}
              </div>
            </div>
          </div>
        </div>

        <!-- 操作 Tabs -->
        <el-tabs v-model="activeTab" class="drawer-tabs">
          <el-tab-pane label="调整状态" name="status">
            <el-form label-position="top" class="drawer-form">
              <el-form-item label="目标房源">
                <el-select
                  v-model="form.homestayIds"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="输入关键词搜索房源"
                  class="homestay-multi-select"
                >
                  <template #prefix>
                    <el-icon><Search /></el-icon>
                  </template>
                  <el-option
                    v-for="item in homestayOptions"
                    :key="item.id"
                    :label="item.title"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="状态">
                <el-radio-group v-model="form.status" size="large">
                  <el-radio-button label="UNAVAILABLE">设为不可订</el-radio-button>
                  <el-radio-button label="AVAILABLE">恢复可订</el-radio-button>
                </el-radio-group>
              </el-form-item>

              <template v-if="form.status === 'UNAVAILABLE'">
                <el-form-item label="原因">
                  <el-input v-model="form.reason" maxlength="80" show-word-limit placeholder="例如：维修、自住" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="form.note" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="可选填" />
                </el-form-item>
              </template>

              <el-form-item>
                <el-button type="primary" :loading="saving" class="submit-btn" @click="submitAvailability">
                  <template v-if="batchProgress">保存中 ({{ batchProgress.current }}/{{ batchProgress.total }})</template>
                  <template v-else>保存状态</template>
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="调整价格" name="price">
            <el-form label-position="top" class="drawer-form">
              <el-form-item label="目标房源">
                <el-select
                  v-model="form.homestayIds"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="输入关键词搜索房源"
                  class="homestay-multi-select"
                >
                  <template #prefix>
                    <el-icon><Search /></el-icon>
                  </template>
                  <el-option
                    v-for="item in homestayOptions"
                    :key="item.id"
                    :label="item.title"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="每日价格">
                <el-input-number
                  v-model="form.customPrice"
                  :min="0"
                  :precision="2"
                  :step="10"
                  placeholder="请输入每日价格"
                  class="price-input"
                  controls-position="right"
                />
                <div class="form-hint">留空表示恢复为默认价格</div>
              </el-form-item>

              <el-form-item>
                <div class="price-actions">
                  <el-button type="success" :loading="saving" class="submit-btn" @click="submitPrice">
                    <template v-if="batchProgress">保存中 ({{ batchProgress.current }}/{{ batchProgress.total }})</template>
                    <template v-else>保存价格</template>
                  </el-button>
                  <el-button :loading="saving" class="submit-btn" @click="submitClearPrice">
                    <template v-if="batchProgress">保存中 ({{ batchProgress.current }}/{{ batchProgress.total }})</template>
                    <template v-else>清除价格</template>
                  </el-button>
                </div>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.calendar-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading h1 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  font-weight: 700;
}

.calendar-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.legend::before {
  content: "";
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #94a3b8;
}

.legend.available::before {
  background: #16a34a;
}

.legend.pending::before {
  background: #d97706;
}

.legend.booked::before {
  background: #2563eb;
}

.legend.unavailable::before {
  background: #dc2626;
}

.legend.locked::before {
  background: #64748b;
}

.grid-scroll {
  overflow-x: auto;
}

.empty-state {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
}

.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 日期头部 */
.date-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #eff6ff;
  border-radius: 8px;
  color: #1e40af;
}

.date-header .el-icon {
  font-size: 18px;
}

.date-text {
  font-size: 15px;
  font-weight: 600;
}

.day-badge {
  margin-left: auto;
  padding: 2px 10px;
  background: #dbeafe;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: #1d4ed8;
}

/* 日统计条 */
.day-summary-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.summary-label {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
  white-space: nowrap;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 房源状态卡片 */
.entry-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.entry-card {
  padding: 14px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-left-width: 4px;
  transition: box-shadow 0.15s ease;
}

.entry-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.entry-card.status-available { border-left-color: #16a34a; }
.entry-card.status-pending-confirm { border-left-color: #d97706; }
.entry-card.status-booked { border-left-color: #2563eb; }
.entry-card.status-checked-in { border-left-color: #16a34a; }
.entry-card.status-checked-out { border-left-color: #64748b; }
.entry-card.status-unavailable { border-left-color: #dc2626; }
.entry-card.status-locked { border-left-color: #64748b; }

.entry-empty {
  padding: 20px 0;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.card-title {
  font-weight: 600;
  color: #111827;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-price {
  font-size: 16px;
  font-weight: 700;
  color: #059669;
}

.price-original {
  margin-left: 6px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 400;
  text-decoration: line-through;
}

.card-guest,
.card-order,
.card-reason {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
}

.card-order {
  color: #2563eb;
  text-decoration: none;
  cursor: pointer;
}

.card-order:hover {
  text-decoration: underline;
}

/* Tabs */
.drawer-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.drawer-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.drawer-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #374151;
  padding-bottom: 4px;
}

.homestay-multi-select,
.range-picker,
.price-input {
  width: 100%;
}

.price-input :deep(.el-input__wrapper) {
  width: 100%;
}

.form-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.price-actions {
  display: flex;
  gap: 10px;
  width: 100%;
}

.price-actions .submit-btn {
  flex: 1;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
