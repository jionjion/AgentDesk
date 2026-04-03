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
            <el-icon :size="22" class="text-gray-600"><component :is="card.icon" /></el-icon>
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
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          :placeholder="chatStore.isStreaming ? '等待回复中...' : '描述任务，/ 调用技能与工具，标准模式经济高效'"
          :disabled="chatStore.isStreaming"
          resize="none"
          class="border-none!"
          @keydown.enter.exact.prevent="handleSend"
        />
        <div class="flex items-center justify-between mt-3">
          <div class="flex items-center gap-2">
            <el-button text size="small">
              <el-icon class="mr-1"><FolderOpened /></el-icon>
              <span class="text-xs text-gray-500">选择工作目录</span>
            </el-button>
            <el-button text size="small" circle>
              <el-icon :size="16"><Brush /></el-icon>
            </el-button>
            <el-button text size="small" circle>
              <el-icon :size="16"><Paperclip /></el-icon>
            </el-button>
          </div>
          <div class="flex items-center gap-2">
            <el-tag size="small" effect="plain" class="cursor-pointer">
              &#x26A1; 标准
              <el-icon :size="10" class="ml-1"><ArrowDown /></el-icon>
            </el-tag>
            <!-- 发送 / 停止按钮 -->
            <el-button
              v-if="!chatStore.isStreaming"
              type="primary"
              circle
              size="small"
              :disabled="!inputText.trim()"
              @click="handleSend"
            >
              <el-icon><Top /></el-icon>
            </el-button>
            <el-button
              v-else
              type="danger"
              circle
              size="small"
              @click="chatStore.interrupt()"
            >
              <el-icon><VideoPause /></el-icon>
            </el-button>
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
  FolderOpened,
  Brush,
  Paperclip,
  ArrowDown,
  Top,
  VideoPause,
  Document,
  Camera,
  DataLine
} from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
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
  { title: '文件整理', description: '智能整理和管理本地文件', icon: Document, prompt: '帮我整理当前目录下的文件' },
  { title: '内容创作', description: '创作演示文稿、文档和多媒体内容', icon: Camera, prompt: '帮我写一篇技术文档' },
  { title: '文档处理', description: '处理和分析文档数据', icon: DataLine, prompt: '帮我分析这份数据' }
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
