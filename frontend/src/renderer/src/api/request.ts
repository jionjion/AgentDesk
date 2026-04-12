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

// 响应拦截器 — 统一错误提示
request.interceptors.response.use(
    (response) => response,
    (error) => {
        const {toast} = useToast()
        const status = error.response?.status

        if (status === 401) {
            localStorage.removeItem('auth_token')
            router.push('/login')
            toast({title: '登录已过期', description: '请重新登录', variant: 'warning'})
        } else if (status === 403) {
            toast({title: '没有权限', description: '您没有权限执行此操作', variant: 'destructive'})
        } else if (status === 404) {
            toast({title: '资源不存在', description: '请求的资源未找到', variant: 'warning'})
        } else if (status && status >= 500) {
            toast({title: '服务器错误', description: '服务器异常，请稍后重试', variant: 'destructive'})
        } else if (!error.response) {
            toast({title: '网络连接失败', description: '请检查网络连接', variant: 'destructive'})
        }

        return Promise.reject(error)
    }
)

export default request
