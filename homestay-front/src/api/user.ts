import request from "../utils/request";

// 用户登录
export function login(data: any) {
  return request({
    url: "/api/auth/login",
    method: "post",
    data,
  });
}

// 用户注册
export function register(data: any) {
  return request({
    url: "/api/auth/register",
    method: "post",
    data,
  });
}

// 获取用户信息
export function getUserInfo(userId?: number) {
  return request({
    url: userId ? `/api/user/${userId}` : "/api/auth/current",
    method: "get",
  }).catch((error) => {
    console.error("获取用户信息失败:", error);
    // 返回一个默认的响应以防止程序崩溃
    return {
      data: {
        username: "",
        email: "",
        phone: "",
        realName: "",
      },
    };
  });
}

// 更新用户信息
export function updateUserInfo(data: any) {
  return request({
    url: "/api/user/info",
    method: "put",
    data,
  });
}

// 修改密码
export function changePassword(data: any) {
  return request({
    url: "/api/auth/change-password",
    method: "post",
    data,
  });
}
