<template>
  <div class="my-2 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden hover:shadow-sm transition-shadow">
    <!-- 头部 -->
    <div class="flex items-center gap-2 px-3 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <Settings2 :size="14" class="text-gray-500 dark:text-gray-400"/>
      <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ message.toolName }}</span>

      <!-- remote_exec 审批按钮 -->
      <template v-if="pendingCommand">
        <div class="ml-auto flex items-center gap-1">
          <button class="rounded px-1.5 py-0.5 text-xs text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                  @click="handleReject">
            拒绝
          </button>
          <button class="rounded px-1.5 py-0.5 text-xs text-green-600 dark:text-green-400 hover:bg-green-100 dark:hover:bg-green-900/30 transition-colors"
                  @click="handleApprove">
            允许
          </button>
        </div>
      </template>

      <!-- 正常状态图标 -->
      <template v-else>
        <span v-if="message.status === 'calling'" class="ml-auto">
          <Loader2 :size="12" class="animate-spin text-violet-500"/>
        </span>
        <CheckCircle2 v-else-if="message.status === 'done'" :size="14" class="ml-auto text-green-500"/>
        <XCircle v-else-if="message.status === 'error'" :size="14" class="ml-auto text-red-500"/>
      </template>
    </div>
    <!-- 参数 -->
    <div v-if="hasArguments" class="px-3 py-2 text-xs">
      <div
          class="flex items-center gap-1 cursor-pointer text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
          @click="showArgs = !showArgs"
      >
        <ChevronRight :size="10" :class="showArgs ? 'rotate-90 transition-transform' : 'transition-transform'"/>
        <span>参数</span>
      </div>
      <pre v-if="showArgs" class="mt-1 p-2 bg-gray-50 dark:bg-gray-800 rounded text-xs text-gray-600 dark:text-gray-400 overflow-auto max-h-24">{{ formattedArgs }}</pre>
    </div>
    <!-- 结果 -->
    <div v-if="message.result" class="px-3 py-2 border-t border-gray-100 dark:border-gray-700">
      <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">结果</div>
      <div class="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap overflow-auto max-h-24">{{ message.result }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import {CheckCircle2, ChevronRight, Loader2, Settings2, XCircle} from 'lucide-vue-next'
import type {ToolCallMessage} from '@/types/chat'
import {useRemoteExecStore} from '@/stores/remoteExec'

const props = defineProps<{
  message: ToolCallMessage
}>()

const showArgs = ref(false)
const remoteExecStore = useRemoteExecStore()

const hasArguments = computed(() =>
    props.message.arguments && Object.keys(props.message.arguments).length > 0
)

const formattedArgs = computed(() =>
    JSON.stringify(props.message.arguments, null, 2)
)

/** 找到与当前 tool_call 匹配的待审批命令 */
const pendingCommand = computed(() => {
  if (props.message.toolName !== 'remote_exec' || props.message.status !== 'calling') {
    return null
  }
  // 优先按命令内容匹配，fallback 到取第一个 pending
  const cmd = props.message.arguments?.command as string
  if (cmd) {
    const exact = remoteExecStore.pendingCommands.find(p => p.command === cmd)
    if (exact) return exact
  }
  // 如果只有一个 pending 且当前是唯一 calling 的 remote_exec，直接关联
  if (remoteExecStore.pendingCommands.length > 0) {
    return remoteExecStore.pendingCommands[0]
  }
  return null
})

function handleApprove() {
  if (pendingCommand.value) {
    remoteExecStore.approveCommand(pendingCommand.value.requestId)
  }
}

function handleReject() {
  if (pendingCommand.value) {
    remoteExecStore.rejectCommand(pendingCommand.value.requestId)
  }
}
</script>
