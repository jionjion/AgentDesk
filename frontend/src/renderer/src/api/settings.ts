import request from './request'
import type {
  SettingsResponse,
  UpdateProfileRequest,
  ChangePasswordRequest,
  ModelSettings,
  AppSettings,
  ProfileInfo
} from '@/types/settings'

/** 获取全部设置 */
export function getSettings() {
  return request.get<SettingsResponse>('/api/settings')
}

/** 修改个人资料 */
export function updateProfile(data: UpdateProfileRequest) {
  return request.put<ProfileInfo>('/api/settings/profile', data)
}

/** 修改密码 */
export function changePassword(data: ChangePasswordRequest) {
  return request.put<{ message: string }>('/api/settings/password', data)
}

/** 修改模型配置 */
export function updateModelSettings(data: ModelSettings) {
  return request.put<ModelSettings>('/api/settings/model', data)
}

/** 修改应用偏好 */
export function updateAppSettings(data: AppSettings) {
  return request.put<AppSettings>('/api/settings/app', data)
}

/** 上传头像 */
export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ProfileInfo>('/api/settings/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
