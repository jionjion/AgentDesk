import request from './request'
import type {AuthResponse, LoginRequest, RegisterRequest, UserInfo} from '@/types/auth'

/** 登录 */
export function login(data: LoginRequest) {
    return request.post<AuthResponse>('/api/auth/login', data)
}

/** 注册 */
export function register(data: RegisterRequest) {
    return request.post<AuthResponse>('/api/auth/register', data)
}

/** 获取当前用户信息 */
export function getMe() {
    return request.get<UserInfo>('/api/auth/me')
}
