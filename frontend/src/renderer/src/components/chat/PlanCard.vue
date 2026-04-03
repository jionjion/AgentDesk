<template>
  <div class="my-2 mx-11 border border-amber-200 rounded-lg overflow-hidden bg-amber-50">
    <!-- 头部 -->
    <div class="flex items-center gap-2 px-3 py-2 border-b border-amber-200">
      <el-icon :size="14" class="text-amber-600"><List /></el-icon>
      <span class="text-xs font-medium text-amber-700">{{ displayName }}</span>
      <el-icon v-if="message.result" :size="14" class="ml-auto text-green-500"><CircleCheckFilled /></el-icon>
      <el-icon v-else :size="12" class="ml-auto animate-spin text-amber-500"><Loading /></el-icon>
    </div>
    <!-- 结果 -->
    <div v-if="message.result" class="px-3 py-2">
      <div class="text-xs text-amber-800 whitespace-pre-wrap">{{ message.result }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { List, CircleCheckFilled, Loading } from '@element-plus/icons-vue'
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
  finish_plan: '完成计划',
  view_historical_plans: '查看历史计划',
  recover_historical_plan: '恢复历史计划'
}

const displayName = computed(() =>
  DISPLAY_NAMES[props.message.toolName] || props.message.toolName
)
</script>
