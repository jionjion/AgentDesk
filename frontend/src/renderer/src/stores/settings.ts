import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {changePassword, getSettings, updateAppSettings, updateModelSettings, updateProfile, uploadAvatar} from '@/api/settings'
import {updateMemorySettings as apiUpdateMemorySettings} from '@/api/memory'
import {getGroupedModels} from '@/api/models'
import {useAppStore} from './app'
import type {AppSettings, ChangePasswordRequest, ModelSettings, ProfileInfo, UpdateProfileRequest} from '@/types/settings'
import type {MemorySettings} from '@/types/memory'
import type {ModelDefinition} from '@/types/model'

/** 模型配置默认值 */
const DEFAULT_MODEL: ModelSettings = {
    modelId: 'qwen3.6-plus',
    temperature: 0.7,
    maxTokens: 4096,
    topP: 0.9,
    enableThinking: false,
    systemPrompt: ''
}

/** 内置模型列表（后端 API 不可用时的 fallback） */
const BUILTIN_MODELS: Record<string, ModelDefinition[]> = {
    '旗舰模型': [
        {id: 'qwen3.6-plus', displayName: 'Qwen3.6 Plus', group: '旗舰模型', inputModalities: ['text', 'image', 'video'], supportsReasoning: true, maxContextWindow: 1000000, maxOutputTokens: 65536, description: '最新旗舰，多模态推理'},
        {id: 'qwen3.5-plus', displayName: 'Qwen3.5 Plus', group: '旗舰模型', inputModalities: ['text', 'image', 'video'], supportsReasoning: true, maxContextWindow: 1000000, maxOutputTokens: 65536, description: '上代旗舰多模态'},
        {id: 'qwen3-max', displayName: 'Qwen3 Max', group: '旗舰模型', inputModalities: ['text'], supportsReasoning: true, maxContextWindow: 262144, maxOutputTokens: 65536, description: '文本推理最强'},
        {id: 'qwen-plus', displayName: 'Qwen Plus', group: '旗舰模型', inputModalities: ['text'], supportsReasoning: true, maxContextWindow: 1000000, maxOutputTokens: 32768, description: '高性价比'}
    ],
    '轻量快速': [
        {id: 'qwen3.5-flash', displayName: 'Qwen3.5 Flash', group: '轻量快速', inputModalities: ['text', 'image', 'video'], supportsReasoning: true, maxContextWindow: 1000000, maxOutputTokens: 65536, description: '快速多模态'},
        {id: 'qwen-flash', displayName: 'Qwen Flash', group: '轻量快速', inputModalities: ['text'], supportsReasoning: true, maxContextWindow: 1000000, maxOutputTokens: 32768, description: '极速文本'},
        {id: 'qwen-turbo', displayName: 'Qwen Turbo', group: '轻量快速', inputModalities: ['text'], supportsReasoning: false, maxContextWindow: 1000000, maxOutputTokens: 8192, description: '最快响应'}
    ],
    '代码专精': [
        {id: 'qwen3-coder-plus', displayName: 'Qwen3 Coder Plus', group: '代码专精', inputModalities: ['text'], supportsReasoning: false, maxContextWindow: 1000000, maxOutputTokens: 65536, description: '代码生成 Plus'},
        {id: 'qwen3-coder-flash', displayName: 'Qwen3 Coder Flash', group: '代码专精', inputModalities: ['text'], supportsReasoning: false, maxContextWindow: 1000000, maxOutputTokens: 65536, description: '代码生成 Flash'}
    ],
    '视觉理解': [
        {id: 'qwen3-vl-plus', displayName: 'Qwen3 VL Plus', group: '视觉理解', inputModalities: ['text', 'image', 'video'], supportsReasoning: true, maxContextWindow: 262144, maxOutputTokens: 32768, description: '视觉理解 Plus'},
        {id: 'qwen3-vl-flash', displayName: 'Qwen3 VL Flash', group: '视觉理解', inputModalities: ['text', 'image', 'video'], supportsReasoning: true, maxContextWindow: 262144, maxOutputTokens: 32768, description: '视觉理解 Flash'},
        {id: 'qwen-vl-max', displayName: 'Qwen VL Max', group: '视觉理解', inputModalities: ['text', 'image', 'video'], supportsReasoning: false, maxContextWindow: 131072, maxOutputTokens: 32768, description: '视觉旗舰'}
    ],
    '推理专精': [
        {id: 'qwq-plus', displayName: 'QwQ Plus', group: '推理专精', inputModalities: ['text'], supportsReasoning: true, maxContextWindow: 131072, maxOutputTokens: 8192, description: '深度推理'},
        {id: 'qwen-deep-research', displayName: 'Qwen Deep Research', group: '推理专精', inputModalities: ['text'], supportsReasoning: false, maxContextWindow: 1000000, maxOutputTokens: 32768, description: '深度研究'}
    ],
    '全模态': [
        {id: 'qwen3.5-omni-plus', displayName: 'Qwen3.5 Omni Plus', group: '全模态', inputModalities: ['text', 'image', 'video', 'audio'], supportsReasoning: false, maxContextWindow: 262144, maxOutputTokens: 65536, description: '全模态 Plus'},
        {id: 'qwen3.5-omni-flash', displayName: 'Qwen3.5 Omni Flash', group: '全模态', inputModalities: ['text', 'image', 'video', 'audio'], supportsReasoning: false, maxContextWindow: 262144, maxOutputTokens: 65536, description: '全模态 Flash'}
    ]
}

/** 应用偏好默认值 */
const DEFAULT_APP: AppSettings = {
    theme: 'auto',
    language: 'zh-CN',
    sendKey: 'Enter',
    fontSize: 14
}

/** 长期记忆默认值 */
const DEFAULT_MEMORY: MemorySettings = {
    enabled: false
}

export const useSettingsStore = defineStore('settings', () => {
    const profile = ref<ProfileInfo | null>(null)
    const model = ref<ModelSettings>({...DEFAULT_MODEL})
    const app = ref<AppSettings>({...DEFAULT_APP})
    const memory = ref<MemorySettings>({...DEFAULT_MEMORY})
    const loaded = ref(false)

    /** 模型列表（按分组），初始使用内置列表 */
    const groupedModels = ref<Record<string, ModelDefinition[]>>({...BUILTIN_MODELS})
    /** 标记是否已从后端成功加载过 */
    const modelsLoadedFromApi = ref(false)

    /** 扁平化的全部模型列表 */
    const modelList = computed<ModelDefinition[]>(() =>
        Object.values(groupedModels.value).flat()
    )

    /** 当前选中的模型定义 */
    const currentModelDef = computed<ModelDefinition | undefined>(() =>
        modelList.value.find(m => m.id === model.value.modelId)
    )

    /** 加载全部设置 */
    async function fetchSettings() {
        const res = await getSettings()
        profile.value = res.data.profile
        model.value = res.data.model
        app.value = res.data.app
        memory.value = res.data.memory ?? {...DEFAULT_MEMORY}
        loaded.value = true

        // 应用主题和字体
        const appStore = useAppStore()
        appStore.setTheme(res.data.app.theme)
        applyFontSize(res.data.app.fontSize)
    }

    /** 加载可用模型列表（成功则用后端数据，失败则保留内置列表） */
    async function fetchModels() {
        if (modelsLoadedFromApi.value) return
        try {
            const res = await getGroupedModels()
            groupedModels.value = res.data
            modelsLoadedFromApi.value = true
        } catch {
            // 保留内置列表，不覆盖
        }
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

    /** 快速切换模型（从 ModelSelector 调用） */
    async function switchModel(modelId: string) {
        const def = modelList.value.find(m => m.id === modelId)
        const settings: ModelSettings = {
            modelId,
            temperature: model.value.temperature,
            maxTokens: def ? Math.min(model.value.maxTokens, def.maxOutputTokens) : model.value.maxTokens,
            topP: model.value.topP,
            enableThinking: def?.supportsReasoning ? model.value.enableThinking : false,
            systemPrompt: model.value.systemPrompt
        }
        // 先乐观更新本地状态，再尝试同步后端
        model.value = {...settings}
        try {
            await saveModelSettings(settings)
        } catch {
            // 后端不可用时保持本地选择
        }
    }

    /** 修改应用偏好 */
    async function saveAppSettings(data: AppSettings) {
        const res = await updateAppSettings(data)
        app.value = res.data

        const appStore = useAppStore()
        appStore.setTheme(res.data.theme)
        applyFontSize(res.data.fontSize)
    }

    /** 修改长期记忆配置 */
    async function saveMemorySettings(data: MemorySettings) {
        const res = await apiUpdateMemorySettings(data)
        memory.value = res.data
    }

    return {
        profile, model, app, memory, loaded,
        groupedModels, modelList, currentModelDef,
        fetchSettings, fetchModels,
        saveProfile, doChangePassword, doUploadAvatar,
        saveModelSettings, switchModel, saveAppSettings, saveMemorySettings
    }
})

/** 应用字体大小到 CSS 变量 */
function applyFontSize(size: number) {
    document.documentElement.style.setProperty('--chat-font-size', `${size}px`)
}
