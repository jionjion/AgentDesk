<template>
  <div
      v-if="chatStore.knowledgeRetrievedResults.count > 0 && chatStore.isStreaming"
      class="mb-2 flex items-center gap-1.5 px-3 py-1.5 text-xs border border-emerald-200 dark:border-emerald-800 rounded-lg bg-emerald-50 dark:bg-emerald-900/20"
  >
    <Database :size="13" class="shrink-0 text-emerald-500"/>
    <span class="text-emerald-600 dark:text-emerald-400">
      已检索到 {{ chatStore.knowledgeRetrievedResults.count }} 条相关知识
      <span v-if="chatStore.knowledgeRetrievedResults.sources.length > 0" class="text-emerald-500/70">
        ({{ chatStore.knowledgeRetrievedResults.sources.join(', ') }})
      </span>
    </span>
  </div>
</template>

<script setup lang="ts">
import {Database} from 'lucide-vue-next'
import {useChatStore} from '@/stores/chat'

const chatStore = useChatStore()
</script>
