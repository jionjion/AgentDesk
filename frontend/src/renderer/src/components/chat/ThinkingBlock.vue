<template>
  <div class="my-2 mx-11">
    <div
      class="flex items-center gap-1.5 cursor-pointer text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 text-xs"
      @click="collapsed = !collapsed"
    >
      <ChevronRight :size="10" :class="collapsed ? '' : 'rotate-90 transition-transform'" />
      <span>思考中...</span>
    </div>
    <div v-if="!collapsed" class="mt-1 pl-4 border-l-2 border-gray-200 dark:border-gray-700">
      <p class="text-xs text-gray-400 dark:text-gray-500 italic whitespace-pre-wrap leading-relaxed">{{ message.content }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import type { ThinkingMessage } from '@/types/chat'

const props = defineProps<{
  message: ThinkingMessage
}>()

const collapsed = ref(props.message.isCollapsed)
</script>
