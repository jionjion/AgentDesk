<template>
  <div ref="bubbleRef" class="flex gap-3 py-3" :class="isUser ? 'flex-row-reverse' : 'flex-row'">
    <!-- 头像 -->
    <div
      class="w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center text-sm"
      :class="isUser ? 'bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400' : 'bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400'"
    >
      {{ isUser ? '你' : 'AI' }}
    </div>
    <!-- 消息内容 -->
    <div class="max-w-[75%] min-w-0">
      <div
        class="inline-block px-4 py-2.5 rounded-2xl text-sm leading-relaxed break-words"
        :class="isUser
          ? 'bg-blue-500 text-white rounded-br-md'
          : 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-100 rounded-bl-md'"
      >
        <div v-if="isUser">
          {{ message.content }}
          <div v-if="(message as UserMessage).attachments?.length"
               class="flex flex-wrap gap-1.5 mt-2">
            <div v-for="att in (message as UserMessage).attachments" :key="att.id"
                 class="flex items-center gap-1 bg-white/20 rounded px-2 py-1 text-xs">
              <FileText :size="12" />
              <span class="truncate max-w-[120px]">{{ att.name }}</span>
            </div>
          </div>
        </div>
        <div v-else>
          <div class="markdown-body" v-html="renderedContent" />
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
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { Marked } from 'marked'
import hljs from 'highlight.js'
import { FileText } from 'lucide-vue-next'
import type { ChatMessage, AssistantMessage, UserMessage } from '@/types/chat'

const marked = new Marked()

// 自定义 code block 渲染，注入复制按钮 + 高亮
marked.use({
  renderer: {
    code({ text, lang }) {
      // text 是原始代码文本（marked 传入的）
      const highlighted = lang && hljs.getLanguage(lang)
        ? hljs.highlight(text, { language: lang }).value
        : hljs.highlightAuto(text).value
      const langLabel = lang || ''
      // data-code 存原始代码用于复制
      const escaped = text.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      return `<div class="code-block-wrapper"><div class="code-block-header"><span class="code-lang">${langLabel}</span><button class="code-copy-btn" data-code="${escaped}">复制</button></div><pre><code class="hljs language-${langLabel}">${highlighted}</code></pre></div>`
    }
  }
})

const props = defineProps<{
  message: ChatMessage
}>()

const isUser = computed(() => props.message.role === 'user')

const renderedContent = computed(() => {
  const content = (props.message as AssistantMessage).content || ''
  return marked.parse(content) as string
})

// 事件委托处理复制按钮点击
const bubbleRef = ref<HTMLElement>()

function handleCopyClick(e: Event) {
  const target = (e.target as HTMLElement).closest('.code-copy-btn') as HTMLButtonElement | null
  if (!target) return
  const code = target.getAttribute('data-code')
    ?.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&amp;/g, '&')
  if (!code) return
  navigator.clipboard.writeText(code)
  target.textContent = '已复制'
  setTimeout(() => { target.textContent = '复制' }, 2000)
}

onMounted(() => {
  bubbleRef.value?.addEventListener('click', handleCopyClick)
})

onBeforeUnmount(() => {
  bubbleRef.value?.removeEventListener('click', handleCopyClick)
})
</script>

