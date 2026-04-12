<template>
  <Dialog v-model:open="open">
    <DialogContent class="w-[90vw] max-w-3xl min-w-[320px] p-0 gap-0">
      <!-- 搜索输入框 -->
      <div class="flex items-center gap-2 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
        <Search :size="18" class="text-gray-400 flex-shrink-0"/>
        <input
            ref="inputRef"
            v-model="keyword"
            type="text"
            placeholder="搜索消息内容..."
            class="flex-1 bg-transparent border-none outline-none text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400"
            @input="onInput"
        />
        <button
            v-if="keyword"
            class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            @click="keyword = ''; results = []"
        >
          <X :size="16"/>
        </button>
      </div>

      <!-- 搜索结果 -->
      <ScrollArea class="h-[50vh] min-h-[200px]">
        <!-- 加载中 -->
        <div v-if="loading" class="flex items-center justify-center py-8">
          <Loader2 :size="20" class="animate-spin text-gray-400"/>
          <span class="ml-2 text-sm text-gray-400">搜索中...</span>
        </div>

        <!-- 无结果 -->
        <div v-else-if="keyword && !loading && results.length === 0 && searched" class="py-8 text-center">
          <SearchX :size="32" class="mx-auto text-gray-300 dark:text-gray-600 mb-2"/>
          <p class="text-sm text-gray-400">未找到相关消息</p>
        </div>

        <!-- 初始状态 -->
        <div v-else-if="!keyword && results.length === 0" class="py-8 text-center">
          <Search :size="32" class="mx-auto text-gray-300 dark:text-gray-600 mb-2"/>
          <p class="text-sm text-gray-400">输入关键词搜索聊天记录</p>
        </div>

        <!-- 结果列表 (按会话分组) -->
        <div v-else class="py-1">
          <div v-for="group in groupedResults" :key="group.sessionId" class="mb-1">
            <div class="px-4 py-1.5 text-xs font-medium text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-800/50 sticky top-0">
              {{ group.sessionTitle }}
            </div>
            <button
                v-for="item in group.items"
                :key="item.id"
                class="w-full text-left px-4 py-2.5 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors cursor-pointer"
                @click="handleSelect(item)"
            >
              <div class="flex items-center gap-2 mb-1">
                <Badge variant="secondary" class="text-[10px] px-1.5 py-0"
                       :class="item.role === 'user' ? 'bg-violet-200 text-violet-700 dark:bg-violet-900/40 dark:text-violet-300' : 'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-300'">
                  {{ item.role === 'user' ? '用户' : '助手' }}
                </Badge>
                <span class="text-xs text-gray-400">{{ formatTime(item.createdAt) }}</span>
              </div>
              <p class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2" v-html="highlightKeyword(item.content)"></p>
            </button>
          </div>
        </div>
      </ScrollArea>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import {computed, nextTick, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import {useChatStore} from '@/stores/chat'
import {searchMessages} from '@/api/chat'
import type {SearchResult} from '@/types/chat'
import {Loader2, Search, SearchX, X} from 'lucide-vue-next'
import {Dialog, DialogContent} from '@/components/ui/dialog'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Badge} from '@/components/ui/badge'

const open = defineModel<boolean>('open', {default: false})
const router = useRouter()
const chatStore = useChatStore()

const inputRef = ref<HTMLInputElement>()
const keyword = ref('')
const results = ref<SearchResult[]>([])
const loading = ref(false)
const searched = ref(false)

let debounceTimer: ReturnType<typeof setTimeout> | null = null

// 打开时自动聚焦
watch(open, (val) => {
  if (val) {
    keyword.value = ''
    results.value = []
    searched.value = false
    nextTick(() => inputRef.value?.focus())
  }
})

function onInput() {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (!keyword.value.trim()) {
    results.value = []
    searched.value = false
    return
  }
  debounceTimer = setTimeout(() => doSearch(), 300)
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) return
  loading.value = true
  searched.value = true
  try {
    const res = await searchMessages(q)
    results.value = res.data
  } catch {
    results.value = []
  } finally {
    loading.value = false
  }
}

// 按会话分组
const groupedResults = computed(() => {
  const map = new Map<string, { sessionId: string; sessionTitle: string; items: SearchResult[] }>()
  for (const item of results.value) {
    if (!map.has(item.sessionId)) {
      map.set(item.sessionId, {sessionId: item.sessionId, sessionTitle: item.sessionTitle, items: []})
    }
    map.get(item.sessionId)!.items.push(item)
  }
  return Array.from(map.values())
})

function handleSelect(item: SearchResult) {
  open.value = false
  chatStore.switchSession(item.sessionId)
  router.push(`/chat/${item.sessionId}`)
}

function highlightKeyword(text: string): string {
  if (!keyword.value.trim()) return escapeHtml(truncate(text, 120))
  const escaped = escapeHtml(truncate(text, 120))
  const kw = escapeHtml(keyword.value.trim())
  const regex = new RegExp(`(${kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return escaped.replace(regex, '<mark class="bg-yellow-200 dark:bg-yellow-800 text-inherit rounded px-0.5">$1</mark>')
}

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function truncate(str: string, maxLen: number): string {
  if (!str) return ''
  return str.length > maxLen ? str.substring(0, maxLen) + '...' : str
}

function formatTime(ts: number): string {
  const date = new Date(ts)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  if (isToday) {
    return date.toLocaleTimeString('zh-CN', {hour: '2-digit', minute: '2-digit'})
  }
  return date.toLocaleDateString('zh-CN', {month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'})
}
</script>
