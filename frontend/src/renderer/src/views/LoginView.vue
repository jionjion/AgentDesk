<template>
  <div class="h-screen w-screen flex overflow-hidden">
    <!-- 左侧 -->
    <div class="flex-1 flex flex-col items-start justify-center px-16">
      <!-- Logo -->
      <div class="w-12 h-12 bg-green-600 rounded-xl flex items-center justify-center mb-6">
        <Bot :size="28" class="text-white" />
      </div>

      <h1 class="text-3xl font-bold text-gray-900 mb-2">欢迎使用搭子</h1>
      <p class="text-sm text-green-600 mb-4">面向所有人的 AI 桌面助手</p>
      <p class="text-sm text-gray-500 leading-relaxed mb-8 max-w-md">
        Agent-Desk 将 Agent 能力从代码领域扩展到日常工作场景，描述需求，自动执行，直接交付结果。
      </p>

      <!-- 表单 -->
      <form class="w-full max-w-sm space-y-4" @submit.prevent="handleSubmit">
        <div>
          <input
            v-model="form.username"
            type="text"
            placeholder="用户名"
            required
            class="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent"
          />
        </div>
        <div>
          <input
            v-model="form.password"
            type="password"
            placeholder="密码"
            required
            class="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent"
          />
        </div>
        <div v-if="isRegister">
          <input
            v-model="form.nickname"
            type="text"
            placeholder="昵称"
            required
            class="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-transparent"
          />
        </div>

        <!-- 错误提示 -->
        <p v-if="errorMsg" class="text-red-500 text-sm">{{ errorMsg }}</p>

        <button
          type="submit"
          :disabled="loading"
          class="w-full py-2.5 bg-gray-900 text-white text-sm font-medium rounded-lg hover:bg-gray-800 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ loading ? '请稍候...' : (isRegister ? '注册' : '登录') }}
        </button>
      </form>

      <p class="mt-4 text-sm text-gray-500">
        <span v-if="!isRegister">
          还没有账号?
          <button class="text-green-600 hover:underline" @click="toggleMode">去注册</button>
        </span>
        <span v-else>
          已有账号?
          <button class="text-green-600 hover:underline" @click="toggleMode">去登录</button>
        </span>
      </p>
    </div>

    <!-- 右侧装饰区 -->
    <div class="w-[480px] bg-green-100 flex items-center justify-center relative overflow-hidden">
      <div class="absolute inset-0 opacity-30">
        <svg class="w-full h-full" viewBox="0 0 480 720" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path v-for="i in 20" :key="i" :d="`M${i * 24} 0 Q${i * 24 + 12} 360 ${i * 24} 720`" stroke="#22c55e" stroke-width="1.5" fill="none" />
        </svg>
      </div>
      <p class="relative z-10 text-green-800 text-lg font-medium text-center leading-relaxed px-12">
        Go from answers to action with<br />your agentic work partner
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Bot } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const isRegister = ref(false)
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

function toggleMode() {
  isRegister.value = !isRegister.value
  errorMsg.value = ''
}

async function handleSubmit() {
  loading.value = true
  errorMsg.value = ''
  try {
    if (isRegister.value) {
      await authStore.doRegister(form)
    } else {
      await authStore.doLogin({ username: form.username, password: form.password })
    }
    router.push('/chat')
  } catch (err: any) {
    errorMsg.value = err.response?.data?.error || '操作失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>
