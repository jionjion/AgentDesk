<template>
  <div v-if="loading" class="flex items-center justify-center py-16">
    <Loader2 :size="24" class="animate-spin text-gray-400"/>
  </div>
  <EmptyState
      v-else-if="logs.length === 0"
      :icon="History"
      title="暂无执行记录"
      description="任务执行后会在这里显示记录"
  />
  <div v-else class="space-y-3">
    <div
        v-for="log in logs"
        :key="log.id"
        class="border border-gray-200 dark:border-gray-700 rounded-lg p-4"
    >
      <div class="flex items-center justify-between mb-2">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-gray-800 dark:text-gray-200">{{ log.taskName }}</span>
          <Badge
              :variant="log.status === 'SUCCESS' ? 'default' : log.status === 'FAILED' ? 'destructive' : 'secondary'"
              :class="log.status === 'RUNNING' ? 'animate-pulse' : ''"
          >
            {{ statusLabel(log.status) }}
          </Badge>
        </div>
        <span class="text-xs text-gray-400">{{ formatDuration(log.duration) }}</span>
      </div>
      <p v-if="log.result" class="text-xs text-gray-500 dark:text-gray-400 line-clamp-3 mb-2">{{ log.result }}</p>
      <div class="text-xs text-gray-400">
        {{ formatTime(log.startedAt) }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {History, Loader2} from 'lucide-vue-next'
import {Badge} from '@/components/ui/badge'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import type {ScheduledTaskLog} from '@/types/scheduledTask'

defineProps<{
  logs: ScheduledTaskLog[]
  loading: boolean
}>()

function statusLabel(status: string) {
  switch (status) {
    case 'SUCCESS':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'RUNNING':
      return '执行中'
    default:
      return status
  }
}

function formatDuration(ms: number) {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatTime(timestamp: number) {
  return new Date(timestamp).toLocaleString('zh-CN')
}
</script>
