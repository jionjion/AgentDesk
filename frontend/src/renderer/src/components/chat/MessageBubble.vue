<template>
  <div>
    <!-- 子任务执行输出：以卡片形式展示 -->
    <div v-if="subtaskOutput && subtaskName && !isUser" ref="bubbleRef" class="my-1 mx-11">
      <div class="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden bg-white dark:bg-gray-800">
        <!-- 卡片头部：子任务名 + 状态 -->
        <div
            class="flex items-center gap-2 px-3 py-2 cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
            @click="contentCollapsed = !contentCollapsed"
        >
          <CheckCircle2 v-if="subtaskDone" :size="14" class="shrink-0 text-green-500"/>
          <Loader2 v-else-if="(message as AssistantMessage).isStreaming" :size="14" class="shrink-0 animate-spin text-violet-500"/>
          <CheckCircle2 v-else :size="14" class="shrink-0 text-green-500"/>
          <span class="text-xs text-gray-700 dark:text-gray-300 flex-1 truncate font-medium">{{ subtaskName }}</span>
          <ChevronRight
              :size="12"
              class="shrink-0 text-gray-400 transition-transform"
              :class="contentCollapsed ? '' : 'rotate-90'"
          />
        </div>
        <!-- 展开的内容 -->
        <div v-if="!contentCollapsed" class="px-3 py-2 border-t border-gray-100 dark:border-gray-700">
          <div class="markdown-body text-xs overflow-hidden" v-html="renderedContent"/>
        </div>
      </div>
    </div>

    <!-- 普通消息气泡 -->
    <div v-else ref="bubbleRef" class="flex gap-3 py-3" :class="isUser ? 'flex-row-reverse' : 'flex-row'">
      <!-- 头像 -->
      <div
          class="w-8 h-8 rounded-lg flex-shrink-0 flex items-center justify-center text-sm overflow-hidden border border-gray-200 dark:border-gray-600"
          :class="isUser ? 'bg-violet-200 dark:bg-violet-900/40 text-violet-700 dark:text-violet-400' : 'bg-violet-100 dark:bg-violet-900/30 text-violet-600 dark:text-violet-400'"
      >
        <img v-if="isUser && userAvatar && !avatarError" :src="userAvatar" alt="头像" class="w-full h-full object-cover" @error="avatarError = true"/>
        <img v-else-if="!isUser" :src="aiIcon" alt="AI" class="w-full h-full object-cover"/>
        <template v-else>你</template>
      </div>
      <!-- 消息内容 -->
      <div class="min-w-0 group/bubble" :class="[isUser ? 'text-right max-w-[75%]' : hasToolCalls ? 'w-[75%]' : 'max-w-[75%]']">
        <div
            class="px-4 py-2.5 rounded-2xl text-sm leading-relaxed break-words text-left"
            :class="[
              isUser
                ? 'inline-block bg-violet-500 text-white rounded-br-md'
                : 'block bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-100 rounded-bl-md'
            ]"
        >
          <div v-if="isUser">
            {{ (message as UserMessage).content }}
            <!-- 非图片附件保留在气泡内 -->
            <div v-if="fileAttachments.length" class="flex flex-wrap gap-1.5 mt-2">
              <div
                  v-for="att in fileAttachments" :key="att.id"
                  class="flex items-center gap-1 bg-white/20 rounded px-2 py-1 text-xs"
              >
                <FileText :size="12"/>
                <span class="truncate max-w-[120px]">{{ att.name }}</span>
                <span class="text-white/60 text-[10px]">{{ formatFileSize(att.size) }}</span>
              </div>
            </div>
          </div>
          <div v-else>
            <!-- 有标记时：分段渲染，每段后插入卡片 -->
            <template v-if="contentSegments.length > 0">
              <template v-for="(seg, segIdx) in contentSegments" :key="segIdx">
                <div v-if="seg.html" class="markdown-body overflow-hidden" v-html="seg.html"/>
                <!-- 子任务完成卡片 -->
                <div v-if="seg.card" class="my-2 rounded-lg border border-gray-200 dark:border-gray-600 overflow-hidden">
                  <div
                      class="flex items-center gap-2 px-3 py-1.5 cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors"
                      @click="toggleFinishCard(seg.subtaskIdx!)"
                  >
                    <CheckCircle2 :size="13" class="shrink-0 text-green-500"/>
                    <span class="text-xs font-medium text-gray-700 dark:text-gray-300 flex-1 truncate">{{ seg.card.name }}</span>
                    <ChevronRight
                        :size="11"
                        class="shrink-0 text-gray-400 transition-transform"
                        :class="expandedFinishCards.has(seg.subtaskIdx!) ? 'rotate-90' : ''"
                    />
                  </div>
                  <div v-if="expandedFinishCards.has(seg.subtaskIdx!)" class="px-3 py-2 border-t border-gray-100 dark:border-gray-600 bg-white/50 dark:bg-gray-800/50">
                    <div class="text-xs text-gray-600 dark:text-gray-400 whitespace-pre-wrap">{{ seg.card.outcome }}</div>
                  </div>
                </div>
                <!-- 工具调用卡片 -->
                <div v-if="seg.toolCall" class="my-2 rounded-lg border border-gray-200 dark:border-gray-600 overflow-hidden">
                  <div
                      class="flex items-center gap-2 px-3 py-1.5 cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors"
                      @click="toggleToolCall(seg.toolCallId!)"
                  >
                    <Settings2 :size="13" class="shrink-0 text-gray-500 dark:text-gray-400"/>
                    <span class="text-xs font-medium text-gray-700 dark:text-gray-300 flex-1 truncate">{{ seg.toolCall.toolName }}</span>
                    <Loader2 v-if="seg.toolCall.status === 'calling'" :size="12" class="shrink-0 animate-spin text-violet-500"/>
                    <CheckCircle2 v-else :size="13" class="shrink-0 text-green-500"/>
                    <ChevronRight
                        :size="11"
                        class="shrink-0 text-gray-400 transition-transform"
                        :class="expandedToolCalls.has(seg.toolCallId!) ? 'rotate-90' : ''"
                    />
                  </div>
                  <div v-if="expandedToolCalls.has(seg.toolCallId!)" class="px-3 py-2 border-t border-gray-100 dark:border-gray-600 bg-white/50 dark:bg-gray-800/50 overflow-auto max-h-32">
                    <div v-if="seg.toolCall.arguments && Object.keys(seg.toolCall.arguments).length > 0" class="mb-2">
                      <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">参数</div>
                      <pre class="p-2 bg-gray-50 dark:bg-gray-800 rounded text-xs text-gray-600 dark:text-gray-400">{{ JSON.stringify(seg.toolCall.arguments, null, 2) }}</pre>
                    </div>
                    <div v-if="seg.toolCall.result">
                      <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">结果</div>
                      <div class="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ seg.toolCall.result }}</div>
                    </div>
                  </div>
                </div>
              </template>
            </template>
            <!-- 无标记时：正常渲染 -->
            <template v-else>
              <div
                  class="markdown-body overflow-hidden"
                  :class="shouldCollapse && contentCollapsed ? 'max-h-24' : ''"
                  v-html="renderedContent"
              />
              <button
                  v-if="shouldCollapse"
                  class="mt-1 text-xs text-violet-500 hover:text-violet-600 dark:text-violet-400 dark:hover:text-violet-300"
                  @click="contentCollapsed = !contentCollapsed"
              >
                {{ contentCollapsed ? '展开全文...' : '收起' }}
              </button>
            </template>
            <!-- 计划确认操作卡片 -->
            <div v-if="showPlanConfirm" class="mt-3 flex gap-2">
              <button
                  class="px-4 py-1.5 text-xs font-medium rounded-lg bg-violet-600 hover:bg-violet-700 text-white transition-colors"
                  @click="handlePlanAction('好的，请执行计划')"
              >
                执行计划
              </button>
              <button
                  class="px-4 py-1.5 text-xs font-medium rounded-lg border border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                  @click="handlePlanAction('请调整计划')"
              >
                调整计划
              </button>
            </div>
          </div>
        </div>
        <!-- 知识库引用来源 -->
        <div
            v-if="!isUser && (message as AssistantMessage).knowledgeRefs?.length"
            class="flex flex-wrap items-center gap-1.5 mt-2"
        >
          <Database :size="12" class="text-emerald-500 shrink-0"/>
          <span
              v-for="(ref, idx) in uniqueKnowledgeRefs" :key="idx"
              class="inline-flex items-center gap-1 px-1.5 py-0.5 text-[10px] rounded bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800"
          >
            {{ ref.documentName }}
            <span class="text-emerald-400 dark:text-emerald-500">{{ (ref.score * 100).toFixed(0) }}%</span>
          </span>
        </div>
        <!-- 图片附件（气泡外部） -->
        <div
            v-if="isUser && imageAttachments.length"
            v-viewer="{toolbar: true, navbar: false, title: false, transition: false}"
            class="flex flex-wrap gap-1.5 mt-1.5"
            :class="isUser ? 'justify-end' : 'justify-start'"
        >
          <img
              v-for="att in imageAttachments" :key="att.id"
              :src="att.url" :alt="att.name"
              class="max-w-[180px] max-h-[180px] rounded-xl object-cover cursor-pointer shadow-sm hover:shadow-md hover:scale-[1.02] transition-all"
          />
        </div>
        <!-- 悬停工具栏 -->
        <div
            v-if="!isUser && !(message as AssistantMessage).isStreaming"
            class="flex items-center gap-0.5 mt-1 opacity-0 group-hover/bubble:opacity-100 transition-opacity"
        >
          <button
              class="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              title="复制"
              @click="handleCopyContent"
          >
            <Copy :size="14"/>
          </button>
          <button
              v-if="obsidianConfigured"
              class="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              title="沉淀到 Obsidian"
              @click="showPrecipitateDialog = true"
          >
            <BookMarked :size="14"/>
          </button>
          <button
              class="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              title="重新生成"
              @click="handleRegenerate"
          >
            <RefreshCw :size="14"/>
          </button>
          <button
              class="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-400 hover:text-red-500 transition-colors"
              title="删除"
              @click="handleDelete"
          >
            <Trash2 :size="14"/>
          </button>
        </div>
      </div>
    </div>
  </div>
  <!-- 删除确认对话框 -->
  <AlertDialog v-model:open="deleteConfirmOpen">
    <AlertDialogContent class="max-w-sm">
      <AlertDialogTitle>确认删除</AlertDialogTitle>
      <AlertDialogDescription>确定要删除该消息吗？</AlertDialogDescription>
      <AlertDialogFooter>
        <AlertDialogCancel>取消</AlertDialogCancel>
        <AlertDialogAction @click="confirmDelete">删除</AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
  <!-- 沉淀到 Obsidian 弹窗 -->
  <PrecipitateDialog
      v-if="showPrecipitateDialog"
      :message-id="message.id"
      @close="showPrecipitateDialog = false"
  />
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {Marked} from 'marked'
import hljs from 'highlight.js'
import {api as viewerApi} from 'v-viewer'
import {CheckCircle2, ChevronRight, Copy, Database, FileText, Loader2, RefreshCw, Settings2, Trash2, BookMarked} from 'lucide-vue-next'
import type {AssistantMessage, ChatMessage, ToolCallMessage, UserMessage} from '@/types/chat'
import {useChatStore} from '@/stores/chat'
import {useAppStore} from '@/stores/app'
import {useSettingsStore} from '@/stores/settings'
import aiIcon from '@/assets/icon_255.png'
import {formatFileSize, isImageType} from '@/utils/file'
import {AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import PrecipitateDialog from '@/components/chat/PrecipitateDialog.vue'

const marked = new Marked()

// 自定义 code block 渲染，注入复制按钮 + 高亮
marked.use({
  renderer: {
    code({text, lang}) {
      const highlighted = lang && hljs.getLanguage(lang)
          ? hljs.highlight(text, {language: lang}).value
          : hljs.highlightAuto(text).value
      const langLabel = lang || ''
      const escaped = text.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      return `<div class="code-block-wrapper"><div class="code-block-header"><span class="code-lang">${langLabel}</span><button class="code-copy-btn" data-code="${escaped}" title="复制"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg></button></div><pre><code class="hljs language-${langLabel}">${highlighted}</code></pre></div>`
    }
  }
})

const props = defineProps<{
  message: ChatMessage
  afterPlanCreated?: boolean
  subtaskOutput?: boolean
  subtaskName?: string
  subtaskDone?: boolean
  /** 子任务完成卡片数据，key 为子任务索引 */
  subtaskCards?: Record<string, { name: string; outcome: string }>
  /** 工具调用消息，key 为 tool_call 消息 ID */
  toolCalls?: Record<string, ToolCallMessage>
}>()

const chatStore = useChatStore()
const appStore = useAppStore()
const settingsStore = useSettingsStore()

const isUser = computed(() => props.message.role === 'user')
const userAvatar = computed(() => appStore.currentUser.avatar)
const avatarError = ref(false)

const contentCollapsed = ref(!!props.subtaskOutput)
const shouldCollapse = computed(() => {
  if (isUser.value) return false
  if (props.subtaskName) return false
  if (!props.subtaskOutput) return false
  const msg = props.message as AssistantMessage
  return !msg.isStreaming
})

const showPlanConfirm = computed(() => {
  if (planActionTaken.value) return false
  if (!props.afterPlanCreated || isUser.value) return false
  const msg = props.message as AssistantMessage
  return !msg.isStreaming
})

const planActionTaken = ref(false)
const expandedFinishCards = ref(new Set<number>())

// 附件分组
const imageAttachments = computed(() => {
  if (!isUser.value) return []
  const atts = (props.message as UserMessage).attachments || []
  return atts.filter(a => isImageType(a.contentType) && a.url)
})

const fileAttachments = computed(() => {
  if (!isUser.value) return []
  const atts = (props.message as UserMessage).attachments || []
  return atts.filter(a => !isImageType(a.contentType))
})

function toggleFinishCard(idx: number) {
  if (expandedFinishCards.value.has(idx)) {
    expandedFinishCards.value.delete(idx)
  } else {
    expandedFinishCards.value.add(idx)
  }
  expandedFinishCards.value = new Set(expandedFinishCards.value)
}

function handlePlanAction(text: string) {
  if (chatStore.isStreaming) return
  planActionTaken.value = true
  chatStore.sendMessage(text)
}

// 标记正则：匹配 <!-- subtask_done:N --> 或 <!-- tool_call:ID -->
const ANY_MARKER = /<!--\s*(?:subtask_done:\d+|tool_call:\S+)\s*-->/
const COMBINED_MARKER = /<!--\s*(?:subtask_done:(\d+)|tool_call:(\S+))\s*-->/g

interface ContentSegment {
  html: string
  subtaskIdx?: number
  card?: { name: string; outcome: string }
  toolCallId?: string
  toolCall?: ToolCallMessage
}

const expandedToolCalls = ref(new Set<string>())

function toggleToolCall(id: string) {
  if (expandedToolCalls.value.has(id)) {
    expandedToolCalls.value.delete(id)
  } else {
    expandedToolCalls.value.add(id)
  }
  expandedToolCalls.value = new Set(expandedToolCalls.value)
}

const CURSOR_HTML = '<span class="inline-block w-1.5 h-4 bg-violet-500 ml-0.5 animate-pulse align-middle"></span>'

/** 将光标注入到 HTML 最后一个块级闭合标签之前，使其与最后一行文字同行 */
function injectCursor(html: string): string {
  const blockCloseRe = /<\/(p|li|td|th|h[1-6]|div|pre|blockquote)>\s*$/i
  const m = html.match(blockCloseRe)
  if (m) {
    const idx = html.lastIndexOf(m[0])
    return html.slice(0, idx) + CURSOR_HTML + html.slice(idx)
  }
  // 没有块级标签则直接追加
  return html + CURSOR_HTML
}

/**
 * 将原始 markdown 按 subtask_done / tool_call 标记分段
 * 每段后面附带对应的子任务卡片或工具调用数据
 */
const contentSegments = computed<ContentSegment[]>(() => {
  if (isUser.value) return []
  const content = (props.message as AssistantMessage).content || ''
  const cards = props.subtaskCards || {}
  const tools = props.toolCalls || {}

  // 没有任何标记，不分段
  if (!ANY_MARKER.test(content)) return []

  // 在原始 markdown 上按标记分段，再分别渲染
  const regex = new RegExp(COMBINED_MARKER.source, 'g')
  const segments: ContentSegment[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = regex.exec(content)) !== null) {
    const before = content.slice(lastIndex, match.index)
    const html = before.trim() ? (marked.parse(before) as string) : ''

    if (match[1] !== undefined) {
      // subtask_done marker
      const subtaskIdx = parseInt(match[1])
      const card = cards[String(subtaskIdx)]
      segments.push({html, subtaskIdx, card})
    } else if (match[2] !== undefined) {
      // tool_call marker
      const toolCallId = match[2]
      const toolCall = tools[toolCallId]
      segments.push({html, toolCallId, toolCall})
    }

    lastIndex = regex.lastIndex
  }

  const remaining = content.slice(lastIndex)
  if (remaining.trim()) {
    segments.push({html: marked.parse(remaining) as string})
  }

  // 流式输出时，将光标注入最后一个有 html 的段落
  if ((props.message as AssistantMessage).isStreaming) {
    for (let i = segments.length - 1; i >= 0; i--) {
      if (segments[i].html) {
        segments[i] = {...segments[i], html: injectCursor(segments[i].html!)}
        break
      }
    }
  }

  return segments
})

const hasToolCalls = computed(() =>
    contentSegments.value.some(seg => seg.toolCall)
)

const uniqueKnowledgeRefs = computed(() => {
  const refs = (props.message as AssistantMessage).knowledgeRefs
  if (!refs?.length) return []
  const seen = new Set<string>()
  return refs.filter(r => {
    if (seen.has(r.documentName)) return false
    seen.add(r.documentName)
    return true
  })
})

const renderedContent = computed(() => {
  const content = (props.message as AssistantMessage).content || ''
  const clean = content.replace(new RegExp(ANY_MARKER.source, 'g'), '')
  let html = marked.parse(clean) as string
  if ((props.message as AssistantMessage).isStreaming) {
    html = injectCursor(html)
  }
  return html
})

function handleCopyContent() {
  const content = props.message.role === 'user'
      ? (props.message as UserMessage).content
      : (props.message as AssistantMessage).content
  const clean = (content || '').replace(new RegExp(ANY_MARKER.source, 'g'), '')
  navigator.clipboard.writeText(clean)
}

function handleRegenerate() {
  chatStore.regenerateMessage(props.message.id)
}

const deleteConfirmOpen = ref(false)
const showPrecipitateDialog = ref(false)

const obsidianConfigured = computed(() => !!settingsStore.obsidian.vaultPath)

function handleDelete() {
  deleteConfirmOpen.value = true
}

function confirmDelete() {
  deleteConfirmOpen.value = false
  chatStore.deleteMessage(props.message.id)
}

// 事件委托处理复制按钮点击 + markdown 图片点击
const bubbleRef = ref<HTMLElement>()

function handleBubbleClick(e: Event) {
  const target = e.target as HTMLElement

  // 复制按钮
  const copyBtn = target.closest('.code-copy-btn') as HTMLButtonElement | null
  if (copyBtn) {
    const code = copyBtn.getAttribute('data-code')
        ?.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&amp;/g, '&')
    if (!code) return
    navigator.clipboard.writeText(code)
    copyBtn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>'
    copyBtn.style.color = '#22c55e'
    setTimeout(() => {
      copyBtn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'
      copyBtn.style.color = ''
    }, 2000)
    return
  }

  // markdown 中的图片点击放大
  if (target.tagName === 'IMG' && target.closest('.markdown-body')) {
    const src = (target as HTMLImageElement).src
    viewerApi({images: [src], options: {toolbar: true, navbar: false, title: false, transition: false}})
  }
}

onMounted(() => {
  bubbleRef.value?.addEventListener('click', handleBubbleClick)
})

onBeforeUnmount(() => {
  bubbleRef.value?.removeEventListener('click', handleBubbleClick)
})
</script>
