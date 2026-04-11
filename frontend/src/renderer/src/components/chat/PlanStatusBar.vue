<template>
  <div class="mb-2">
    <!-- 状态栏 -->
    <div
      class="flex items-center border border-gray-200 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800 overflow-hidden cursor-pointer select-none"
      :class="expanded ? 'rounded-b-none' : ''"
      @click="expanded = !expanded"
    >
      <div class="flex items-center gap-1.5 px-3 py-2 text-xs flex-1 min-w-0">
        <ListTodo :size="13" class="shrink-0 text-gray-500 dark:text-gray-400" />
        <span class="font-medium text-gray-700 dark:text-gray-300">任务</span>
        <span class="font-mono shrink-0" :class="allDone ? 'text-green-500' : 'text-gray-500 dark:text-gray-400'">
          {{ doneCount }}/{{ totalCount }}
        </span>

        <!-- 折叠时：显示当前任务名 -->
        <template v-if="!expanded && currentTaskName">
          <span class="mx-1 text-gray-300 dark:text-gray-600">|</span>
          <Loader2 :size="12" class="shrink-0 animate-spin text-violet-500" />
          <span class="ml-1 text-violet-500 dark:text-violet-400 truncate">
            {{ currentTaskName }}
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

    <!-- 展开的子任务列表 -->
    <div
      v-if="expanded"
      class="border border-t-0 border-gray-200 dark:border-gray-700 rounded-b-lg bg-white dark:bg-gray-900 max-h-56 overflow-y-auto"
    >
      <!-- 计划标题 -->
      <div v-if="planState.title !== '任务计划'" class="px-3 pt-2 pb-1">
        <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ planState.title }}</span>
      </div>

      <!-- 子任务列表（有解析出的子任务时显示） -->
      <div v-if="planState.subtasks.length > 0" class="py-1">
        <div
          v-for="(subtask, idx) in planState.subtasks"
          :key="idx"
          class="flex items-center gap-2.5 px-3 py-1.5"
        >
          <!-- 状态图标 -->
          <CheckCircle2 v-if="subtask.state === 'done'" :size="14" class="shrink-0 text-green-500" />
          <Loader2 v-else-if="subtask.state === 'in_progress'" :size="14" class="shrink-0 animate-spin text-violet-500" />
          <XCircle v-else-if="subtask.state === 'abandoned'" :size="14" class="shrink-0 text-gray-400" />
          <Circle v-else :size="14" class="shrink-0 text-gray-300 dark:text-gray-600" />
          <!-- 子任务名 -->
          <span
            class="text-xs flex-1 truncate"
            :class="{
              'text-green-600 dark:text-green-400': subtask.state === 'done',
              'text-violet-600 dark:text-violet-400 font-medium': subtask.state === 'in_progress',
              'text-gray-400 dark:text-gray-500 line-through': subtask.state === 'abandoned',
              'text-gray-600 dark:text-gray-400': subtask.state === 'todo'
            }"
          >{{ subtask.name }}</span>
        </div>
      </div>

      <!-- 兜底：没有解析出子任务时，显示 plan 操作列表 -->
      <div v-else class="py-1">
        <div
          v-for="msg in planMessages"
          :key="msg.id"
          class="flex items-center gap-2.5 px-3 py-1.5"
        >
          <CheckCircle2 v-if="msg.result" :size="14" class="shrink-0 text-green-500" />
          <Loader2 v-else :size="14" class="shrink-0 animate-spin text-violet-500" />
          <span
            class="text-xs flex-1 truncate"
            :class="msg.result ? 'text-gray-500 dark:text-gray-400' : 'text-gray-700 dark:text-gray-200'"
          >{{ displayName(msg.toolName) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ListTodo, CheckCircle2, Loader2, Circle, XCircle, ChevronRight } from 'lucide-vue-next'
import type { PlanMessage, PlanState } from '@/types/chat'

const props = defineProps<{
  planMessages: PlanMessage[]
  planState: PlanState
}>()

const DISPLAY_NAMES: Record<string, string> = {
  create_plan: '创建计划',
  revise_current_plan: '修改计划',
  update_plan_info: '更新计划信息',
  update_subtask_state: '更新子任务',
  finish_subtask: '完成子任务',
  view_subtasks: '查看子任务',
  get_subtask_count: '查询子任务数',
  finish_plan: '完成计划',
  view_historical_plans: '查看历史计划',
  recover_historical_plan: '恢复历史计划'
}

function displayName(toolName: string): string {
  return DISPLAY_NAMES[toolName] || toolName
}

const expanded = ref(false)

// 优先用解析出的子任务统计，兜底用 plan 消息统计
const totalCount = computed(() =>
  props.planState.subtasks.length > 0
    ? props.planState.subtasks.length
    : props.planMessages.length
)

const doneCount = computed(() =>
  props.planState.subtasks.length > 0
    ? props.planState.subtasks.filter(s => s.state === 'done').length
    : props.planMessages.filter(m => m.result).length
)

const allDone = computed(() =>
  totalCount.value > 0 && doneCount.value === totalCount.value
)

// 当前正在执行的子任务名
const currentTaskName = computed(() => {
  const inProgress = props.planState.subtasks.find(s => s.state === 'in_progress')
  return inProgress?.name || ''
})
</script>
