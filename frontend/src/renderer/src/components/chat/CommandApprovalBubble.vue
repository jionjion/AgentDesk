<template>
  <div class="my-1.5 flex justify-start">
    <div class="max-w-[85%] rounded-lg border px-3 py-2 transition-colors" :class="borderClass">
      <!-- 单行: 图标 + 命令 + 按钮 -->
      <div class="flex items-center gap-2">
        <ShieldAlert :size="14" class="shrink-0" :class="message.riskLevel === 'HIGH' ? 'text-amber-500' : 'text-blue-500'" />
        <code class="flex-1 min-w-0 truncate text-xs bg-zinc-100 dark:bg-zinc-800 rounded px-1.5 py-0.5 text-zinc-700 dark:text-zinc-300">{{ message.command }}</code>

        <!-- Actions (pending) -->
        <template v-if="message.status === 'pending'">
          <Button variant="ghost" size="icon" class="h-6 w-6 shrink-0 text-red-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950" @click="handleReject">
            <X :size="14" />
          </Button>
          <Button variant="ghost" size="icon" class="h-6 w-6 shrink-0 text-green-600 hover:text-green-700 hover:bg-green-50 dark:hover:bg-green-950" @click="handleApprove">
            <Check :size="14" />
          </Button>
        </template>

        <!-- Status badge (after action) -->
        <span v-else class="shrink-0 inline-flex items-center rounded px-1.5 py-0.5 text-[11px] font-medium" :class="statusBadgeClass">
          <component :is="statusIcon" :size="10" class="mr-0.5" />
          {{ statusLabel }}
        </span>
      </div>

      <!-- 工作目录（仅在有值时显示，小字） -->
      <div v-if="message.workingDir" class="mt-0.5 text-[11px] text-muted-foreground pl-5 truncate">
        {{ message.workingDir }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { ShieldAlert, Check, X, Clock, Ban } from 'lucide-vue-next'
import { useRemoteExecStore } from '@/stores/remoteExec'
import type { CommandApprovalMessage } from '@/types/chat'

const props = defineProps<{
  message: CommandApprovalMessage
}>()

const remoteExecStore = useRemoteExecStore()

const borderClass = computed(() => {
  if (props.message.status === 'pending') {
    return props.message.riskLevel === 'HIGH'
        ? 'border-amber-400/60 bg-amber-50/5'
        : 'border-blue-400/60 bg-blue-50/5'
  }
  if (props.message.status === 'approved') return 'border-green-400/40 bg-green-50/5'
  return 'border-zinc-300/50 dark:border-zinc-600/50'
})

const statusBadgeClass = computed(() => {
  switch (props.message.status) {
    case 'approved': return 'text-green-600 dark:text-green-400'
    case 'rejected': return 'text-red-600 dark:text-red-400'
    case 'timeout': return 'text-zinc-500'
    case 'cancelled': return 'text-zinc-500'
    default: return ''
  }
})

const statusLabel = computed(() => {
  switch (props.message.status) {
    case 'approved': return '已执行'
    case 'rejected': return '已拒绝'
    case 'timeout': return '超时'
    case 'cancelled': return '取消'
    default: return ''
  }
})

const statusIcon = computed(() => {
  switch (props.message.status) {
    case 'approved': return Check
    case 'rejected': return X
    case 'timeout': return Clock
    case 'cancelled': return Ban
    default: return X
  }
})

function handleApprove() {
  remoteExecStore.approveCommand(props.message.requestId)
}

function handleReject() {
  remoteExecStore.rejectCommand(props.message.requestId)
}
</script>
