<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef } from "vue";
import type { HostCalendarDay, HostCalendarStatus } from "@/api/hostCalendar";
import { formatCalendarDate } from "@/composables/useHostCalendar";

interface CalendarCell {
  date: string;
  dayNumber: number;
  inMonth: boolean;
  isToday: boolean;
  entries: HostCalendarDay[];
}

const props = defineProps<{
  currentMonth: Date;
  days: HostCalendarDay[];
  loading: boolean;
}>();

const emit = defineEmits<{
  "day-click": [payload: { date: string; entries: HostCalendarDay[] }];
  "range-select": [payload: { startDate: string; endDate: string; entries: HostCalendarDay[] }];
  "order-click": [orderId: number];
  escape: [];
}>();

const focusedDate = shallowRef<string | null>(null);

const selectionStart = shallowRef<string | null>(null);
const selectionEnd = shallowRef<string | null>(null);
const isSelecting = ref(false);

function getDateRange(): [string, string] | null {
  if (!selectionStart.value || !selectionEnd.value) return null;
  const start =
    selectionStart.value <= selectionEnd.value ? selectionStart.value : selectionEnd.value;
  const end =
    selectionStart.value <= selectionEnd.value ? selectionEnd.value : selectionStart.value;
  return [start, end];
}

function isInRange(date: string): boolean {
  const range = getDateRange();
  if (!range) return false;
  return date >= range[0] && date <= range[1];
}

function startSelection(cell: CalendarCell) {
  if (!cell.inMonth) return;
  focusedDate.value = cell.date;
  selectionStart.value = cell.date;
  selectionEnd.value = cell.date;
  isSelecting.value = true;
}

function extendSelection(cell: CalendarCell) {
  if (!isSelecting.value || !cell.inMonth) return;
  selectionEnd.value = cell.date;
}

function finishSelection(cell: CalendarCell) {
  if (!isSelecting.value) return;
  isSelecting.value = false;

  const range = getDateRange();
  selectionStart.value = null;
  selectionEnd.value = null;

  if (!range) return;

  if (range[0] === range[1]) {
    emit("day-click", { date: cell.date, entries: cell.entries });
  } else {
    emit("range-select", { startDate: range[0], endDate: range[1], entries: cell.entries });
  }
}

function cancelSelection() {
  if (isSelecting.value) {
    isSelecting.value = false;
    selectionStart.value = null;
    selectionEnd.value = null;
  }
}

const inMonthCells = computed(() => cells.value.filter((c) => c.inMonth));

function focusNearby(current: string, deltaRow: number, deltaCol: number): string | null {
  const idx = cells.value.findIndex((c) => c.date === current);
  if (idx < 0) return null;

  const targetIdx = idx + deltaRow * 7 + deltaCol;
  if (targetIdx < 0 || targetIdx >= cells.value.length) return null;

  const target = cells.value[targetIdx];
  if (!target.inMonth) return null;

  return target.date;
}

function handleGridKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") {
    e.preventDefault();
    focusedDate.value = null;
    emit("escape");
    return;
  }

  if (!focusedDate.value) {
    if (
      e.key === "ArrowDown" ||
      e.key === "ArrowUp" ||
      e.key === "ArrowLeft" ||
      e.key === "ArrowRight"
    ) {
      e.preventDefault();
      focusedDate.value = inMonthCells.value[0]?.date ?? null;
    }
    return;
  }

  let next: string | null = null;
  switch (e.key) {
    case "ArrowUp":
      next = focusNearby(focusedDate.value, -1, 0);
      break;
    case "ArrowDown":
      next = focusNearby(focusedDate.value, 1, 0);
      break;
    case "ArrowLeft":
      next = focusNearby(focusedDate.value, 0, -1);
      break;
    case "ArrowRight":
      next = focusNearby(focusedDate.value, 0, 1);
      break;
    case "Enter": {
      e.preventDefault();
      const cell = cells.value.find((c) => c.date === focusedDate.value);
      if (cell?.inMonth) {
        emit("day-click", { date: cell.date, entries: cell.entries });
      }
      return;
    }
    case "Escape": {
      e.preventDefault();
      focusedDate.value = null;
      emit("escape");
      return;
    }
  }

  if (next) {
    e.preventDefault();
    focusedDate.value = next;
  }
}

function findCellFromTouch(touch: Touch): string | null {
  const el = document.elementFromPoint(touch.clientX, touch.clientY);
  const cellEl = el?.closest?.("[data-date]");
  return cellEl?.getAttribute("data-date") ?? null;
}

function handleTouchStart(e: TouchEvent, cell: CalendarCell) {
  if (!cell.inMonth) return;
  e.preventDefault();
  startSelection(cell);
}

function handleTouchMove(e: TouchEvent) {
  if (!isSelecting.value) return;
  const date = findCellFromTouch(e.touches[0]);
  if (date) selectionEnd.value = date;
}

function handleTouchEnd(e: TouchEvent) {
  if (!isSelecting.value) return;
  const date = findCellFromTouch(e.changedTouches[0]);
  const cell = date ? cells.value.find((c) => c.date === date) : null;
  if (cell) finishSelection(cell);
  else cancelSelection();
}

onMounted(() => {
  document.addEventListener("mouseup", cancelSelection);
});

onUnmounted(() => {
  document.removeEventListener("mouseup", cancelSelection);
});

const weekdays = ["一", "二", "三", "四", "五", "六", "日"];

const statusLabels: Record<HostCalendarStatus, string> = {
  AVAILABLE: "可订",
  PENDING_CONFIRM: "待确认",
  BOOKED: "已订",
  CHECKED_IN: "入住中",
  CHECKED_OUT: "已退房",
  UNAVAILABLE: "不可订",
  LOCKED: "锁定中",
};

const statusPriority: HostCalendarStatus[] = [
  "CHECKED_IN",
  "CHECKED_OUT",
  "BOOKED",
  "PENDING_CONFIRM",
  "LOCKED",
  "UNAVAILABLE",
  "AVAILABLE",
];

const today = formatCalendarDate(new Date());

const daysByDate = computed(() => {
  const map = new Map<string, HostCalendarDay[]>();
  for (const day of props.days) {
    const entries = map.get(day.date) ?? [];
    entries.push(day);
    map.set(day.date, entries);
  }
  return map;
});

const cells = computed<CalendarCell[]>(() => {
  const monthStart = new Date(props.currentMonth.getFullYear(), props.currentMonth.getMonth(), 1);
  const leadingDays = (monthStart.getDay() + 6) % 7;
  const gridStart = new Date(monthStart);
  gridStart.setDate(monthStart.getDate() - leadingDays);

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    const dateKey = formatCalendarDate(date);
    return {
      date: dateKey,
      dayNumber: date.getDate(),
      inMonth: date.getMonth() === props.currentMonth.getMonth(),
      isToday: dateKey === today,
      entries: daysByDate.value.get(dateKey) ?? [],
    };
  });
});

function getDominantStatus(entries: HostCalendarDay[]): HostCalendarStatus {
  if (entries.length === 0) {
    return "AVAILABLE";
  }
  return (
    statusPriority.find((status) => entries.some((entry) => entry.status === status)) ?? "AVAILABLE"
  );
}

function getStatusClass(status: HostCalendarStatus) {
  return `status-${status.toLowerCase().replace(/_/g, "-")}`;
}

function countByStatus(entries: HostCalendarDay[], status: HostCalendarStatus) {
  return entries.filter((entry) => entry.status === status).length;
}

function getCellSummary(entries: HostCalendarDay[]) {
  if (entries.length === 0) {
    return "暂无房源";
  }

  const booked = entries.filter((entry) =>
    ["BOOKED", "CHECKED_IN", "CHECKED_OUT"].includes(entry.status)
  ).length;
  const unavailable = countByStatus(entries, "UNAVAILABLE");
  const pending = countByStatus(entries, "PENDING_CONFIRM");
  const available = countByStatus(entries, "AVAILABLE");

  if (entries.length === 1) {
    return statusLabels[entries[0].status];
  }

  return `${available}可订 / ${booked}已订 / ${pending}待确认 / ${unavailable}不可订`;
}
</script>

<template>
  <div v-loading="loading" class="calendar-grid" tabindex="0" @keydown="handleGridKeydown">
    <div v-for="weekday in weekdays" :key="weekday" class="weekday">
      {{ weekday }}
    </div>

    <button
      v-for="cell in cells"
      :key="cell.date"
      type="button"
      :data-date="cell.date"
      class="calendar-cell"
      :class="[
        {
          muted: !cell.inMonth,
          today: cell.isToday,
          'in-range': isInRange(cell.date),
          focused: focusedDate === cell.date,
        },
        getStatusClass(getDominantStatus(cell.entries)),
      ]"
      :disabled="!cell.inMonth"
      :tabindex="focusedDate === cell.date ? 0 : -1"
      @mousedown.prevent="startSelection(cell)"
      @mouseenter="extendSelection(cell)"
      @mouseup="finishSelection(cell)"
      @touchstart="handleTouchStart($event, cell)"
      @touchmove.prevent="handleTouchMove($event)"
      @touchend="handleTouchEnd($event)"
    >
      <span class="cell-header">
        <span class="day-number">{{ cell.dayNumber }}</span>
        <span v-if="cell.entries.some((entry) => entry.checkIn)" class="flag checkin">入住</span>
        <span v-if="cell.entries.some((entry) => entry.checkOut)" class="flag checkout">退房</span>
      </span>

      <span class="cell-status">{{ getCellSummary(cell.entries) }}</span>

      <span v-if="cell.entries.length === 1" class="cell-detail">
        <template v-if="cell.entries[0].orderId">
          <a
            class="order-link"
            :href="`/host/orders/${cell.entries[0].orderId}`"
            @mousedown.stop
            @click.prevent="emit('order-click', cell.entries[0].orderId)"
          >
            {{
              cell.entries[0].guestName ||
              cell.entries[0].orderNumber ||
              `订单#${cell.entries[0].orderId}`
            }}
          </a>
        </template>
        <template v-else>
          <span v-if="cell.entries[0].reason" class="reason-tag">{{ cell.entries[0].reason }}</span>
          <span v-else-if="cell.entries[0].guestName">{{ cell.entries[0].guestName }}</span>
        </template>
        <span v-if="cell.entries[0].finalPrice" class="price-tag"
          >¥{{ cell.entries[0].finalPrice }}</span
        >
      </span>

      <el-tooltip v-else-if="cell.entries.length > 1" placement="top" :show-after="300">
        <template #content>
          <div class="tooltip-homestay-list">
            <div v-for="e in cell.entries" :key="e.homestayId" class="tooltip-row">
              <span class="tooltip-name">{{ e.homestayTitle }}</span>
              <span class="tooltip-status" :class="`text-${getStatusClass(e.status)}`">
                {{ statusLabels[e.status] }}
              </span>
            </div>
          </div>
        </template>
        <span class="cell-detail"> {{ cell.entries.length }} 套房源 </span>
      </el-tooltip>
    </button>
  </div>
</template>

<style scoped>
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid #d8dee9;
  border-radius: 8px;
  background: #d8dee9;
}

.weekday {
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.calendar-cell {
  min-height: 118px;
  padding: 10px;
  border: none;
  background: #fff;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition:
    background 0.16s ease,
    box-shadow 0.16s ease;
}

.calendar-cell:hover:not(:disabled) {
  background: #f8fbff;
  box-shadow: inset 0 0 0 1px #93c5fd;
}

.calendar-cell:disabled {
  cursor: default;
}

.muted {
  background: #f8fafc;
  color: #94a3b8;
}

.today {
  box-shadow: inset 0 0 0 2px #2563eb;
}

.cell-header {
  min-height: 22px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.day-number {
  color: #111827;
  font-weight: 700;
}

.flag {
  padding: 2px 6px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.2;
}

.checkin {
  color: #075985;
  background: #e0f2fe;
}

.checkout {
  color: #5b21b6;
  background: #ede9fe;
}

.cell-status {
  color: #334155;
  font-size: 13px;
  line-height: 1.35;
}

.cell-detail {
  margin-top: auto;
  color: #64748b;
  font-size: 12px;
  line-height: 1.3;
  word-break: break-word;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.order-link {
  display: inline-block;
  color: #2563eb;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
  padding: 2px 0;
  line-height: 1.4;
}

.order-link:hover {
  color: #1d4ed8;
  background: rgba(37, 99, 235, 0.06);
  border-radius: 4px;
}

.order-link:active {
  color: #1e40af;
}

.reason-tag {
  color: #dc2626;
  font-size: 11px;
}

.price-tag {
  color: #059669;
  font-weight: 600;
  font-size: 12px;
}

.status-available {
  border-top: 3px solid #16a34a;
}

.status-pending-confirm {
  border-top: 3px solid #d97706;
}

.status-booked,
.status-checked-in,
.status-checked-out {
  border-top: 3px solid #2563eb;
}

.status-unavailable {
  border-top: 3px solid #dc2626;
}

.status-locked {
  border-top: 3px solid #64748b;
}

.in-range {
  background: #dbeafe;
  box-shadow: inset 0 0 0 1px #93c5fd;
}

.in-range:hover:not(:disabled) {
  background: #bfdbfe;
  box-shadow: inset 0 0 0 1px #60a5fa;
}

.focused {
  box-shadow:
    inset 0 0 0 2px #2563eb,
    0 0 0 2px #fff,
    0 0 0 4px #2563eb !important;
  z-index: 1;
  position: relative;
}

.calendar-grid:focus-visible {
  outline: none;
}

@media (max-width: 900px) {
  .calendar-grid {
    min-width: 760px;
  }
}

@media (max-width: 768px) {
  .calendar-cell {
    min-height: 80px;
    padding: 6px;
    gap: 4px;
  }

  .day-number {
    font-size: 13px;
  }

  .flag {
    font-size: 10px;
    padding: 1px 4px;
  }

  .cell-status {
    font-size: 11px;
  }

  .cell-detail {
    font-size: 10px;
  }

  .weekday {
    height: 30px;
    font-size: 12px;
  }

  .price-tag {
    font-size: 11px;
  }
}

.tooltip-homestay-list {
  min-width: 160px;
}

.tooltip-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 3px 0;
  font-size: 12px;
}

.tooltip-name {
  color: #e2e8f0;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tooltip-status {
  white-space: nowrap;
}

.tooltip-status.text-status-available {
  color: #4ade80;
}
.tooltip-status.text-status-pending-confirm {
  color: #fbbf24;
}
.tooltip-status.text-status-booked,
.tooltip-status.text-status-checked-in,
.tooltip-status.text-status-checked-out {
  color: #60a5fa;
}
.tooltip-status.text-status-unavailable {
  color: #f87171;
}
.tooltip-status.text-status-locked {
  color: #94a3b8;
}
</style>
