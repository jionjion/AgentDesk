<template>
  <div class="h-full flex flex-col">
    <!-- 骨架屏 - 切换会话加载中 -->
    <div v-if="chatStore.isLoadingSession" class="flex-1 overflow-hidden">
      <div class="max-w-2xl lg:max-w-3xl xl:max-w-4xl 2xl:max-w-5xl mx-auto">
        <MessageSkeleton />
      </div>
    </div>

    <!-- 消息区域 / 欢迎页 -->
    <div v-else-if="chatStore.currentMessages.length === 0" class="flex-1 flex flex-col items-center justify-center px-8">
      <!-- Logo + 标语 -->
      <div class="w-16 h-16 rounded-2xl bg-blue-50 dark:bg-blue-900/30 flex items-center justify-center mb-6">
        <Bot :size="32" class="text-gray-600 dark:text-gray-400" />
      </div>
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">说做就做，交付结果</h1>
      <p class="text-sm text-gray-500 dark:text-gray-400 mb-10">本地运行、自主规划、安全可控的 AI 工作搭子</p>

      <!-- 功能卡片 -->
      <div class="flex gap-4 mb-16 w-full max-w-2xl lg:max-w-3xl xl:max-w-4xl 2xl:max-w-5xl">
        <div
          v-for="card in featureCards"
          :key="card.title"
          class="flex-1 p-5 border rounded-xl hover:shadow-sm transition-all cursor-pointer"
          :class="selectedCard?.title === card.title ? 'border-blue-400 dark:border-blue-500 bg-blue-50/50 dark:bg-blue-900/20' : 'border-gray-200 dark:border-gray-700'"
          @click="toggleCard(card)"
        >
          <div class="w-10 h-10 rounded-lg bg-gray-50 dark:bg-gray-800 flex items-center justify-center mb-3">
            <component :is="card.icon" :size="22" class="text-gray-600 dark:text-gray-400" />
          </div>
          <h3 class="text-sm font-medium text-gray-800 dark:text-gray-200 mb-1">{{ card.title }}</h3>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ card.description }}</p>
        </div>
      </div>
    </div>

    <!-- 消息列表 -->
    <div v-else ref="messageListRef" class="flex-1 overflow-y-auto px-8 py-4">
      <div class="max-w-2xl lg:max-w-3xl xl:max-w-4xl 2xl:max-w-5xl mx-auto">
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
      <div class="max-w-2xl lg:max-w-3xl xl:max-w-4xl 2xl:max-w-5xl mx-auto relative">
        <!-- 技能使用说明（浮动弹出） -->
        <div
          v-if="selectedCard"
          class="absolute bottom-full mb-2 left-0 right-0 border border-gray-200 dark:border-gray-700 rounded-xl p-4 bg-white dark:bg-gray-800 shadow-lg z-10"
        >
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <component :is="selectedCard.icon" :size="16" class="text-gray-600 dark:text-gray-400" />
              <span class="text-sm font-medium text-gray-800 dark:text-gray-200">{{ selectedCard.title }}</span>
            </div>
          </div>
          <p class="text-xs text-gray-500 dark:text-gray-400 mb-3">{{ selectedCard.usage }}</p>
          <div class="space-y-1.5">
            <p class="text-xs text-gray-400 dark:text-gray-500">试试这样说：</p>
            <div
              v-for="(example, idx) in selectedCard.examples"
              :key="idx"
              class="flex items-start gap-2 text-xs text-gray-600 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 cursor-pointer transition-colors"
              @click.stop="fillExample(example)"
            >
              <span class="mt-0.5 w-1.5 h-1.5 rounded-full bg-gray-300 dark:bg-gray-600 shrink-0" />
              <span>{{ example }}</span>
            </div>
          </div>
        </div>

        <!-- 输入框 -->
        <div class="border border-gray-200 dark:border-gray-700 rounded-xl p-4">
        <!-- 隐藏的文件选择器 -->
        <input ref="fileInputRef" type="file" multiple
               accept=".txt,.md,.csv,.json,.xml,.log,.java,.py,.js,.ts,.html,.css,.yaml,.yml,.sql,.sh"
               class="hidden"
               @change="handleFileSelect" />
        <!-- 附件预览区 -->
        <div v-if="chatStore.pendingAttachments.length > 0"
             class="flex flex-wrap gap-2 mb-2 px-1">
          <div v-for="file in chatStore.pendingAttachments" :key="file.name"
               class="flex items-center gap-2 bg-gray-100 dark:bg-gray-700 rounded-lg px-3 py-1.5 text-sm dark:text-gray-200">
            <FileText :size="14" />
            <span class="truncate max-w-[150px]">{{ file.name }}</span>
            <span class="text-gray-400 dark:text-gray-500 text-xs">{{ formatSize(file.size) }}</span>
            <Loader2 v-if="file.uploading" :size="14" class="animate-spin" />
            <button v-else @click="chatStore.removeAttachment(file.id)"
                    class="text-gray-400 hover:text-red-500">
              <X :size="14" />
            </button>
          </div>
        </div>
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
              <span class="text-xs text-gray-500 dark:text-gray-400">选择工作目录</span>
            </Button>
            <Button variant="ghost" size="icon" class="h-8 w-8" @click="inputText = ''">
              <Paintbrush :size="16" />
            </Button>
            <Button variant="ghost" size="icon" class="h-8 w-8" @click="fileInputRef?.click()">
              <Paperclip :size="16" />
            </Button>
          </div>
          <div class="flex items-center gap-2">
            <!-- 发送 / 停止按钮 -->
            <Button
              v-if="!chatStore.isStreaming"
              size="icon"
              class="h-8 w-8 rounded-full"
              :disabled="!inputText.trim() && !chatStore.pendingAttachments.some(p => !p.uploading && p.id > 0)"
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
  BarChart3,
  Bot,
  Loader2,
  X
} from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import ToolCallCard from '@/components/chat/ToolCallCard.vue'
import ThinkingBlock from '@/components/chat/ThinkingBlock.vue'
import PlanCard from '@/components/chat/PlanCard.vue'
import MessageSkeleton from '@/components/chat/MessageSkeleton.vue'

const chatStore = useChatStore()
const route = useRoute()

const inputText = ref('')
const messageListRef = ref<HTMLElement>()
const scrollAnchorRef = ref<HTMLElement>()
const fileInputRef = ref<HTMLInputElement | null>(null)

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files) return
  for (const file of Array.from(input.files)) {
    if (file.size > 10 * 1024 * 1024) {
      continue
    }
    chatStore.addAttachment(file)
  }
  input.value = ''
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

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

function handleSend(e?: KeyboardEvent) {
  if (e?.isComposing) return
  const hasAttachments = chatStore.pendingAttachments.some(p => !p.uploading && p.id > 0)
  if ((!inputText.value.trim() && !hasAttachments) || chatStore.isStreaming) return
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
