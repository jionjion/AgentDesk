import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {getMe, login, register} from '@/api/auth'
import type {LoginRequest, RegisterRequest, UserInfo} from '@/types/auth'
import router from '@/router'
import {cacheAvatar, clearAvatarCache, getCachedAvatar} from '@/utils/avatar-cache'

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
        const avatarUrl = res.data.avatar || ''
        const cachedAvatar = avatarUrl ? await cacheAvatar(avatarUrl) : ''
        user.value = {
            id: res.data.id,
            username: res.data.username,
            nickname: res.data.nickname,
            avatar: cachedAvatar || avatarUrl
        }
    }

    /** 注册 */
    async function doRegister(data: RegisterRequest) {
        const res = await register(data)
        token.value = res.data.token
        localStorage.setItem(TOKEN_KEY, res.data.token)
        const avatarUrl = res.data.avatar || ''
        const cachedAvatar = avatarUrl ? await cacheAvatar(avatarUrl) : ''
        user.value = {
            id: res.data.id,
            username: res.data.username,
            nickname: res.data.nickname,
            avatar: cachedAvatar || avatarUrl
        }
    }

    /** 登出 */
    function doLogout() {
        token.value = ''
        user.value = null
        localStorage.removeItem(TOKEN_KEY)
        clearAvatarCache()
        router.push('/login')
    }

    /** 获取用户信息 (页面刷新时调用) */
    async function fetchUser() {
        if (!token.value) return
        try {
            const res = await getMe()
            const avatarUrl = res.data.avatar || ''
            // 优先使用本地缓存，后台异步更新
            const cached = getCachedAvatar()
            res.data.avatar = cached || avatarUrl
            user.value = res.data
            // 异步缓存最新头像
            if (avatarUrl) {
                cacheAvatar(avatarUrl).then(base64 => {
                    if (user.value && base64) {
                        user.value.avatar = base64
                    }
                })
            }
        } catch {
            doLogout()
        }
    }

    return {token, user, isLoggedIn, doLogin, doRegister, doLogout, fetchUser}
})
