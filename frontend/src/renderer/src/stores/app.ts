import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {useAuthStore} from './auth'

export type ThemeMode = 'light' | 'dark' | 'auto'

export const useAppStore = defineStore('app', () => {
    const sidebarCollapsed = ref(false)
    const currentUser = computed(() => {
        const authStore = useAuthStore()
        return authStore.user
            ? {
                id: String(authStore.user.id),
                name: authStore.user.nickname || authStore.user.username,
                avatar: authStore.user.avatar || '',
                plan: 'Pro Plan'
            }
            : {id: '', name: '', avatar: '', plan: ''}
    })
    const theme = ref<ThemeMode>(
        (localStorage.getItem('theme') as ThemeMode) || 'light'
    )

    function toggleSidebar() {
        sidebarCollapsed.value = !sidebarCollapsed.value
    }

    function setTheme(mode: ThemeMode) {
        theme.value = mode
        localStorage.setItem('theme', mode)
        applyTheme()
    }

    function initTheme() {
        applyTheme()
        // 监听系统主题变化，auto 模式下自动切换
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
            if (theme.value === 'auto') applyTheme()
        })
    }

    function applyTheme() {
        const isDark = theme.value === 'dark' ||
            (theme.value === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches)
        document.documentElement.classList.toggle('dark', isDark)
    }

    return {
        sidebarCollapsed,
        currentUser,
        theme,
        toggleSidebar,
        setTheme,
        initTheme
    }
})
