import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {getMe, login, register} from '@/api/auth'
import type {LoginRequest, RegisterRequest, UserInfo} from '@/types/auth'
import router from '@/router'

const TOKEN_KEY = 'auth_token'

export const useAuthStore = defineStore('auth', () => {
    const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
    const user = ref<UserInfo | null>(null)

    const isLoggedIn = computed(() => !!token.value)

    /** 登录 */
    async function doLogin(data: LoginRequest) {
        const res = await login(data)
        token.value = res.data.token
        localStorage.setItem(TOKEN_KEY, res.data.token)
        user.value = {
            id: res.data.id,
            username: res.data.username,
            nickname: res.data.nickname,
            avatar: res.data.avatar
        }
    }

    /** 注册 */
    async function doRegister(data: RegisterRequest) {
        const res = await register(data)
        token.value = res.data.token
        localStorage.setItem(TOKEN_KEY, res.data.token)
        user.value = {
            id: res.data.id,
            username: res.data.username,
            nickname: res.data.nickname,
            avatar: res.data.avatar || ''
        }
    }

    /** 登出 */
    function doLogout() {
        token.value = ''
        user.value = null
        localStorage.removeItem(TOKEN_KEY)
        router.push('/login')
    }

    /** 获取用户信息 (页面刷新时调用) */
    async function fetchUser() {
        if (!token.value) return
        try {
            const res = await getMe()
            user.value = res.data
        } catch {
            doLogout()
        }
    }

    return {token, user, isLoggedIn, doLogin, doRegister, doLogout, fetchUser}
})
