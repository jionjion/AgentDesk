/** 个人资料 */
export interface ProfileInfo {
  id: number
  username: string
  nickname: string
  avatar: string
}

/** 修改个人资料请求 */
export interface UpdateProfileRequest {
  nickname: string
  avatar: string
}

/** 修改密码请求 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 模型配置 */
export interface ModelSettings {
  provider: 'dashscope'
  modelName: string
  temperature: number
  maxTokens: number
  topP: number
  systemPrompt: string
}

/** 应用偏好 */
export interface AppSettings {
  theme: 'light' | 'dark' | 'auto'
  language: 'zh-CN' | 'en-US'
  sendKey: 'Enter' | 'Ctrl+Enter'
  fontSize: number
}

/** 完整设置响应 */
export interface SettingsResponse {
  profile: ProfileInfo
  model: ModelSettings
  app: AppSettings
}
