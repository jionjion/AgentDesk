<template>
  <div
    class="my-1 mx-11 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden transition-colors"
    :class="message.result ? 'bg-white dark:bg-gray-800' : 'bg-blue-50/50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-800'"
  >
    <!-- 单行头部：点击展开 -->
    <div
      class="flex items-center gap-2 px-3 py-1.5 cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
      @click="collapsed = !collapsed"
    >
      <!-- 状态图标 -->
      <CheckCircle2 v-if="message.result" :size="14" class="shrink-0 text-green-500" />
      <Loader2 v-else :size="14" class="shrink-0 animate-spin text-blue-500" />
      <!-- 任务名 -->
      <span class="text-xs text-gray-600 dark:text-gray-400 flex-1 truncate">{{ displayName }}</span>
      <!-- 展开箭头 -->
      <ChevronRight
        v-if="message.result"
        :size="12"
        class="shrink-0 text-gray-400 transition-transform"
        :class="collapsed ? '' : 'rotate-90'"
      />
    </div>
    <!-- 展开内容 -->
    <div v-if="!collapsed && message.result" class="px-3 py-2 border-t border-gray-100 dark:border-gray-700">
      <div class="text-xs text-gray-500 dark:text-gray-400 whitespace-pre-wrap leading-relaxed">{{ message.result }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { CheckCircle2, Loader2, ChevronRight } from 'lucide-vue-next'
import type { PlanMessage } from '@/types/chat'

const props = defineProps<{
  message: PlanMessage
}>()

const DISPLAY_NAMES: Record<string, string> = {
  create_plan: '创建计划',
  revise_current_plan: '修改计划',
  update_subtask_state: '更新子任务',
  finish_subtask: '完成子任务',
  view_subtasks: '查看子任务',
  get_subtask_count: '查询子任务数',
  finish_plan: '完成计划',
  view_historical_plans: '查看历史计划',
  recover_historical_plan: '恢复历史计划'
}

const displayName = computed(() =>
  DISPLAY_NAMES[props.message.toolName] || props.message.toolName
)

// 默认折叠
const collapsed = ref(true)

// 执行中的消息收到结果后保持折叠
watch(() => props.message.result, (newVal, oldVal) => {
  if (newVal && !oldVal) {
    collapsed.value = true
  }
})
</script>
