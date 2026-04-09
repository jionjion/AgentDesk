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
        <template v-for="(msg, idx) in chatStore.currentMessages" :key="msg.id">
          <ThinkingBlock v-if="msg.role === 'thinking'" :message="msg" />
          <template v-else-if="msg.role === 'tool_call'" />
          <template v-else-if="msg.role === 'plan'" />
          <MessageBubble v-else :message="msg" :after-plan-created="isAfterPlanCreated(idx)" :subtask-output="isSubtaskOutput(idx)" :subtask-name="getSubtaskName(idx)" :subtask-done="isSubtaskDone(idx)" :subtask-cards="getSubtaskCardsMap(idx)" :tool-calls="getToolCallsMap(idx)" />
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

        <!-- 任务状态栏（输入框上方） -->
        <PlanStatusBar v-if="planMessages.length > 0" :plan-messages="planMessages" :plan-state="planState" />

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
          @keydown="handleKeydown"
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
import { ref, computed, watch, nextTick, onMounted } from 'vue'
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
import { useSettingsStore } from '@/stores/settings'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import ThinkingBlock from '@/components/chat/ThinkingBlock.vue'
import PlanStatusBar from '@/components/chat/PlanStatusBar.vue'
import MessageSkeleton from '@/components/chat/MessageSkeleton.vue'
import { usePlanSubtasks } from '@/composables/usePlanSubtasks'
import type { PlanMessage, ToolCallMessage } from '@/types/chat'

const chatStore = useChatStore()
const settingsStore = useSettingsStore()
const route = useRoute()
const { planState } = usePlanSubtasks()

const planMessages = computed(() =>
  chatStore.currentMessages.filter((m): m is PlanMessage => m.role === 'plan')
)

/**
 * 获取 index 处的助手消息对应的子任务名称
 * 往前查找最近的 update_subtask_state / finish_subtask，从 arguments 提取子任务索引，
 * 然后从 planState 中取出该子任务名称
 */
function getSubtaskName(index: number): string | undefined {
  const msgs = chatStore.currentMessages
  const msg = msgs[index]
  if (msg.role !== 'assistant') return undefined

  // 往前找最近的 plan 消息
  for (let i = index - 1; i >= 0; i--) {
    const prev = msgs[i]
    if (prev.role === 'user') break
    if (prev.role === 'thinking') continue
    if (prev.role === 'plan') {
      const planMsg = prev as PlanMessage
      const args = planMsg.arguments || {}

      if (planMsg.toolName === 'update_subtask_state' || planMsg.toolName === 'finish_subtask') {
        const idx = args.subtask_idx ?? args.subtask_index ?? args.index
        if (typeof idx === 'number' && idx >= 0) {
          const subtasks = planState.value.subtasks
          if (idx < subtasks.length) return subtasks[idx].name
          return `子任务 ${idx + 1}`
        }
      }
      // 如果是其他 plan 消息（如 create_plan），看 planState 中是否有 in_progress 的子任务
      const inProgress = planState.value.subtasks.find(s => s.state === 'in_progress')
      if (inProgress) return inProgress.name
      break
    }
    break
  }
  return undefined
}

/**
 * 判断 index 处的助手消息对应的子任务是否已完成
 */
function isSubtaskDone(index: number): boolean {
  const msgs = chatStore.currentMessages
  // 往后看是否有 finish_subtask
  for (let i = index + 1; i < msgs.length; i++) {
    const next = msgs[i]
    if (next.role === 'user') break
    if (next.role === 'plan' && (next as PlanMessage).toolName === 'finish_subtask') return true
  }
  return false
}

/**
 * 从 finish_subtask 的 plan 消息中获取子任务名称
 */

/**
 * 获取 index 处的助手消息关联的 finish_subtask 卡片数据
 * 往前查找 finish_subtask plan 消息，返回 { subtaskIdx: { name, outcome } } 的 map
 */
function getSubtaskCardsMap(index: number): Record<string, { name: string; outcome: string }> {
  const msgs = chatStore.currentMessages
  const msg = msgs[index]
  if (msg.role !== 'assistant') return {}

  const map: Record<string, { name: string; outcome: string }> = {}
  for (let i = index - 1; i >= 0; i--) {
    const prev = msgs[i]
    if (prev.role === 'user') break
    if (prev.role === 'plan' && (prev as PlanMessage).toolName === 'finish_subtask') {
      const planMsg = prev as PlanMessage
      const args = planMsg.arguments || {}
      const idx = args.subtask_idx ?? args.subtask_index ?? args.index
      if (typeof idx !== 'number') continue
      let name = `子任务 ${idx + 1}`
      if (idx >= 0 && idx < planState.value.subtasks.length) {
        name = planState.value.subtasks[idx].name
      } else {
        const match = planMsg.result?.match(/named '([^']+)'/)
        if (match) name = match[1]
      }
      const outcome = (args.subtask_outcome as string) || planMsg.result || ''
      map[String(idx)] = { name, outcome }
    }
  }
  return map
}

/**
 * 获取 index 处的助手消息关联的 tool_call 消息 map
 * key 为 tool_call 消息 ID，value 为 ToolCallMessage
 */
function getToolCallsMap(index: number): Record<string, ToolCallMessage> {
  const msgs = chatStore.currentMessages
  const msg = msgs[index]
  if (msg.role !== 'assistant') return {}

  const map: Record<string, ToolCallMessage> = {}
  for (let i = index - 1; i >= 0; i--) {
    const prev = msgs[i]
    if (prev.role === 'user') break
    if (prev.role === 'tool_call') {
      map[prev.id] = prev as ToolCallMessage
    }
  }
  return map
}

/**
 * 判断 index 处的助手消息是否紧跟在 create_plan 完成之后
 * 即：该助手消息之前最近的非 thinking 消息是一条已完成的 create_plan/revise_current_plan
 * 且该助手消息之后到下一条用户消息之间没有更多的 plan 消息（说明 agent 在等待用户确认）
 */
function isAfterPlanCreated(index: number): boolean {
  const msgs = chatStore.currentMessages
  const msg = msgs[index]
  if (msg.role !== 'assistant') return false

  // 往前找最近的 plan 消息（跳过 thinking / tool_call）
  let foundCreatePlan = false
  for (let i = index - 1; i >= 0; i--) {
    const prev = msgs[i]
    if (prev.role === 'thinking' || prev.role === 'tool_call') continue
    if (prev.role === 'plan' && (prev.toolName === 'create_plan' || prev.toolName === 'revise_current_plan') && prev.result) {
      foundCreatePlan = true
    }
    break
  }
  if (!foundCreatePlan) return false

  // 往后看：这条助手消息之后不应有更多 plan/tool_call/user 消息（说明 agent 停了，在等确认）
  for (let i = index + 1; i < msgs.length; i++) {
    const next = msgs[i]
    if (next.role === 'user') return false
    if (next.role === 'plan' || next.role === 'tool_call') return false
  }
  return true
}

/**
 * 判断 index 处的助手消息是否属于子任务执行过程中的输出
 * 即：该助手消息前后都有 plan 消息（夹在 plan 操作之间），不是最后一轮对话
 */
function isSubtaskOutput(index: number): boolean {
  const msgs = chatStore.currentMessages
  const msg = msgs[index]
  if (msg.role !== 'assistant') return false

  // 前面有 plan 消息（不是 create_plan 等待确认的那条）
  let hasPlanBefore = false
  for (let i = index - 1; i >= 0; i--) {
    const prev = msgs[i]
    if (prev.role === 'user') break
    if (prev.role === 'plan') {
      hasPlanBefore = true
      break
    }
  }
  if (!hasPlanBefore) return false

  // 后面也有 plan 或 tool_call 消息（说明还在执行中，不是最终回复）
  for (let i = index + 1; i < msgs.length; i++) {
    const next = msgs[i]
    if (next.role === 'user') return false
    if (next.role === 'plan' || next.role === 'tool_call') return true
  }
  return false
}

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

function handleKeydown(e: KeyboardEvent) {
  if (e.isComposing) return
  const sendKey = settingsStore.app.sendKey
  if (sendKey === 'Ctrl+Enter' && e.key === 'Enter' && e.ctrlKey) {
    e.preventDefault()
    handleSend()
  } else if (sendKey === 'Enter' && e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handleSend() {
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
