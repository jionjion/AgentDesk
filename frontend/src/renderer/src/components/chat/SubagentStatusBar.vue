<template>
  <div class="mb-2">
    <!-- 状态栏 -->
    <div
        class="flex items-center border border-gray-200 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800 overflow-hidden cursor-pointer select-none"
        :class="expanded ? 'rounded-b-none' : ''"
        @click="expanded = !expanded"
    >
      <div class="flex items-center gap-1.5 px-3 py-2 text-xs flex-1 min-w-0">
        <UsersRound :size="13" class="shrink-0 text-gray-500 dark:text-gray-400"/>
        <span class="font-medium text-gray-700 dark:text-gray-300">专家团</span>
        <span class="font-mono shrink-0" :class="allDone ? 'text-green-500' : 'text-gray-500 dark:text-gray-400'">
          {{ doneCount }}/{{ messages.length }}
        </span>

        <!-- 折叠时：显示当前执行中的专家名 -->
        <template v-if="!expanded && runningName">
          <span class="mx-1 text-gray-300 dark:text-gray-600">|</span>
          <Loader2 :size="12" class="shrink-0 animate-spin text-violet-500"/>
          <span class="ml-1 text-violet-500 dark:text-violet-400 truncate">
            {{ runningName }}
          </span>
        </template>
      </div>

      <!-- 展开/收起箭头 -->
      <ChevronRight
          :size="14"
          class="shrink-0 mr-2 text-gray-400 transition-transform"
          :class="expanded ? 'rotate-90' : ''"
      />
    </div>

    <!-- 展开的专家列表 -->
    <div
        v-if="expanded"
        class="border border-t-0 border-gray-200 dark:border-gray-700 rounded-b-lg bg-white dark:bg-gray-900 max-h-72 overflow-y-auto py-1"
    >
      <template v-for="msg in messages" :key="msg.id">
        <!-- 专家行 -->
        <div
            class="flex items-center gap-2.5 px-3 py-1.5 cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-gray-800"
            @click="toggleDetail(msg.id)"
        >
          <CheckCircle2 v-if="msg.isCompleted" :size="14" class="shrink-0 text-green-500"/>
          <Loader2 v-else :size="14" class="shrink-0 animate-spin text-violet-500"/>
          <span
              class="text-xs flex-1 truncate"
              :class="msg.isCompleted ? 'text-green-600 dark:text-green-400' : 'text-violet-600 dark:text-violet-400 font-medium'"
          >{{ msg.name || msg.agentId }}</span>
          <span v-if="msg.toolCalls.length > 0" class="text-xs text-gray-400 dark:text-gray-500 shrink-0">
            {{ msg.toolCalls.length }} 次工具调用
          </span>
          <ChevronRight
              :size="13"
              class="shrink-0 text-gray-400 transition-transform"
              :class="detailOpen[msg.id] ? 'rotate-90' : ''"
          />
        </div>

        <!-- 专家详情 -->
        <div v-if="detailOpen[msg.id]" class="px-3 pb-2 pl-9 space-y-1.5">
          <!-- 思考过程 -->
          <div v-if="msg.thinking" class="pl-2.5 border-l-2 border-gray-200 dark:border-gray-700">
            <p class="text-xs text-gray-400 dark:text-gray-500 italic whitespace-pre-wrap leading-relaxed">{{ msg.thinking }}</p>
          </div>

          <!-- 工具调用 -->
          <div v-for="call in msg.toolCalls" :key="call.toolId" class="flex items-center gap-2">
            <CheckCircle2 v-if="call.status === 'done'" :size="12" class="shrink-0 text-green-500"/>
            <Loader2 v-else :size="12" class="shrink-0 animate-spin text-violet-500"/>
            <span class="text-xs text-gray-600 dark:text-gray-400 font-mono truncate">{{ call.toolName }}</span>
          </div>

          <!-- 正文输出 -->
          <p v-if="msg.content" class="text-xs text-gray-600 dark:text-gray-400 whitespace-pre-wrap leading-relaxed">{{ msg.content }}</p>

          <p v-if="!msg.thinking && msg.toolCalls.length === 0 && !msg.content" class="text-xs text-gray-400 dark:text-gray-500">
            等待输出...
          </p>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import {CheckCircle2, ChevronRight, Loader2, UsersRound} from 'lucide-vue-next'
import type {SubagentMessage} from '@/types/chat'

const props = defineProps<{
  messages: SubagentMessage[]
}>()

const expanded = ref(false)
const detailOpen = ref<Record<string, boolean>>({})

function toggleDetail(id: string) {
  detailOpen.value[id] = !detailOpen.value[id]
}

const doneCount = computed(() => props.messages.filter(m => m.isCompleted).length)

const allDone = computed(() =>
    props.messages.length > 0 && doneCount.value === props.messages.length
)

// 当前正在执行的专家名
const runningName = computed(() => {
  const running = props.messages.find(m => !m.isCompleted)
  return running ? (running.name || running.agentId) : ''
})
</script>
