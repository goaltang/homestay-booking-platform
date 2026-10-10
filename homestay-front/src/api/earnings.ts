import request from "@/utils/request";

export interface EarningsQueryParams {
  startDate?: string | null;
  endDate?: string | null;
  homestayId?: number | null;
  type?: "daily" | "monthly";
  format?: string;
  page?: number;
  size?: number;
}

export interface EarningsSummary {
  totalEarnings: number;
  totalOrders: number;
  averagePerOrder: number;
}

export interface EarningsDetail {
  orderNumber: string;
  homestayTitle: string;
  guestName: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  amount: number;
  status: string;
  createTime: string;
}

export interface TrendData {
  labels: string[];
  values: number[];
}

export interface PaginationResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// 银行账户信息接口
export interface BankAccount {
  id: number;
  accountName: string;
  accountNumber: string;
  bankName: string;
  bankBranch?: string;
  accountType: "BANK" | "ALIPAY" | "WECHAT";
  isDefault: boolean;
  createTime: string;
}

// 提现记录接口
export interface Withdrawal {
  id: number;
  amount: number;
  status: "PENDING" | "COMPLETED" | "REJECTED";
  createTime: string;
  completeTime?: string;
  accountInfo: string;
  remark?: string;
}

// 获取收益汇总数据
export const getEarningsSummary = (params: EarningsQueryParams): Promise<EarningsSummary> => {
  console.log("请求收益汇总数据，参数:", params);
  return request
    .get("/api/host/earnings/summary", { params })
    .then((response) => {
      console.log("收益汇总数据获取成功:", response.data);
      return response.data;
    })
    .catch((error) => {
      console.error("收益汇总数据获取失败:", error);
      throw error;
    });
};

// 获取收益明细
export const getEarningsDetail = (
  params: EarningsQueryParams
): Promise<PaginationResponse<EarningsDetail>> => {
  console.log("请求收益明细数据，参数:", params);
  return request
    .get("/api/host/earnings/detail", { params })
    .then((response) => {
      console.log("收益明细数据获取成功:", response.data);
      return response.data;
    })
    .catch((error) => {
      console.error("收益明细数据获取失败:", error);
      throw error;
    });
};

// 获取收益趋势数据
export const getEarningsTrend = (params: EarningsQueryParams): Promise<TrendData> => {
  console.log("请求收益趋势数据，参数:", params);
  return request
    .get("/api/host/earnings/trend", { params })
    .then((response) => {
      console.log("收益趋势数据获取成功:", response.data);
      return response.data;
    })
    .catch((error) => {
      console.error("收益趋势数据获取失败:", error);
      throw error;
    });
};

// 获取月度收益
export const getMonthlyEarnings = (): Promise<number> => {
  console.log("请求月度收益数据");
  return request
    .get("/api/host/earnings/monthly")
    .then((response) => {
      console.log("月度收益数据获取成功:", response.data);
      return response.data;
    })
    .catch((error) => {
      console.error("月度收益数据获取失败:", error);
      throw error;
    });
};

// 获取待结算收益
export const getPendingEarnings = (): Promise<number> => {
  console.log("请求待结算收益数据");
  return request
    .get("/api/host/earnings/pending")
    .then((response) => {
      console.log("待结算收益数据获取成功:", response.data);
      return response.data;
    })
    .catch((error) => {
      console.error("待结算收益数据获取失败:", error);
      throw error;
    });
};

// 获取可提现余额
export const getWithdrawalBalance = (): Promise<number> => {
  return request.get("/api/host/earnings/balance");
};

// 获取银行账户列表
export const getBankAccounts = (): Promise<BankAccount[]> => {
  return request.get("/api/host/earnings/accounts");
};

// 添加银行账户
export const addBankAccount = (
  data: Omit<BankAccount, "id" | "createTime">
): Promise<BankAccount> => {
  return request.post("/api/host/earnings/accounts", data);
};

// 更新银行账户
export const updateBankAccount = (id: number, data: Partial<BankAccount>): Promise<BankAccount> => {
  return request.put(`/api/host/earnings/accounts/${id}`, data);
};

// 删除银行账户
export const deleteBankAccount = (id: number): Promise<void> => {
  return request.delete(`/api/host/earnings/accounts/${id}`);
};

// 设置默认银行账户
export const setDefaultBankAccount = (id: number): Promise<BankAccount> => {
  return request.put(`/api/host/earnings/accounts/${id}/default`);
};

// 申请提现
export const requestWithdrawal = (data: {
  amount: number;
  accountId: number;
  remark?: string;
}): Promise<Withdrawal> => {
  return request.post("/api/host/earnings/withdraw", data);
};

// 获取提现记录
export const getWithdrawalHistory = (params: {
  page?: number;
  size?: number;
}): Promise<PaginationResponse<Withdrawal>> => {
  return request.get("/api/host/earnings/withdrawals", { params });
};

// 取消提现申请 (仅PENDING状态可取消)
export const cancelWithdrawal = (id: number): Promise<void> => {
  return request.delete(`/api/host/earnings/withdrawals/${id}`);
};

// 导出收益数据
export const exportEarningsData = (params: EarningsQueryParams): Promise<Blob> => {
  return request
    .get("/api/host/earnings/export", {
      params,
      responseType: "blob",
    })
    .then((response) => response.data);
};

/**
 * 手动触发结算房东收益
 */
export function settleHostEarnings(): Promise<any> {
  return request({
    url: "/api/host/earnings/settle",
    method: "post",
    // 根据后端接口的实际需要，可能需要发送空的 data: {}
    // data: {}
  });
}
