<template>
  <div
      v-if="chatStore.memoryRecalledCount > 0 && chatStore.isStreaming"
      class="mb-2 flex items-center gap-1.5 px-3 py-1.5 text-xs border border-violet-200 dark:border-violet-800 rounded-lg bg-violet-50 dark:bg-violet-900/20"
  >
    <Brain :size="13" class="shrink-0 text-violet-500"/>
    <span class="text-violet-600 dark:text-violet-400">
      已召回 {{ chatStore.memoryRecalledCount }} 条相关记忆
    </span>
  </div>
</template>

<script setup lang="ts">
import {Brain} from 'lucide-vue-next'
import {useChatStore} from '@/stores/chat'

const chatStore = useChatStore()
</script>
