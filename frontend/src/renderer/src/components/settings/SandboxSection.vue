<template>
  <div class="space-y-6">
    <!-- 启用开关 -->
    <div class="flex items-center justify-between">
      <div>
        <Label>启用代码沙箱</Label>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          开启后对话中的代码块可直接运行，基于 Pyodide (Python WASM) 在本地执行
        </p>
      </div>
      <Switch :model-value="settings.enabled" @update:model-value="settings.enabled = $event"/>
    </div>

    <template v-if="settings.enabled">
      <!-- 执行超时 -->
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <Label>执行超时</Label>
          <span class="text-sm font-mono text-gray-600 dark:text-gray-400">{{ settings.execTimeout }}s</span>
        </div>
        <Slider
            :model-value="[settings.execTimeout]"
            :min="5" :max="120" :step="5"
            @update:model-value="onExecTimeoutChange"
        />
        <p class="text-xs text-gray-500 dark:text-gray-400">
          单次代码执行的最大时长，超时后自动终止
        </p>
      </div>

      <!-- 空闲回收 -->
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <Label>空闲回收时间</Label>
          <span class="text-sm font-mono text-gray-600 dark:text-gray-400">{{ settings.idleTimeout }} 分钟</span>
        </div>
        <Slider
            :model-value="[settings.idleTimeout]"
            :min="5" :max="60" :step="5"
            @update:model-value="onIdleTimeoutChange"
        />
        <p class="text-xs text-gray-500 dark:text-gray-400">
          引擎空闲超过该时间后自动销毁以释放内存，下次执行时重新初始化
        </p>
      </div>

      <!-- 自动运行 -->
      <div class="flex items-center justify-between">
        <div>
          <Label>自动运行代码</Label>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
            AI 返回 Python 代码块时自动执行（谨慎开启）
          </p>
        </div>
        <Switch :model-value="settings.autoRun" @update:model-value="settings.autoRun = $event"/>
      </div>

      <!-- 引擎状态 -->
      <div class="space-y-2">
        <Label>引擎状态</Label>
        <div class="flex items-center justify-between px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
          <div class="flex items-center gap-2">
            <span class="w-2 h-2 rounded-full" :class="statusColor"/>
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ statusLabel }}</span>
          </div>
          <Button variant="outline" size="sm" :disabled="sandboxStore.engineStatus === 'loading'" @click="sandboxStore.restart()">
            <RotateCw :size="14" class="mr-1"/>
            重启内核
          </Button>
        </div>
      </div>

      <!-- 沙箱工具 -->
      <div class="space-y-2">
        <Label>沙箱工具</Label>
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-2">
          预装到沙箱的工具函数，AI 生成代码时可通过 tools.* 直接调用
        </p>
        <SandboxToolsSection/>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {RotateCw} from 'lucide-vue-next'
import {Label} from '@/components/ui/label'
import {Switch} from '@/components/ui/switch'
import {Slider} from '@/components/ui/slider'
import {Button} from '@/components/ui/button'
import {useSandboxStore} from '@/stores/sandbox'
import SandboxToolsSection from '@/components/settings/SandboxToolsSection.vue'

const sandboxStore = useSandboxStore()
const settings = sandboxStore.settings

function onExecTimeoutChange(val: number[] | undefined) {
  if (!val) return
  settings.execTimeout = val[0]
}

function onIdleTimeoutChange(val: number[] | undefined) {
  if (!val) return
  settings.idleTimeout = val[0]
}

const statusColor = computed(() => {
  switch (sandboxStore.engineStatus) {
    case 'ready': return 'bg-green-500'
    case 'running': return 'bg-blue-500 animate-pulse'
    case 'loading': return 'bg-yellow-500 animate-pulse'
    case 'error': return 'bg-red-500'
    default: return 'bg-gray-400'
  }
})

const statusLabel = computed(() => {
  switch (sandboxStore.engineStatus) {
    case 'idle': return '未启动'
    case 'loading': return '正在加载...'
    case 'ready': return '就绪'
    case 'running': return '执行中'
    case 'error': return '异常'
    default: return '未知'
  }
})
</script>
