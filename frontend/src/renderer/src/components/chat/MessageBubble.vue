<template>
  <div class="flex gap-3 py-3" :class="isUser ? 'flex-row-reverse' : 'flex-row'">
    <!-- 头像 -->
    <div
      class="w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center text-sm"
      :class="isUser ? 'bg-blue-100 text-blue-600' : 'bg-green-100 text-green-600'"
    >
      {{ isUser ? '你' : 'AI' }}
    </div>
    <!-- 消息内容 -->
    <div class="max-w-[75%] min-w-0">
      <div
        class="inline-block px-4 py-2.5 rounded-2xl text-sm leading-relaxed break-words"
        :class="isUser
          ? 'bg-blue-500 text-white rounded-br-md'
          : 'bg-gray-100 text-gray-800 rounded-bl-md'"
      >
        <div v-if="isUser">{{ message.content }}</div>
        <div v-else>
          <span>{{ (message as AssistantMessage).content }}</span>
          <span
            v-if="(message as AssistantMessage).isStreaming"
            class="inline-block w-1.5 h-4 bg-green-500 ml-0.5 animate-pulse align-middle"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage, AssistantMessage } from '@/types/chat'

const props = defineProps<{
  message: ChatMessage
}>()

const isUser = computed(() => props.message.role === 'user')
</script>
