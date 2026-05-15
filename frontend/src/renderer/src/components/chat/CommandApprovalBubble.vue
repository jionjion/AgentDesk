<template>
  <div class="my-2 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
    <!-- 头部: 跟 ToolCallCard 一致的灰色背景 -->
    <div class="flex items-center gap-2 px-3 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <Terminal :size="14" class="text-gray-500 dark:text-gray-400"/>
      <span class="text-xs font-medium text-gray-700 dark:text-gray-300">remote_exec</span>
      <!-- 待审批: 按钮 -->
      <div v-if="message.status === 'pending'" class="ml-auto flex items-center gap-1">
        <button class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                @click="handleReject">
          拒绝
        </button>
        <button class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium text-green-600 dark:text-green-400 hover:bg-green-100 dark:hover:bg-green-900/30 transition-colors"
                @click="handleApprove">
          允许
        </button>
      </div>
    </div>
    <!-- 命令内容 -->
    <div class="px-3 py-2">
      <pre class="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap overflow-auto max-h-20">$ {{ message.command }}</pre>
      <div v-if="message.workingDir" class="mt-1 text-[11px] text-gray-400 dark:text-gray-500">{{ message.workingDir }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Terminal } from 'lucide-vue-next'
import { useRemoteExecStore } from '@/stores/remoteExec'
import type { CommandApprovalMessage } from '@/types/chat'

const props = defineProps<{
  message: CommandApprovalMessage
}>()

const remoteExecStore = useRemoteExecStore()

function handleApprove() {
  remoteExecStore.approveCommand(props.message.requestId)
}

function handleReject() {
  remoteExecStore.rejectCommand(props.message.requestId)
}
</script>
