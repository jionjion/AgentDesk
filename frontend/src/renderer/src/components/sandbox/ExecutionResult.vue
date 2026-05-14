<template>
  <div class="my-2 border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
    <!-- 头部 -->
    <div class="flex items-center gap-2 px-3 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <Terminal :size="14" class="text-gray-500 dark:text-gray-400"/>
      <span class="text-xs font-medium text-gray-700 dark:text-gray-300">执行结果</span>
      <span class="text-xs text-gray-400 ml-auto">{{ formatDuration(result.duration) }}</span>
      <span
          class="w-2 h-2 rounded-full"
          :class="result.success ? 'bg-green-500' : 'bg-red-500'"
      />
    </div>

    <!-- stdout -->
    <div v-if="result.stdout" class="px-3 py-2 border-b border-gray-100 dark:border-gray-700">
      <pre class="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap overflow-auto max-h-60 font-mono">{{ result.stdout }}</pre>
    </div>

    <!-- stderr -->
    <div v-if="result.stderr" class="px-3 py-2 border-b border-gray-100 dark:border-gray-700 bg-red-50 dark:bg-red-900/10">
      <pre class="text-xs text-red-600 dark:text-red-400 whitespace-pre-wrap overflow-auto max-h-40 font-mono">{{ result.stderr }}</pre>
    </div>

    <!-- result 变量 -->
    <div v-if="result.result !== undefined && result.result !== null" class="px-3 py-2 border-b border-gray-100 dark:border-gray-700">
      <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">返回值</div>
      <pre class="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap overflow-auto max-h-40 font-mono">{{ formatResult(result.result) }}</pre>
    </div>

    <!-- 图表 -->
    <div v-if="result.figures?.length" class="px-3 py-2">
      <img
          v-for="(fig, idx) in result.figures"
          :key="idx"
          :src="`data:image/png;base64,${fig}`"
          class="max-w-full rounded border border-gray-200 dark:border-gray-600"
          alt="图表输出"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import {Terminal} from 'lucide-vue-next'
import type {ExecuteResult} from '@/types/sandbox'

defineProps<{
  result: ExecuteResult
}>()

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatResult(value: unknown): string {
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}
</script>
