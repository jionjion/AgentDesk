<template>
  <div class="space-y-6">
    <!-- 连接状态 -->
    <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-4 space-y-3">
      <div class="flex items-center justify-between">
        <div>
          <div class="text-sm font-medium text-gray-900 dark:text-gray-100">远程执行通道</div>
          <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
            Agent 通过 WebSocket 在本机执行 shell/Python 命令与文件操作
          </div>
        </div>
        <span
            class="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium"
            :class="statusClass"
        >
          <span class="w-1.5 h-1.5 rounded-full" :class="statusDotClass"/>
          {{ statusText }}
        </span>
      </div>
      <div class="flex items-center justify-between pt-2 border-t border-gray-100 dark:border-gray-700/60">
        <span class="text-sm text-gray-700 dark:text-gray-300">启用本地执行</span>
        <Switch :model-value="remoteExecStore.settings.enabled" @update:model-value="(v) => remoteExecStore.updateSettings({ enabled: v })"/>
      </div>
      <div class="flex items-center justify-between">
        <div>
          <span class="text-sm text-gray-700 dark:text-gray-300">自动执行低风险命令</span>
          <div class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">只读命令（如 ls、git status）无需逐条确认</div>
        </div>
        <Switch :model-value="remoteExecStore.settings.autoExecuteLowRisk" @update:model-value="(v) => remoteExecStore.updateSettings({ autoExecuteLowRisk: v })"/>
      </div>
    </div>

    <!-- 设备信息 -->
    <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-4 space-y-2">
      <div class="text-sm font-medium text-gray-900 dark:text-gray-100 mb-2">设备信息</div>
      <div class="flex justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-400">设备 ID</span>
        <span class="text-gray-800 dark:text-gray-200 font-mono text-xs">{{ deviceId || '获取中…' }}</span>
      </div>
      <div class="flex justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-400">系统</span>
        <span class="text-gray-800 dark:text-gray-200">{{ platformText }}</span>
      </div>
      <div class="flex justify-between text-sm">
        <span class="text-gray-500 dark:text-gray-400">Shell</span>
        <span class="text-gray-800 dark:text-gray-200">{{ shellText }}</span>
      </div>
    </div>

    <!-- Python 环境 -->
    <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-4">
      <div class="flex items-center justify-between mb-2">
        <div class="text-sm font-medium text-gray-900 dark:text-gray-100">检测到的 Python</div>
        <Button variant="outline" size="sm" :disabled="detecting" @click="detectPython">
          <RefreshCw :size="14" :class="detecting ? 'animate-spin' : ''" class="mr-1"/>
          重新检测
        </Button>
      </div>
      <div v-if="pythonCandidates.length === 0" class="text-sm text-gray-500 dark:text-gray-400 py-2">
        {{ detecting ? '检测中…' : '未检测到系统级 Python。项目可使用自身 .venv 或在项目中单独配置解释器。' }}
      </div>
      <div v-else class="space-y-1.5">
        <div
            v-for="candidate in pythonCandidates" :key="candidate.path"
            class="flex items-center justify-between text-sm py-1"
        >
          <span class="font-mono text-xs text-gray-700 dark:text-gray-300 truncate mr-3">{{ candidate.path }}</span>
          <span class="text-xs text-gray-500 dark:text-gray-400 shrink-0">{{ candidate.version }}</span>
        </div>
      </div>
      <p class="text-xs text-gray-500 dark:text-gray-400 mt-3">
        每个项目实际使用的解释器按顺序取: 项目配置 → 项目 .venv → 系统默认。python_exec 执行始终需要用户确认。
      </p>
    </div>

    <!-- 执行历史 -->
    <div v-if="remoteExecStore.executionHistory.length > 0" class="rounded-lg border border-gray-200 dark:border-gray-700 p-4">
      <div class="text-sm font-medium text-gray-900 dark:text-gray-100 mb-2">最近执行</div>
      <div class="space-y-1.5 max-h-48 overflow-y-auto">
        <div
            v-for="(item, idx) in remoteExecStore.executionHistory.slice(0, 10)" :key="idx"
            class="text-xs font-mono text-gray-600 dark:text-gray-400 truncate"
        >
          <span :class="item.result.exitCode === 0 ? 'text-green-500' : 'text-red-500'">●</span>
          {{ item.command }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {RefreshCw} from 'lucide-vue-next'
import {Button} from '@/components/ui/button'
import {Switch} from '@/components/ui/switch'
import {useRemoteExecStore} from '@/stores/remoteExec'

const remoteExecStore = useRemoteExecStore()

const deviceId = ref('')
const platformText = ref('')
const pythonCandidates = ref<Array<{ path: string; version: string }>>([])
const detecting = ref(false)

const shellText = computed(() => {
  if (platformText.value.toLowerCase().includes('win')) return 'PowerShell（非交互）'
  return '/bin/sh'
})

const statusText = computed(() => {
  switch (remoteExecStore.status) {
    case 'connected':
      return '已连接'
    case 'connecting':
      return '连接中'
    case 'error':
      return '连接错误'
    default:
      return '未连接'
  }
})

const statusClass = computed(() => {
  switch (remoteExecStore.status) {
    case 'connected':
      return 'bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400'
    case 'connecting':
      return 'bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400'
    default:
      return 'bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400'
  }
})

const statusDotClass = computed(() => {
  switch (remoteExecStore.status) {
    case 'connected':
      return 'bg-green-500'
    case 'connecting':
      return 'bg-amber-500 animate-pulse'
    default:
      return 'bg-gray-400'
  }
})

async function detectPython() {
  detecting.value = true
  try {
    pythonCandidates.value = await window.electronAPI.runtime.listPythonCandidates()
  } catch {
    pythonCandidates.value = []
  } finally {
    detecting.value = false
  }
}

onMounted(async () => {
  try {
    deviceId.value = await window.electronAPI.projects.getDeviceId()
  } catch { /* 留空 */ }
  try {
    const info = await window.electronAPI.app.getPlatformInfo()
    platformText.value = `${info.platform} ${info.arch} (${info.release})`
  } catch { /* 留空 */ }
  detectPython()
})
</script>
