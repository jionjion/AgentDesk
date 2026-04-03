<template>
  <div class="h-full flex flex-col">
    <!-- 消息区域 / 欢迎页 -->
    <div v-if="chatStore.currentMessages.length === 0" class="flex-1 flex flex-col items-center justify-center px-8">
      <!-- Logo + 标语 -->
      <div class="w-16 h-16 rounded-2xl bg-green-50 flex items-center justify-center mb-6">
        <span class="text-3xl">&#x1F916;</span>
      </div>
      <h1 class="text-2xl font-bold text-gray-900 mb-2">不止聊天，搞定一切</h1>
      <p class="text-sm text-gray-500 mb-10">本地运行、自主规划、安全可控的 AI 工作搭子</p>

      <!-- 功能卡片 -->
      <div class="flex gap-4 mb-16">
        <div
          v-for="card in featureCards"
          :key="card.title"
          class="w-52 p-5 border border-gray-200 rounded-xl hover:shadow-sm transition-shadow cursor-pointer"
          @click="sendQuickMessage(card.prompt)"
        >
          <div class="w-10 h-10 rounded-lg bg-gray-50 flex items-center justify-center mb-3">
            <component :is="card.icon" :size="22" class="text-gray-600" />
          </div>
          <h3 class="text-sm font-medium text-gray-800 mb-1">{{ card.title }}</h3>
          <p class="text-xs text-gray-400">{{ card.description }}</p>
        </div>
      </div>
    </div>

    <!-- 消息列表 -->
    <div v-else ref="messageListRef" class="flex-1 overflow-y-auto px-8 py-4">
      <div class="max-w-3xl mx-auto">
        <template v-for="msg in chatStore.currentMessages" :key="msg.id">
          <ThinkingBlock v-if="msg.role === 'thinking'" :message="msg" />
          <ToolCallCard v-else-if="msg.role === 'tool_call'" :message="msg" />
          <PlanCard v-else-if="msg.role === 'plan'" :message="msg" />
          <MessageBubble v-else :message="msg" />
        </template>
        <div ref="scrollAnchorRef" />
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="px-8 pb-6">
      <div class="max-w-2xl mx-auto border border-gray-200 rounded-xl p-4">
        <Textarea
          v-model="inputText"
          :rows="2"
          :placeholder="chatStore.isStreaming ? '等待回复中...' : '描述任务，/ 调用技能与工具，标准模式经济高效'"
          :disabled="chatStore.isStreaming"
          class="resize-none border-none shadow-none focus-visible:ring-0"
          @keydown.enter.exact.prevent="handleSend"
        />
        <div class="flex items-center justify-between mt-3">
          <div class="flex items-center gap-2">
            <Button variant="ghost" size="sm">
              <FolderOpen :size="14" class="mr-1" />
              <span class="text-xs text-gray-500">选择工作目录</span>
            </Button>
            <Button variant="ghost" size="icon" class="h-8 w-8">
              <Paintbrush :size="16" />
            </Button>
            <Button variant="ghost" size="icon" class="h-8 w-8">
              <Paperclip :size="16" />
            </Button>
          </div>
          <div class="flex items-center gap-2">
            <Badge variant="outline" class="cursor-pointer">
              &#x26A1; 标准
              <ChevronDown :size="10" class="ml-1" />
            </Badge>
            <!-- 发送 / 停止按钮 -->
            <Button
              v-if="!chatStore.isStreaming"
              size="icon"
              class="h-8 w-8 rounded-full"
              :disabled="!inputText.trim()"
              @click="handleSend"
            >
              <ArrowUp :size="16" />
            </Button>
            <Button
              v-else
              variant="destructive"
              size="icon"
              class="h-8 w-8 rounded-full"
              @click="chatStore.interrupt()"
            >
              <Square :size="14" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  FolderOpen,
  Paintbrush,
  Paperclip,
  ChevronDown,
  ArrowUp,
  Square,
  FileText,
  Camera,
  BarChart3
} from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import ToolCallCard from '@/components/chat/ToolCallCard.vue'
import ThinkingBlock from '@/components/chat/ThinkingBlock.vue'
import PlanCard from '@/components/chat/PlanCard.vue'

const chatStore = useChatStore()
const route = useRoute()

const inputText = ref('')
const messageListRef = ref<HTMLElement>()
const scrollAnchorRef = ref<HTMLElement>()

const featureCards = [
  { title: '文件整理', description: '智能整理和管理本地文件', icon: FileText, prompt: '帮我整理当前目录下的文件' },
  { title: '内容创作', description: '创作演示文稿、文档和多媒体内容', icon: Camera, prompt: '帮我写一篇技术文档' },
  { title: '文档处理', description: '处理和分析文档数据', icon: BarChart3, prompt: '帮我分析这份数据' }
]

function handleSend() {
  if (!inputText.value.trim() || chatStore.isStreaming) return
  chatStore.sendMessage(inputText.value)
  inputText.value = ''
}

function sendQuickMessage(prompt: string) {
  inputText.value = prompt
  handleSend()
}

// 自动滚动到底部
watch(
  () => chatStore.currentMessages,
  () => {
    nextTick(() => {
      scrollAnchorRef.value?.scrollIntoView({ behavior: 'smooth' })
    })
  },
  { deep: true }
)

// 路由参数处理
onMounted(() => {
  const sessionId = route.params.sessionId as string | undefined
  if (sessionId) {
    chatStore.switchSession(sessionId)
  }
})
</script>
