import {defineStore} from 'pinia'
import {ref} from 'vue'
import {changePassword, getSettings, updateAppSettings, updateModelSettings, updateProfile, uploadAvatar} from '@/api/settings'
import {useAppStore} from './app'
import type {AppSettings, ChangePasswordRequest, ModelSettings, ProfileInfo, UpdateProfileRequest} from '@/types/settings'

/** 模型配置默认值 */
const DEFAULT_MODEL: ModelSettings = {
    provider: 'dashscope',
    modelName: 'qwen-plus',
    temperature: 0.7,
    maxTokens: 4096,
    topP: 0.9,
    systemPrompt: ''
}

/** 应用偏好默认值 */
const DEFAULT_APP: AppSettings = {
    theme: 'auto',
    language: 'zh-CN',
    sendKey: 'Enter',
    fontSize: 14
}

export const useSettingsStore = defineStore('settings', () => {
    const profile = ref<ProfileInfo | null>(null)
    const model = ref<ModelSettings>({...DEFAULT_MODEL})
    const app = ref<AppSettings>({...DEFAULT_APP})
    const loaded = ref(false)

    /** 加载全部设置 */
    async function fetchSettings() {
        const res = await getSettings()
        profile.value = res.data.profile
        model.value = res.data.model
        app.value = res.data.app
        loaded.value = true

        // 应用主题和字体
        const appStore = useAppStore()
        appStore.setTheme(res.data.app.theme)
        applyFontSize(res.data.app.fontSize)
    }

    /** 修改个人资料 */
    async function saveProfile(data: UpdateProfileRequest) {
        const res = await updateProfile(data)
        profile.value = res.data
    }

    /** 修改密码 */
    async function doChangePassword(data: ChangePasswordRequest) {
        await changePassword(data)
    }

    /** 上传头像 */
    async function doUploadAvatar(file: File) {
        const res = await uploadAvatar(file)
        profile.value = res.data
        return res.data
    }

    /** 修改模型配置 */
    async function saveModelSettings(data: ModelSettings) {
        const res = await updateModelSettings(data)
        model.value = res.data
    }

    /** 修改应用偏好 */
    async function saveAppSettings(data: AppSettings) {
        const res = await updateAppSettings(data)
        app.value = res.data

        const appStore = useAppStore()
        appStore.setTheme(res.data.theme)
        applyFontSize(res.data.fontSize)
    }

    return {
        profile, model, app, loaded,
        fetchSettings, saveProfile, doChangePassword, doUploadAvatar,
        saveModelSettings, saveAppSettings
    }
})

/** 应用字体大小到 CSS 变量 */
function applyFontSize(size: number) {
    document.documentElement.style.setProperty('--chat-font-size', `${size}px`)
}
