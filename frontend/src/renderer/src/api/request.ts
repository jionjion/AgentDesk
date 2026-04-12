import axios from 'axios'
import router from '@/router'
import {useToast} from '@/components/ui/toast'

const request = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    timeout: 10000
})

// 请求拦截器 — 注入 Token
request.interceptors.request.use((config) => {
    const token = localStorage.getItem('auth_token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// 错误提示去重：同类错误 2 秒内只弹一次
const lastToastTime: Record<string, number> = {}

function deduplicatedToast(key: string, options: Parameters<ReturnType<typeof useToast>['toast']>[0]) {
    const now = Date.now()
    if (now - (lastToastTime[key] || 0) < 2000) return
    lastToastTime[key] = now
    const {toast} = useToast()
    toast(options)
}

// 响应拦截器 — 统一错误提示
request.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error.response?.status

        if (status === 401) {
            // 登录/注册接口的 401 由页面自行处理，不弹全局提示
            const url = error.config?.url || ''
            if (!url.includes('/auth/login') && !url.includes('/auth/register')) {
                localStorage.removeItem('auth_token')
                router.push('/login')
                deduplicatedToast('401', {title: '登录已过期', description: '请重新登录', variant: 'warning'})
            }
        } else if (status === 403) {
            deduplicatedToast('403', {title: '没有权限', description: '您没有权限执行此操作', variant: 'destructive'})
        } else if (status === 404) {
            deduplicatedToast('404', {title: '资源不存在', description: '请求的资源未找到', variant: 'warning'})
        } else if (status && status >= 500) {
            deduplicatedToast('500', {title: '服务器错误', description: '服务器异常，请稍后重试', variant: 'destructive'})
        } else if (!error.response) {
            deduplicatedToast('network', {title: '网络连接失败', description: '请检查网络连接', variant: 'destructive'})
        }

        return Promise.reject(error)
    }
)

export default request
