import request from "@/utils/request";
import api from ".";
import { handleApiError } from "@/utils/errorHandler";

// 定义房东统计数据 DTO 接口 (对应后端的 HostStatisticsDTO)
export interface HostStatisticsData {
  homestayCount: number;
  orderCount: number;
  reviewCount: number;
  rating: number | null;
  totalEarnings: number | null;
  pendingOrders: number;
  completedOrders: number;
  cancelledOrders: number;
}

// 定义订单 DTO 接口 (简化版，仅包含 Dashboard 需要的字段)
export interface HostOrderData {
  id: number;
  orderNumber?: string; // 订单号可能用于跳转
  homestayTitle: string;
  checkInDate: string;
  checkOutDate: string;
  guestName?: string; // 房客姓名，可能来自关联 User
  totalAmount: number;
  status: string;
}

// 定义分页响应接口 (通用或特定于订单)
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // 当前页码 (0-based)
  size: number;
  // ... 其他分页信息
}

// 获取房东收益统计
export function getHostEarnings(params?: any) {
  return request({
    url: "/api/host/earnings",
    method: "get",
    params,
  });
}

// 获取房东信息
export function getHostInfo() {
  return request({
    url: "/api/host/info",
    method: "get",
  });
}

// 获取房源的房东信息
export function getHomestayHostInfo(homestayId: number) {
  console.log(`开始获取房源ID为${homestayId}的房东信息`);

  return request({
    url: `/api/host/info/${homestayId}`,
    method: "get",
  })
    .then((response) => {
      console.log(`获取房源房东信息成功，房源ID: ${homestayId}`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`获取房源房东信息失败，房源ID: ${homestayId}`, error);

      // 如果是开发环境且请求失败，返回模拟数据
      if (process.env.NODE_ENV === "development") {
        console.log("使用房东模拟数据");
        return {
          data: {
            id: 101,
            name: "张晓明",
            avatar: "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png",
            rating: 4.92,
            accommodations: 156,
            years: 5,
            responseRate: "99%",
            responseTime: "1小时内",
            companions: [
              {
                name: "李华",
                avatar: "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png",
              },
              {
                name: "王芳",
                avatar: "https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png",
              },
            ],
          },
        };
      }

      // 如果不是开发环境，继续抛出错误
      throw error;
    });
}

// 更新房东信息
export function updateHostInfo(data: any) {
  return request({
    url: "/api/host/info",
    method: "put",
    data,
  });
}

// 成为房东
export function becomeHost() {
  return request({
    url: "/api/host/register",
    method: "post",
  });
}

/**
 * 获取房东统计数据
 */
export function getHostStatistics(): Promise<HostStatisticsData> {
  return request({
    url: "/api/host/statistics",
    method: "get",
  })
    .then((response) => {
      // 假设后端直接返回 HostStatisticsDTO 对象
      if (response && response.data) {
        return response.data as HostStatisticsData;
      } else {
        throw new Error("获取房东统计数据格式无效");
      }
    })
    .catch((error) => {
      handleApiError(error, "获取房东统计数据失败");
      throw error;
    });
}

// 上传房东头像
export function uploadHostAvatar(data: FormData) {
  return request({
    url: "/api/files/upload?type=avatar",
    method: "post",
    data,
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}

// 获取房东房源列表
export function getHostHomestays(params?: any) {
  return request({
    url: "/api/host/homestays",
    method: "get",
    params,
  });
}

// 获取房东订单列表
export function getHostOrders(params?: any) {
  return request({
    url: "/api/host/orders",
    method: "get",
    params,
  });
}

// 获取房东评价列表
export function getHostReviews(params?: any) {
  return request({
    url: "/api/host/reviews",
    method: "get",
    params,
  });
}

/**
 * 更新房东信息
 * @param profileData 房东资料数据
 * @returns 更新后的房东信息
 */
export const updateHostProfile = async (profileData: any) => {
  const response = await api.put("/api/host/profile", profileData);
  return response.data;
};

/**
 * 获取房东信息
 * @returns 房东信息
 */
export const getHostProfile = async () => {
  const response = await api.get("/api/host/profile");
  return response.data;
};

/**
 * 上传房东证件照片
 * @param file 文件对象
 * @param type 文件类型 (idCardFront | idCardBack)
 * @returns 上传后的文件URL
 */
export const uploadHostDocument = async (file: File, type: string) => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("type", type);

  const response = await api.post("/api/host/upload-document", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });

  return response.data;
};

/**
 * 获取房东最近订单
 * @param limit 获取的订单数量，默认为 5
 */
export function getHostRecentOrders(limit: number = 5): Promise<HostOrderData[]> {
  return request({
    url: "/api/host/orders",
    method: "get",
    params: {
      page: 0, // 获取第一页
      size: limit, // 指定数量
    },
  })
    .then((response) => {
      // 检查分页结构
      if (response && response.data && Array.isArray(response.data.content)) {
        return response.data.content as HostOrderData[];
      } else if (response && Array.isArray(response.data)) {
        // 兼容直接返回数组的情况 (取前 limit 个)
        return (response.data as HostOrderData[]).slice(0, limit);
      } else {
        throw new Error("获取房东订单数据格式无效");
      }
    })
    .catch((error) => {
      handleApiError(error, "获取最近订单失败");
      throw error;
    });
}

/**
 * 获取房东本月收入
 */
export function getHostMonthlyEarnings(): Promise<number> {
  return request({
    url: "/api/host/earnings/monthly",
    method: "get",
  })
    .then((response) => {
      // 假设后端直接返回 BigDecimal 或 Number
      if (response && (typeof response.data === "number" || !isNaN(parseFloat(response.data)))) {
        return Number(response.data);
      } else {
        // 如果返回的是空或无效数据，则返回 0
        console.warn("获取本月收入返回无效数据:", response?.data);
        return 0;
      }
    })
    .catch((error) => {
      handleApiError(error, "获取本月收入失败");
      // 出错时返回 0
      return 0;
      // 或者可以选择抛出错误: throw error;
    });
}

/**
 * 获取房东的房源选项列表 (用于下拉菜单)
 */
export function getHostHomestayOptions(): Promise<{ id: number; title: string }[]> {
  return request({
    url: "/api/host/homestay-options",
    method: "get",
  })
    .then((response) => {
      // 假设后端直接返回 [{id: 1, title: "..."}, ...]
      if (response && Array.isArray(response.data)) {
        return response.data as { id: number; title: string }[];
      } else {
        console.error("获取房源选项数据格式无效", response);
        return []; // 返回空数组避免后续错误
      }
    })
    .catch((error) => {
      handleApiError(error, "获取房源选项失败");
      return []; // 出错时也返回空数组
    });
}
