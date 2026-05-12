<template>
  <div
      class="absolute right-0 top-0 h-full flex flex-col justify-center items-end pr-3 z-10"
      @mouseenter="expanded = true"
      @mouseleave="expanded = false"
  >
    <div
        class="flex flex-col rounded-lg py-1 px-1 transition-all duration-200"
        :class="expanded
          ? 'bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-lg'
          : ''"
    >
      <div
          v-for="item in items"
          :key="item.id"
          class="cursor-pointer h-6 flex items-center justify-end rounded px-1 transition-colors"
          :class="[
            expanded && item.id === activeId
              ? 'bg-violet-50 dark:bg-violet-900/20'
              : expanded ? 'hover:bg-gray-100 hover:dark:bg-gray-700' : ''
          ]"
          @click="handleClick(item.id)"
      >
        <!-- 文字：展开时显示 -->
        <span
            class="text-xs truncate overflow-hidden text-left transition-all duration-200"
            :class="[
              expanded ? 'w-[140px] opacity-100 pr-2' : 'w-0 opacity-0 pr-0',
              item.id === activeId
                ? 'text-violet-600 dark:text-violet-400 font-medium'
                : 'text-gray-600 dark:text-gray-300'
            ]"
        >{{ item.preview }}</span>
        <!-- 一：始终显示 -->
        <span
            class="text-xs select-none shrink-0"
            :class="item.id === activeId
              ? 'font-bold text-violet-500 dark:text-violet-400'
              : 'text-gray-400 dark:text-gray-500'"
        >一</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import type {ChatMessage} from '@/types/chat'

const props = defineProps<{
  messages: ChatMessage[]
  activeId: string
}>()

const emit = defineEmits<{
  scrollTo: [id: string]
}>()

const expanded = ref(false)

function handleClick(id: string) {
  emit('scrollTo', id)
  expanded.value = false
}

const items = computed(() => {
  const userMsgs = props.messages.filter(m => m.role === 'user')
  const recent = userMsgs.slice(-10)
  return recent.map(m => ({
    id: m.id,
    preview: (m as { content: string }).content.slice(0, 20) || '...'
  }))
})
</script>
