<template>
  <el-dialog
    :model-value="modelValue"
    title="订单支付"
    width="400px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @open="generateQrCode"
    @close="stopPolling"
  >
    <div class="payment-dialog-content">
      <template v-if="qrCodeLoading">
        <div class="qr-loading" v-loading="true" element-loading-text="正在生成支付二维码..."></div>
      </template>
      <template v-else-if="paymentQrCode">
        <div class="payment-qr-info">
          <p class="payment-amount">支付金额: <span>¥{{ totalAmount }}</span></p>
          <div class="qr-code-wrapper">
            <img v-if="paymentQrCode.startsWith('http')" :src="paymentQrCode" alt="支付二维码" class="qr-image" />
            <qrcode-vue v-else :value="paymentQrCode" :size="200" level="H" />
          </div>
          <p class="payment-tip">请使用支付宝扫描二维码完成支付</p>

          <div class="mock-pay-section" v-if="isDev">
            <el-divider>测试专用</el-divider>
            <el-button type="success" @click="handleManualPay" :loading="payLoading" icon="CircleCheck">
              模拟直接支付 (调用 payOrder API)
            </el-button>
            <p class="mock-tip">提示：此按钮将显式触发后端支付确认逻辑</p>
          </div>

          <el-divider />
          <div class="payment-status-info">
            <el-icon class="is-loading" v-if="isPolling">
              <Loading />
            </el-icon>
            <span>{{ pollingStatusText }}</span>
          </div>
        </div>
      </template>
      <template v-else>
        <el-empty description="二维码生成失败">
          <el-button type="primary" @click="generateQrCode">重试</el-button>
        </el-empty>
      </template>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">取消支付</el-button>
        <el-button type="success" @click="checkPaymentStatus" :loading="checkingStatus">已完成支付</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Loading } from "@element-plus/icons-vue";
import QrcodeVue from "qrcode.vue";
import { generatePaymentQRCode, checkPayment, payOrder } from "@/api/order";

const props = defineProps<{
  modelValue: boolean;
  orderId?: number | null;
  totalAmount?: number | null;
  isDev?: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  paid: [];
}>();

const qrCodeLoading = ref(false);
const paymentQrCode = ref("");
const isPolling = ref(false);
const pollingTimer = ref<number | null>(null);
const pollingStatusText = ref("等待支付...");
const checkingStatus = ref(false);
const payLoading = ref(false);

const notifyPaid = () => {
  emit("paid");
  emit("update:modelValue", false);
};

// 生成二维码
const generateQrCode = async () => {
  if (!props.orderId) return;

  qrCodeLoading.value = true;
  paymentQrCode.value = "";

  try {
    const response = await generatePaymentQRCode({
      orderId: props.orderId,
      method: "alipay",
    });

    if (response.data.success) {
      const data = response.data;
      if (data.paymentUrl && data.paymentUrl.includes("<form")) {
        // HTML 表单跳转支付
        const div = document.createElement("div");
        div.id = "alipay-form-container";
        div.style.display = "none";
        div.innerHTML = data.paymentUrl;
        document.body.appendChild(div);
        const form = div.querySelector("form");
        if (form) {
          form.submit();
          ElMessage.success("正在跳转至支付宝支付页面...");
        } else {
          ElMessage.error("支付表单生成失败，请重试");
        }
        return;
      }

      if (data.qrCode) {
        paymentQrCode.value = data.qrCode;
        startPolling();
      } else if (data.paymentUrl) {
        window.location.href = data.paymentUrl;
      } else {
        ElMessage.error("获取支付信息失败：返回结果异常");
      }
    } else {
      ElMessage.error(response.data.message || "生成支付信息失败");
    }
  } catch (error) {
    console.error("生成支付信息异常:", error);
  } finally {
    qrCodeLoading.value = false;
  }
};

// 开始轮询支付状态
const startPolling = () => {
  stopPolling();
  isPolling.value = true;
  pollingStatusText.value = "支付确认中...";

  let errorCount = 0;
  pollingTimer.value = window.setInterval(async () => {
    if (!props.orderId) return;

    try {
      const response = await checkPayment(props.orderId);
      if (response.data.success && response.data.isPaid) {
        stopPolling();
        ElMessage.success("支付成功！");
        notifyPaid();
      } else {
        errorCount = 0;
        pollingStatusText.value = "正在核对支付结果...";
      }
    } catch (error) {
      console.error("轮询支付状态异常:", error);
      errorCount++;
      if (errorCount > 3) {
        pollingStatusText.value = "支付确认延迟，请勿关闭页面...";
      }
    }
  }, 4000);
};

// 停止轮询
const stopPolling = () => {
  isPolling.value = false;
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value);
    pollingTimer.value = null;
  }
};

// 手动检查支付状态
const checkPaymentStatus = async () => {
  if (!props.orderId) return;

  checkingStatus.value = true;
  try {
    const response = await checkPayment(props.orderId);
    if (response.data.success && response.data.isPaid) {
      ElMessage.success("支付已成功确认");
      notifyPaid();
    } else {
      ElMessage.warning("尚未检测到支付成功，请扫码支付");
    }
  } catch (error) {
    // 错误已由拦截器统一提示
  } finally {
    checkingStatus.value = false;
  }
};

// 手动直接支付（模拟）
const handleManualPay = async () => {
  if (!props.orderId) return;

  try {
    await ElMessageBox.confirm(
      "这将跳过实际支付流程，直接调用后端接口模拟支付成功。是否继续？",
      "手动支付确认",
      {
        confirmButtonText: "确定支付",
        cancelButtonText: "取消",
        type: "warning",
      }
    );

    payLoading.value = true;
    const response = await payOrder(props.orderId, "ALIPAY");

    if (response.data.success) {
      ElMessage.success("模拟支付操作成功！");
      stopPolling();
      notifyPaid();
    } else {
      ElMessage.error(response.data.message || "操作失败");
    }
  } catch (error: any) {
    if (error !== "cancel") {
      console.error("手动支付异常:", error);
    }
  } finally {
    payLoading.value = false;
  }
};

onUnmounted(stopPolling);
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
