import request from "@/utils/request";

/**
 * 获取统计数据
 */
export function getStatistics() {
  return request({
    url: "/api/admin/statistics",
    method: "get",
  }) as Promise<{
    total: {
      homestays: number;
      orders: number;
      users: number;
    };
    period: {
      newHomestays: number;
      newOrders: number;
      newUsers: number;
    };
    today: {
      newHomestays: number;
      newOrders: number;
      newUsers: number;
    };
    yesterday: {
      newHomestays: number;
      newOrders: number;
      newUsers: number;
    };
  }>;
}

/**
 * 获取订单趋势数据
 */
export function getOrderTrend(days: number = 7) {
  // 计算开始日期
  const endDate = new Date();
  const startDate = new Date();
  startDate.setDate(startDate.getDate() - days + 1);

  // 格式化日期
  const formatDate = (date: Date) => {
    return date.toISOString().split("T")[0]; // 格式为 YYYY-MM-DD
  };

  return request({
    url: "/api/admin/statistics/order-trend",
    method: "get",
    params: {
      startDate: formatDate(startDate),
      endDate: formatDate(endDate),
    },
  }) as Promise<{
    dates: string[];
    counts: number[];
  }>;
}

/**
 * 获取房源分布数据
 */
export function getHomestayDistribution() {
  return request({
    url: "/api/admin/statistics/homestay-distribution",
    method: "get",
  }) as Promise<{
    provinces: string[];
    counts: number[];
  }>;
}

/**
 * 获取用户增长数据
 */
export function getUserGrowth(days: number = 30) {
  // 计算开始日期
  const endDate = new Date();
  const startDate = new Date();
  startDate.setDate(startDate.getDate() - days + 1);

  // 格式化日期
  const formatDate = (date: Date) => {
    return date.toISOString().split("T")[0]; // 格式为 YYYY-MM-DD
  };

  return request({
    url: "/api/admin/statistics/user-trend",
    method: "get",
    params: {
      startDate: formatDate(startDate),
      endDate: formatDate(endDate),
    },
  }) as Promise<{
    dates: string[];
    counts: number[];
  }>;
}
