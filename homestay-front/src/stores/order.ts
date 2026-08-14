import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getMyOrders, getOrderById, getTimeoutConfig } from "@/api/order";
import type { OrderStatus, PaymentStatus } from "@/types/index";

export interface ReviewItem {
  id: number;
  userId?: number;
  userName?: string;
  userAvatar?: string;
  homestayId: number;
  homestayTitle?: string;
  orderId?: number;
  rating: number;
  content: string;
  response?: string;
  createTime: string;
  responseTime?: string;
  images?: string[];
  cleanlinessRating?: number;
  accuracyRating?: number;
  communicationRating?: number;
  locationRating?: number;
  checkInRating?: number;
  valueRating?: number;
  isPublic?: boolean;
}

export interface OrderItem {
  id: number;
  orderNumber: string;
  homestayId: number;
  homestayTitle: string;
  hostName?: string;
  imageUrl?: string;
  address?: string;
  guestId?: number;
  guestName?: string;
  guestPhone?: string;
  guestCount: number;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  price?: number;
  cleaningFee?: number;
  serviceFee?: number;
  totalAmount: number;
  status: string;
  paymentStatus?: string;
  remark?: string;
  createTime: string;
  updateTime: string;
  completedAt?: string;
  reviewed?: boolean;
  review?: ReviewItem | null;
  refundType?: string;
  refundReason?: string;
  refundAmount?: number;
  refundInitiatedByName?: string;
  refundInitiatedAt?: string;
  refundProcessedByName?: string;
  refundProcessedAt?: string;
  refundTransactionId?: string;
  refundRejectionReason?: string;
  disputeReason?: string;
  disputeRaisedBy?: number;
  disputeResolution?: string;
  disputeResolutionNote?: string;
}

interface Pagination {
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export const useOrderStore = defineStore("order", () => {
  // State
  const orders = ref<OrderItem[]>([]);
  const statsOrders = ref<OrderItem[]>([]); // 用于 badge 统计的缓存（拉取较多数据）
  const currentOrder = ref<OrderItem | null>(null);
  const pagination = ref<Pagination>({
    page: 0,
    size: 10,
    total: 0,
    totalPages: 0,
  });
  const loading = ref(false);
  const statusCounts = ref({
    PENDING: 0,
    NEED_PAYMENT: 0,
    IN_PROGRESS: 0,
    COMPLETED: 0,
    CANCELLED: 0,
    REFUND_RELATED: 0,
  });

  const timeoutConfig = ref<{
    pendingTimeoutHours: number;
    confirmedTimeoutHours: number;
    paymentPendingTimeoutHours: number;
    warningBeforeTimeoutMinutes: number;
  } | null>(null);

  // Getters
  const orderById = computed(() => {
    return (id: number) =>
      orders.value.find((o) => o.id === id) ||
      statsOrders.value.find((o) => o.id === id) ||
      null;
  });

  // Helpers
  function parseOrders(rawOrders: any[]): OrderItem[] {
    return rawOrders.map((order: any): OrderItem => ({
      id: order.id,
      orderNumber: order.orderNumber,
      homestayId: order.homestayId,
      homestayTitle: order.homestayTitle,
      hostName: order.hostName,
      imageUrl: order.imageUrl,
      address: order.address,
      guestId: order.guestId,
      guestName: order.guestName,
      guestPhone: order.guestPhone,
      guestCount: order.guestCount,
      checkInDate: order.checkInDate,
      checkOutDate: order.checkOutDate,
      nights: order.nights,
      price: order.price,
      cleaningFee: order.cleaningFee,
      serviceFee: order.serviceFee,
      totalAmount: order.totalAmount,
      status: order.status,
      paymentStatus: order.paymentStatus,
      remark: order.remark,
      createTime: order.createTime,
      updateTime: order.updateTime,
      completedAt: order.completedAt,
      reviewed: order.reviewed ?? false,
      review: order.review ?? null,
      refundType: order.refundType,
      refundReason: order.refundReason,
      refundAmount: order.refundAmount,
      refundInitiatedByName: order.refundInitiatedByName,
      refundInitiatedAt: order.refundInitiatedAt,
      refundProcessedByName: order.refundProcessedByName,
      refundProcessedAt: order.refundProcessedAt,
      refundTransactionId: order.refundTransactionId,
      refundRejectionReason: order.refundRejectionReason,
      disputeReason: order.disputeReason,
      disputeRaisedBy: order.disputeRaisedBy,
      disputeResolution: order.disputeResolution,
      disputeResolutionNote: order.disputeResolutionNote,
    }));
  }

  function extractPageData(res: any): {
    content: any[];
    totalElements: number;
    totalPages: number;
  } {
    const orderData = res.data;
    if (orderData?.data?.content) {
      return {
        content: orderData.data.content,
        totalElements: orderData.data.totalElements ?? 0,
        totalPages: orderData.data.totalPages ?? 1,
      };
    } else if (orderData?.content) {
      return {
        content: orderData.content,
        totalElements: orderData.totalElements ?? 0,
        totalPages: orderData.totalPages ?? 1,
      };
    } else if (Array.isArray(orderData)) {
      return {
        content: orderData,
        totalElements: orderData.length,
        totalPages: 1,
      };
    }
    return { content: [], totalElements: 0, totalPages: 0 };
  }

  // Actions
  async function fetchOrders(params: {
    page?: number;
    size?: number;
    status?: string;
    tab?: string;
  } = {}) {
    loading.value = true;
    try {
      const res = await getMyOrders({
        page: params.page ?? pagination.value.page,
        size: params.size ?? pagination.value.size,
        status: params.status,
        tab: params.tab,
      });

      const { content, totalElements, totalPages } = extractPageData(res);
      orders.value = parseOrders(content);
      pagination.value = {
        page: params.page ?? pagination.value.page,
        size: params.size ?? pagination.value.size,
        total: totalElements,
        totalPages,
      };
    } catch (error) {
      console.error("获取订单列表失败:", error);
      orders.value = [];
    } finally {
      loading.value = false;
    }
  }

  /**
   * 拉取较多数据用于 badge 统计
   * 与 fetchOrders 分离，避免影响列表分页性能
   */
  async function fetchStatsOrders(size: number = 200) {
    try {
      const res = await getMyOrders({ page: 0, size });
      const { content } = extractPageData(res);
      statsOrders.value = parseOrders(content);
      updateStatusCounts();
    } catch (error) {
      console.error("获取订单统计失败:", error);
      statsOrders.value = [];
    }
  }

  async function fetchOrderDetail(id: number) {
    try {
      const res = await getOrderById(id);
      const data = res.data;
      currentOrder.value = {
        id: data.id,
        orderNumber: data.orderNumber,
        homestayId: data.homestayId,
        homestayTitle: data.homestayTitle,
        hostName: data.hostName,
        imageUrl: data.imageUrl,
        address: data.address,
        guestId: data.guestId,
        guestName: data.guestName,
        guestPhone: data.guestPhone,
        guestCount: data.guestCount,
        checkInDate: data.checkInDate,
        checkOutDate: data.checkOutDate,
        nights: data.nights,
        price: data.price,
        cleaningFee: data.cleaningFee,
        serviceFee: data.serviceFee,
        totalAmount: data.totalAmount,
        status: data.status,
        paymentStatus: data.paymentStatus,
        remark: data.remark,
        createTime: data.createTime,
        updateTime: data.updateTime,
        completedAt: data.completedAt,
        reviewed: data.reviewed ?? false,
        review: data.review ?? null,
        refundType: data.refundType,
        refundReason: data.refundReason,
        refundAmount: data.refundAmount,
        refundInitiatedByName: data.refundInitiatedByName,
        refundInitiatedAt: data.refundInitiatedAt,
        refundProcessedByName: data.refundProcessedByName,
        refundProcessedAt: data.refundProcessedAt,
        refundTransactionId: data.refundTransactionId,
        refundRejectionReason: data.refundRejectionReason,
        disputeReason: data.disputeReason,
        disputeRaisedBy: data.disputeRaisedBy,
        disputeResolution: data.disputeResolution,
        disputeResolutionNote: data.disputeResolutionNote,
      };
    } catch (error) {
      console.error("获取订单详情失败:", error);
      currentOrder.value = null;
    }
  }

  function updateStatusCounts() {
    const counts = {
      PENDING: 0,
      NEED_PAYMENT: 0,
      IN_PROGRESS: 0,
      COMPLETED: 0,
      CANCELLED: 0,
      REFUND_RELATED: 0,
    };

    const source = statsOrders.value.length > 0 ? statsOrders.value : orders.value;

    source.forEach((order) => {
      const status = order.status as OrderStatus;
      const paymentStatus = order.paymentStatus as PaymentStatus;

      if (
        paymentStatus === "REFUND_PENDING" ||
        paymentStatus === "REFUNDED" ||
        paymentStatus === "REFUND_FAILED"
      ) {
        counts.REFUND_RELATED++;
      } else if (status?.includes("CANCELLED") || status === "REJECTED") {
        counts.CANCELLED++;
      } else if (status === "COMPLETED") {
        counts.COMPLETED++;
      } else if (status === "PENDING") {
        counts.PENDING++;
      } else if (
        (status === "CONFIRMED" &&
          (paymentStatus === "UNPAID" || !paymentStatus)) ||
        status === "PAYMENT_PENDING"
      ) {
        counts.NEED_PAYMENT++;
      } else if (
        paymentStatus === "PAID" ||
        status === "CHECKED_IN" ||
        status === "READY_FOR_CHECKIN"
      ) {
        counts.IN_PROGRESS++;
      }
    });

    statusCounts.value = counts;
  }

  async function fetchTimeoutConfig() {
    try {
      const res = await getTimeoutConfig();
      timeoutConfig.value = res.data;
    } catch (error) {
      console.error("获取超时配置失败:", error);
    }
  }

  function clearCurrentOrder() {
    currentOrder.value = null;
  }

  function updateOrderImage(orderId: number, imageUrl: string) {
    const order = orders.value.find((o) => o.id === orderId);
    if (order) order.imageUrl = imageUrl;
    const statOrder = statsOrders.value.find((o) => o.id === orderId);
    if (statOrder) statOrder.imageUrl = imageUrl;
  }

  return {
    orders,
    statsOrders,
    currentOrder,
    pagination,
    loading,
    statusCounts,
    timeoutConfig,
    orderById,
    fetchOrders,
    fetchStatsOrders,
    fetchOrderDetail,
    fetchTimeoutConfig,
    updateStatusCounts,
    clearCurrentOrder,
    updateOrderImage,
  };
});
