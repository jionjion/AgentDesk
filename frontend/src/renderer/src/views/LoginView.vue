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

      <div class="flex items-center gap-3">
        <button
          class="px-8 py-2.5 bg-gray-900 text-white text-sm font-medium rounded-lg hover:bg-gray-800 transition-colors"
          @click="handleLogin"
        >
          登录/注册
        </button>
        <div class="text-sm text-gray-400">中文</div>
      </div>
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
import { useRouter } from 'vue-router'
import { Bot } from 'lucide-vue-next'

const router = useRouter()

function handleLogin() {
  router.push('/chat')
}
</script>
