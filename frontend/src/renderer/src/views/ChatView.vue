<template>
  <div class="h-full flex flex-col">
    <!-- 消息区域 / 欢迎页 -->
    <div v-if="chatStore.currentMessages.length === 0" class="flex-1 flex flex-col items-center justify-center px-8">
      <!-- Logo + 标语 -->
      <div class="w-16 h-16 rounded-2xl bg-blue-50 flex items-center justify-center mb-6">
        <span class="text-3xl">&#x1F916;</span>
      </div>
      <h1 class="text-2xl font-bold text-gray-900 mb-2">不止聊天，搞定一切</h1>
      <p class="text-sm text-gray-500 mb-10">本地运行、自主规划、安全可控的 AI 工作搭子</p>

      <!-- 功能卡片 -->
      <div class="flex gap-4 mb-16">
        <div
          v-for="card in featureCards"
          :key="card.title"
          class="w-52 p-5 border rounded-xl hover:shadow-sm transition-all cursor-pointer"
          :class="selectedCard?.title === card.title ? 'border-blue-400 bg-blue-50/50' : 'border-gray-200'"
          @click="toggleCard(card)"
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
      <div class="max-w-2xl mx-auto relative">
        <!-- 技能使用说明（浮动弹出） -->
        <div
          v-if="selectedCard"
          class="absolute bottom-full mb-2 left-0 right-0 border border-gray-200 rounded-xl p-4 bg-white shadow-lg z-10"
        >
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <component :is="selectedCard.icon" :size="16" class="text-gray-600" />
              <span class="text-sm font-medium text-gray-800">{{ selectedCard.title }}</span>
            </div>
          </div>
          <p class="text-xs text-gray-500 mb-3">{{ selectedCard.usage }}</p>
          <div class="space-y-1.5">
            <p class="text-xs text-gray-400">试试这样说：</p>
            <div
              v-for="(example, idx) in selectedCard.examples"
              :key="idx"
              class="flex items-start gap-2 text-xs text-gray-600 hover:text-blue-600 cursor-pointer transition-colors"
              @click.stop="fillExample(example)"
            >
              <span class="mt-0.5 w-1.5 h-1.5 rounded-full bg-gray-300 shrink-0" />
              <span>{{ example }}</span>
            </div>
          </div>
        </div>

        <!-- 输入框 -->
        <div class="border border-gray-200 rounded-xl p-4">
        <Textarea
          v-model="inputText"
          :rows="2"
          :placeholder="chatStore.isStreaming ? '等待回复中...' : '描述任务，/ 调用技能与工具'"
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
            <Button variant="ghost" size="icon" class="h-8 w-8" @click="inputText = ''">
              <Paintbrush :size="16" />
            </Button>
            <Button variant="ghost" size="icon" class="h-8 w-8">
              <Paperclip :size="16" />
            </Button>
          </div>
          <div class="flex items-center gap-2">
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
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  FolderOpen,
  Paintbrush,
  Paperclip,
  ArrowUp,
  Square,
  FileText,
  Camera,
  BarChart3
} from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { Button } from '@/components/ui/button'
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
  {
    title: '文件整理',
    description: '智能整理和管理本地文件',
    icon: FileText,
    usage: '选择工作目录后，AI 会扫描文件并按类型、日期等规则自动归类整理。支持去重、重命名、分类归档等操作。',
    examples: [
      '扫描 Downloads 文件夹，找出所有重复的文件，保留最新版本',
      '按文件类型自动分类整理到不同的子文件夹中',
      '读取照片 EXIF 信息，按日期重命名为 YY-MM-DD 格式'
    ]
  },
  {
    title: '内容创作',
    description: '创作演示文稿、文档和多媒体内容',
    icon: Camera,
    usage: 'AI 可以帮你撰写技术文档、博客文章、演示文稿等各类内容，支持 Markdown 格式输出。',
    examples: [
      '帮我写一篇关于 Spring Boot 的技术博客',
      '生成一份项目周报模板',
      '将这段技术方案整理成 PPT 大纲'
    ]
  },
  {
    title: '文档处理',
    description: '处理和分析文档数据',
    icon: BarChart3,
    usage: 'AI 可以读取、解析和转换各类文档，支持数据提取、格式转换、内容摘要等操作。',
    examples: [
      '将 Markdown 文档转换为结构化的 SQL 建表语句',
      '读取 Excel 数据并生成可视化图表',
      '提取 PDF 中的关键信息并整理成表格'
    ]
  }
]

const selectedCard = ref<typeof featureCards[number] | null>(null)

function toggleCard(card: typeof featureCards[number]) {
  selectedCard.value = selectedCard.value?.title === card.title ? null : card
}

function fillExample(example: string) {
  inputText.value = example
}

function handleSend() {
  if (!inputText.value.trim() || chatStore.isStreaming) return
  chatStore.sendMessage(inputText.value)
  inputText.value = ''
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
