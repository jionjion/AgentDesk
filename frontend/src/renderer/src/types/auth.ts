/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 注册请求 */
export interface RegisterRequest {
  username: string
  password: string
  nickname: string
}

/** 认证响应 (登录/注册) */
export interface AuthResponse {
  id: number
  username: string
  nickname: string
  avatar: string
  token: string
}

/** 用户信息 */
export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
}
