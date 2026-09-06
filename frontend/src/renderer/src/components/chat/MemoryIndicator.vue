<template>
  <div
      v-if="state && chatStore.isStreaming"
      class="mb-2 flex items-center gap-1.5 px-3 py-1.5 text-xs border rounded-lg"
      :class="tone"
  >
    <EyeOff v-if="state.status === 'DISABLED'" :size="13" class="shrink-0"/>
    <TriangleAlert v-else-if="state.status === 'DEGRADED'" :size="13" class="shrink-0"/>
    <Brain v-else :size="13" class="shrink-0"/>
    <span>{{ label }}</span>
    <span class="ml-auto opacity-60 tabular-nums">{{ state.elapsedMs }} ms</span>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Brain, EyeOff, TriangleAlert} from 'lucide-vue-next'
import {useChatStore} from '@/stores/chat'

const chatStore = useChatStore()
const state = computed(() => chatStore.memoryRecallState)

const label = computed(() => {
  if (!state.value) return ''
  switch (state.value.status) {
    case 'USED': return `已使用 ${state.value.count || state.value.references.length} 条相关记忆`
    case 'EMPTY': return '已检查记忆，没有找到相关内容'
    case 'DISABLED': return chatStore.temporaryNoMemory ? '本会话处于临时无记忆模式' : '长期记忆未启用'
    case 'DEGRADED': return '记忆服务暂时不可用，本轮已降级继续'
  }
  return ''
})

const tone = computed(() => {
  if (state.value?.status === 'DEGRADED') return 'border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400'
  if (state.value?.status === 'DISABLED' || state.value?.status === 'EMPTY') return 'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 text-gray-500 dark:text-gray-400'
  return 'border-violet-200 dark:border-violet-800 bg-violet-50 dark:bg-violet-900/20 text-violet-600 dark:text-violet-400'
})
</script>
