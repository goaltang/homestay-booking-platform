import request from "@/utils/request";
import { handleApiError } from "@/utils/errorHandler";
import { OrderStatus } from "@/types/order";

/**
 * 创建订单
 * @param data 订单数据
 */
export function createOrder(data: {
  homestayId: number;
  checkInDate: string;
  checkOutDate: string;
  guestCount: number;
  totalAmount: number;
  guestName: string;
  guestPhone: string;
  remark?: string;
  quoteToken?: string;
  couponIds?: number[];
}) {
  console.log("创建订单，参数:", data);
  return request({
    url: "/api/orders",
    method: "post",
    data,
  })
    .then((response) => {
      console.log("订单创建成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("订单创建失败:", error);
      handleApiError(error, "创建订单失败，请重试");
      throw error;
    });
}

/**
 * 获取用户的订单列表
 * @param params 查询参数
 */
export function getUserOrders(params?: { page?: number; size?: number; status?: string }) {
  console.log("获取用户订单列表，参数:", params);
  return request({
    url: "/api/orders",
    method: "get",
    params,
  })
    .then((response) => {
      console.log("获取用户订单成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("获取用户订单失败:", error);
      handleApiError(error, "获取订单列表失败");

      // 开发模式下提供模拟数据
      if (process.env.NODE_ENV === "development") {
        console.warn("开发模式：使用模拟订单数据");
        const mockOrders = [
          {
            id: 1,
            orderNumber: "ORD" + Date.now().toString().substring(5),
            homestayId: 1,
            homestayTitle: "湖景度假房",
            imageUrl: "https://picsum.photos/400/300?random=1",
            guestCount: 2,
            checkInDate: new Date(Date.now() + 86400000 * 7).toISOString().split("T")[0],
            checkOutDate: new Date(Date.now() + 86400000 * 10).toISOString().split("T")[0],
            nights: 3,
            totalAmount: 1200,
            status: OrderStatus.PENDING,
            createTime: new Date().toISOString(),
          },
          {
            id: 2,
            orderNumber: "ORD" + (Date.now() - 1000000).toString().substring(5),
            homestayId: 2,
            homestayTitle: "山景小木屋",
            imageUrl: "https://picsum.photos/400/300?random=2",
            guestCount: 4,
            checkInDate: new Date(Date.now() + 86400000 * 14).toISOString().split("T")[0],
            checkOutDate: new Date(Date.now() + 86400000 * 17).toISOString().split("T")[0],
            nights: 3,
            totalAmount: 1800,
            status: OrderStatus.CONFIRMED,
            createTime: new Date(Date.now() - 86400000 * 2).toISOString(),
          },
        ];

        // 根据传入的状态参数过滤订单
        let filteredOrders = mockOrders;
        if (params && params.status && params.status !== "all") {
          filteredOrders = mockOrders.filter((order) => order.status === params.status);
        }

        return {
          data: {
            content: filteredOrders,
            totalElements: filteredOrders.length,
            totalPages: 1,
            size: filteredOrders.length,
            number: 0,
          },
        };
      }

      throw error;
    });
}

// 假设 ReviewDTO 的类型 (与 MyReviews.vue 或 ReviewDTO.java 对应)
interface ReviewItem {
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
  cleanlinessRating?: number;
  accuracyRating?: number;
  communicationRating?: number;
  locationRating?: number;
  checkInRating?: number;
  valueRating?: number;
  isPublic?: boolean;
}

// 定义订单详情的数据类型
interface OrderDetailData {
  id: number;
  orderNumber: string;
  homestayId: number;
  homestayTitle: string;
  imageUrl?: string;
  address?: string;
  guestId: number;
  guestName: string;
  guestPhone: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  guestCount: number;
  price: number;
  totalAmount: number;
  status: string;
  paymentStatus?: string;
  remark?: string;
  createTime: string;
  updateTime: string;
  completedAt?: string;
  reviewed?: boolean;
  review?: ReviewItem | null;
}

/**
 * 获取订单详情
 * @param id 订单ID
 */
export function getOrderById(id: number) {
  return request({
    url: `/api/orders/${id}`,
    method: "get",
  }).catch((error) => {
    handleApiError(error, "获取订单详情失败");
    throw error;
  });
}

/**
 * 取消订单
 * @param id 订单ID
 * @param reason 取消原因
 */
export function cancelOrder(id: number, reason?: string) {
  console.log("取消订单，ID:", id, "原因:", reason || "无");
  const requestData = reason ? { reason } : {};
  return request({
    url: `/api/orders/${id}/cancel`,
    method: "put",
    data: requestData,
  })
    .then((response) => {
      console.log("订单取消成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("订单取消失败:", error);
      handleApiError(error, "取消订单失败");
      throw error;
    });
}

/**
 * 房东确认订单
 * @param id 订单ID
 */
export function confirmOrder(id: number) {
  console.log("确认订单，ID:", id);
  return request({
    url: `/api/orders/${id}/status`,
    method: "put",
    data: { status: "CONFIRMED" },
  })
    .then((response) => {
      console.log("订单确认成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("订单确认失败:", error);
      handleApiError(error, "确认订单失败");
      throw error;
    });
}

/**
 * 房东拒绝订单
 * @param id 订单ID
 * @param reason 拒绝原因
 */
export function rejectOrder(id: number, reason: string) {
  console.log("拒绝订单，ID:", id, "原因:", reason);
  return request({
    url: `/api/orders/${id}/reject`,
    method: "put",
    data: { reason },
  })
    .then((response) => {
      console.log("订单拒绝成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("订单拒绝失败:", error);
      handleApiError(error, "拒绝订单失败");
      throw error;
    });
}

/**
 * 获取订单列表
 * @param params 查询参数
 */
export function getOrders(params?: any) {
  return request({
    url: "/api/orders",
    method: "get",
    params,
  }).catch((error) => {
    handleApiError(error, "获取订单列表失败");
    throw error;
  });
}

/**
 * 支付订单
 * @param id 订单ID
 * @param paymentMethod 支付方式
 */
export function payOrder(id: number, paymentMethod: string) {
  console.log("支付订单，ID:", id, "支付方式:", paymentMethod);
  return request({
    url: `/api/orders/${id}/pay`,
    method: "post",
    data: { paymentMethod },
  })
    .then((response) => {
      console.log("订单支付成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("订单支付失败:", error);
      handleApiError(error, "支付失败，请重试");
      throw error;
    });
}

/**
 * 订单办理入住
 * @param id 订单ID
 */
export function checkInOrder(id: number) {
  console.log("订单办理入住，ID:", id);
  return request({
    url: `/api/orders/${id}/check-in`,
    method: "put",
  })
    .then((response) => {
      console.log("入住办理成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("入住办理失败:", error);
      handleApiError(error, "办理入住失败");
      throw error;
    });
}

/**
 * 订单办理退房
 * @param id 订单ID
 */
export function checkOutOrder(id: number) {
  console.log("订单办理退房，ID:", id);
  return request({
    url: `/api/orders/${id}/check-out`,
    method: "put",
  })
    .then((response) => {
      console.log("退房办理成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("退房办理失败:", error);
      handleApiError(error, "办理退房失败");
      throw error;
    });
}

/**
 * 评价订单
 * @param id 订单ID
 * @param data 评价数据
 */
export function reviewOrder(id: number, data: any) {
  return request({
    url: `/api/orders/${id}/review`,
    method: "post",
    data,
  }).catch((error) => {
    handleApiError(error, "提交评价失败");
    throw error;
  });
}

// 获取待处理订单数量
export function getPendingOrderCount() {
  return request({
    url: "/api/orders/pending/count",
    method: "get",
  }).catch((error) => {
    handleApiError(error, "获取待处理订单数量失败");
    throw error;
  });
}

/**
 * 创建订单预览
 * @param data 订单预览数据
 */
export function createOrderPreview(data: {
  homestayId: number;
  checkInDate: string;
  checkOutDate: string;
  guestCount: number;
  guestPhone?: string;
  remark?: string;
}) {
  console.log("创建订单预览，参数:", data);
  return request({
    url: "/api/orders/preview",
    method: "post",
    data,
  })
    .then((response) => {
      console.log("订单预览创建成功:", response.data);
      return response;
    })
    .catch((error) => {
      console.error("订单预览创建失败:", error);
      handleApiError(error, "创建订单预览失败");
      throw error;
    });
}

// 获取订单详情
export function getOrderDetail(id: number) {
  return request<OrderDetailData>({
    url: `/api/orders/${id}`,
    method: "get",
  });
}

// 生成支付二维码
export function generatePaymentQRCode(data: { orderId: number; method: string }) {
  return request({
    url: `/api/payment/${data.orderId}/create?method=${data.method}`,
    method: "post",
    timeout: 60000, // 支付接口使用60秒超时
  }).catch((error) => {
    handleApiError(error, "生成支付二维码失败");
    throw error;
  });
}

// 检查支付状态
export function checkPayment(orderId: number) {
  return request({
    url: `/api/payment/${orderId}/status`,
    method: "get",
  }).catch((error) => {
    handleApiError(error, "检查支付状态失败");
    throw error;
  });
}

// 获取订单列表
export function getOrderList(params?: { status?: string; page?: number; size?: number }) {
  return request({
    url: "/api/orders",
    method: "get",
    params,
  }).catch((error) => {
    handleApiError(error, "获取订单列表失败");
    throw error;
  });
}

/**
 * 获取我的订单列表
 * @param params 查询参数
 */
export function getMyOrders(params?: {
  page?: number;
  size?: number;
  status?: string;
  tab?: string;
}) {
  console.log("获取我的订单列表，参数:", params);
  return request({
    url: "/api/orders",
    method: "get",
    params,
  })
    .then((response) => {
      console.log("获取我的订单成功:", response.data);

      // 处理不同格式的响应
      let result = response.data;

      // 处理嵌套响应
      if (response.data && response.data.data) {
        result = response.data;
      }
      // 处理直接返回数组的情况
      else if (Array.isArray(response.data)) {
        result = {
          data: {
            content: response.data,
            totalElements: response.data.length,
            totalPages: 1,
            size: response.data.length,
            number: 0,
          },
        };
      }
      // 处理简化的分页响应
      else if (response.data && response.data.content) {
        result = {
          data: response.data,
        };
      }

      return { data: result };
    })
    .catch((error) => {
      console.error("获取我的订单失败:", error);
      handleApiError(error, "获取我的订单失败");

      // 开发模式下提供模拟数据
      if (process.env.NODE_ENV === "development") {
        console.warn("开发模式：使用模拟订单数据");
        const mockOrders = [
          {
            id: 1,
            orderNumber: "ORD" + Date.now().toString().substring(5),
            homestayId: 1,
            homestayTitle: "湖景度假房",
            imageUrl: "https://picsum.photos/400/300?random=1",
            location: "杭州西湖区",
            guestCount: 2,
            checkInDate: new Date(Date.now() + 86400000 * 7).toISOString().split("T")[0],
            checkOutDate: new Date(Date.now() + 86400000 * 10).toISOString().split("T")[0],
            nights: 3,
            totalAmount: 1200,
            price: 350,
            cleaningFee: 100,
            serviceFee: 50,
            status: OrderStatus.PENDING,
            createTime: new Date().toISOString(),
          },
          {
            id: 2,
            orderNumber: "ORD" + (Date.now() - 1000000).toString().substring(5),
            homestayId: 2,
            homestayTitle: "山景小木屋",
            imageUrl: "https://picsum.photos/400/300?random=2",
            location: "莫干山度假区",
            guestCount: 4,
            checkInDate: new Date(Date.now() + 86400000 * 14).toISOString().split("T")[0],
            checkOutDate: new Date(Date.now() + 86400000 * 17).toISOString().split("T")[0],
            nights: 3,
            totalAmount: 1800,
            price: 500,
            cleaningFee: 150,
            serviceFee: 150,
            status: OrderStatus.CONFIRMED,
            createTime: new Date(Date.now() - 86400000 * 2).toISOString(),
          },
          {
            id: 3,
            orderNumber: "ORD" + (Date.now() - 2000000).toString().substring(5),
            homestayId: 3,
            homestayTitle: "海滨别墅",
            imageUrl: "https://picsum.photos/400/300?random=3",
            location: "三亚亚龙湾",
            guestCount: 6,
            checkInDate: new Date(Date.now() - 86400000 * 10).toISOString().split("T")[0],
            checkOutDate: new Date(Date.now() - 86400000 * 5).toISOString().split("T")[0],
            nights: 5,
            totalAmount: 3500,
            price: 600,
            cleaningFee: 200,
            serviceFee: 200,
            status: OrderStatus.COMPLETED,
            createTime: new Date(Date.now() - 86400000 * 15).toISOString(),
          },
        ];

        // 根据传入的状态参数过滤订单
        let filteredOrders = mockOrders;
        if (params && params.status && params.status !== "all") {
          filteredOrders = mockOrders.filter((order) => order.status === params.status);
        }

        return {
          data: {
            data: {
              content: filteredOrders,
              totalElements: filteredOrders.length,
              totalPages: 1,
              size: filteredOrders.length,
              number: 0,
            },
          },
        };
      }

      throw error;
    });
}

/**
 * 获取订单超时配置
 */
export function getTimeoutConfig() {
  return request({
    url: "/api/orders/timeout-config",
    method: "get",
  }).catch((error) => {
    handleApiError(error, "获取超时配置失败");
    throw error;
  });
}

/**
 * [测试用] 模拟支付成功
 * @param orderId 订单ID
 */
export function mockPaymentSuccess(orderId: number) {
  console.log(`[测试] 模拟订单 ${orderId} 支付成功...`);
  return request({
    url: `/api/payment/${orderId}/mock-success`,
    method: "post",
  })
    .then((response) => {
      console.log(`[测试] 模拟订单 ${orderId} 支付成功完成:`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`[测试] 模拟订单 ${orderId} 支付成功失败:`, error);
      handleApiError(error, "模拟支付成功失败");
      throw error;
    });
}

// ==================== 用户端入住相关API ====================

/**
 * 获取入住凭证
 * @param orderId 订单ID
 */
export function getCheckInCredential(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/checkin-credential`,
    method: "get",
  }).catch((error) => {
    handleApiError(error, "获取入住凭证失败");
    throw error;
  });
}

/**
 * 自助入住（输入入住码）
 * @param checkInCode 入住码
 */
export function selfCheckIn(checkInCode: string) {
  return request({
    url: "/api/orders/checkin/self",
    method: "post",
    data: { checkInCode },
  }).catch((error) => {
    handleApiError(error, "自助入住失败");
    throw error;
  });
}

/**
 * 确认到达（通知房东）
 * @param orderId 订单ID
 */
export function confirmArrival(orderId: number) {
  return request({
    url: `/api/orders/${orderId}/confirm-arrival`,
    method: "put",
  }).catch((error) => {
    handleApiError(error, "确认到达失败");
    throw error;
  });
}

/**
 * 实时计算订单价格（通过后端动态配置算费）
 */
export function calculateOrderPrice(data: {
  homestayId: number;
  checkInDate: string;
  checkOutDate: string;
  guestCount: number;
}) {
  return request({
    url: "/api/orders/calculate-price",
    method: "post",
    data,
  }).catch((error) => {
    handleApiError(error, "获取实时价格失败");
    throw error;
  });
}
