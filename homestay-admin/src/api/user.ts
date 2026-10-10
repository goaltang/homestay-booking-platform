import request from "@/utils/request";
import type { User, UserSearchParams } from "@/types";
import { adaptPageParams, adaptPageResponse, adaptUserItem } from "@/utils/adapter";

// 获取用户列表
export function getUserList(params: UserSearchParams) {
  // 调整前端参数为后端格式
  const adaptedParams = adaptPageParams(params);

  return request<{
    content: any[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }>({
    url: "/api/admin/users",
    method: "get",
    params: adaptedParams,
  }).then((response) => {
    // 将后端响应格式转换为前端期望的格式
    return adaptPageResponse(response, adaptUserItem);
  });
}

// 获取用户详情
export function getUserDetail(id: number) {
  return request<any>({
    url: `/api/admin/users/${id}`,
    method: "get",
  }).then((response) => {
    return adaptUserItem(response);
  });
}

// 创建用户
export function createUser(data: any) {
  // Convert frontend data to backend format
  // Ensure required fields are present and provide defaults if necessary
  const adaptedData: any = {
    username: data.username,
    email: data.email,
    password: data.password, // Assuming password is part of the creation data
    phone: data.phone, // 修复：后端字段是 phone，不是 phoneNumber
    nickname: data.nickname,
    role: data.userType || "ROLE_USER", // 使用 userType 并设置默认值为 ROLE_USER
    // Set enabled, default to true if not provided in data
    enabled: typeof data.enabled === "boolean" ? data.enabled : true,
    // Copy other potential fields from User type excluding id and status
    realName: data.realName,
    avatar: data.avatar,
    // DO NOT include status here if User type still has it
  };

  console.log("Creating user with adapted data:", adaptedData);

  return request<any>({
    url: "/api/admin/users",
    method: "post",
    data: adaptedData,
  }).then((response) => {
    return adaptUserItem(response);
  });
}

// 更新用户
export function updateUser(id: number, data: Partial<User>) {
  // Convert frontend data to backend format
  const adaptedData: any = { ...data };

  // 处理用户类型字段映射
  if ("userType" in adaptedData) {
    adaptedData.role = adaptedData.userType;
    delete adaptedData.userType;
  }

  // 手机号字段在后端就是 phone，不需要转换
  // if ("phone" in adaptedData) {
  //   adaptedData.phoneNumber = adaptedData.phone;
  //   delete adaptedData.phone;
  // }

  // Send the boolean `enabled` value directly if present
  if ("enabled" in adaptedData) {
    // Assuming frontend User type uses 'enabled'
    console.log(`Updating user ${id}, enabled status received: ${adaptedData.enabled}`);
  } else if ("status" in adaptedData) {
    // Handle legacy 'status' if it exists and convert it
    console.warn("Received legacy 'status' field, converting to 'enabled'");
    adaptedData.enabled = adaptedData.status === "1"; // Example conversion
    delete adaptedData.status;
  }

  return request<any>({
    url: `/api/admin/users/${id}`,
    method: "put",
    data: adaptedData,
  }).then((response) => {
    return adaptUserItem(response);
  });
}

// 删除用户
export function deleteUser(id: number) {
  return request({
    url: `/api/admin/users/${id}`,
    method: "delete",
  });
}

// 更新用户状态
export function updateUserStatus(id: number, enabled: boolean) {
  // Parameter changed to boolean
  console.log(`Updating enabled status for user ${id} to: ${enabled}`);
  return request({
    url: `/api/admin/users/${id}/status`,
    method: "put",
    // Backend endpoint expects { enabled: boolean }
    data: { enabled: enabled },
  });
}

// 重置密码
export function resetUserPassword(id: number) {
  return request<{ newPassword: string }>({
    url: `/api/admin/users/${id}/reset-password`,
    method: "post",
  });
}

// 新增: 批量删除用户
export function batchDeleteUsers(ids: number[]) {
  return request({
    url: `/api/admin/users/batch`,
    method: "delete",
    data: { ids },
  });
}

// 新增: 批量更新用户状态
export function batchUpdateUserStatus(ids: number[], enabled: boolean) {
  // Parameter changed to boolean
  console.log(`Batch updating enabled status for ids ${ids} to: ${enabled}`);

  return request({
    url: `/api/admin/users/batch/status`,
    method: "put",
    data: {
      ids,
      // Backend endpoint expects { enabled: boolean }
      enabled: enabled,
    },
  });
}

// 新增: 批量重置密码
export function batchResetUserPasswords(ids: number[]) {
  return request<{ [id: string]: string }>({
    url: `/api/admin/users/batch/reset-password`,
    method: "post",
    data: { ids },
  });
}

// 新增: 身份验证相关接口

// 获取待审核的身份验证列表
export function getVerificationList(params: any) {
  // 调整前端参数为后端格式
  const adaptedParams = adaptPageParams(params);

  return request<{
    content: any[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }>({
    url: "/api/admin/verifications",
    method: "get",
    params: adaptedParams,
  }).then((response) => {
    // 将后端响应格式转换为前端期望的格式
    return adaptPageResponse(response, (item: any) => {
      return {
        id: item.id,
        userId: item.userId,
        username: item.username,
        realName: item.realName || "",
        idCard: item.idCard || "",
        idCardFront: item.idCardFront || "",
        idCardBack: item.idCardBack || "",
        status: item.status || "PENDING",
        submitTime: item.submitTime || "",
        reviewTime: item.reviewTime || "",
        reviewNote: item.reviewNote || "",
      };
    });
  });
}

// 获取单个身份验证详情
export function getVerificationDetail(id: number) {
  return request<any>({
    url: `/api/admin/verifications/${id}`,
    method: "get",
  });
}

// 审核通过
export function approveVerification(id: number, note?: string) {
  return request({
    url: `/api/admin/verifications/${id}/approve`,
    method: "post",
    data: { note },
  });
}

// 审核拒绝
export function rejectVerification(id: number, note: string) {
  return request({
    url: `/api/admin/verifications/${id}/reject`,
    method: "post",
    data: { note },
  });
}
