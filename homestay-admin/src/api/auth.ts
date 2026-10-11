import request from "@/utils/request";

export interface LoginParams {
  username: string;
  password: string;
}

export interface Admin {
  id: number;
  username: string;
  role: string;
}

export interface LoginResult {
  token: string;
  admin: Admin;
}

export function login(data: LoginParams): Promise<LoginResult> {
  return request<LoginResult>({
    url: "/api/admin/auth/login",
    method: "post",
    data,
  });
}

export function logout(): Promise<any> {
  return request({
    url: "/api/admin/auth/logout",
    method: "post",
  });
}

export function getUserInfo(): Promise<Admin> {
  return request<Admin>({
    url: "/api/admin/auth/info",
    method: "get",
  });
}
