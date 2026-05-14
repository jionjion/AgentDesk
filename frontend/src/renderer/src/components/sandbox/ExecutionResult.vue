<template>
  <div class="my-2 border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
    <!-- 头部：可折叠 -->
    <div
        class="flex items-center gap-2 px-3 py-[0.3rem] bg-[#f6f6f6] dark:bg-[#2a2a2a] border-b border-[#e2e2e2] dark:border-[#3a3a3a] cursor-pointer select-none"
        @click="collapsed = !collapsed"
    >
      <Terminal :size="12" class="text-gray-500 dark:text-gray-400"/>
      <span class="text-xs text-gray-700 dark:text-gray-300">执行结果</span>
      <span class="text-xs text-gray-400 ml-auto">{{ formatDuration(result.duration) }}</span>
      <span
          class="w-2 h-2 rounded-full"
          :class="result.success ? 'bg-green-500' : 'bg-red-500'"
      />
      <button
          class="p-0.5 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
          title="复制"
          @click.stop="copyAll"
      >
        <Copy :size="12"/>
      </button>
    </div>

    <!-- 内容区（可折叠） -->
    <div v-if="!collapsed" class="bg-[#fafafa] dark:bg-[#1e1e1e]">
      <!-- stdout -->
      <div v-if="result.stdout" class="px-3 py-1">
        <pre class="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap overflow-auto max-h-60 font-mono">{{ result.stdout }}</pre>
      </div>

      <!-- stderr -->
      <div v-if="result.stderr" class="px-3 py-1 bg-red-50 dark:bg-red-900/10">
        <pre class="text-xs text-red-600 dark:text-red-400 whitespace-pre-wrap overflow-auto max-h-40 font-mono">{{ result.stderr }}</pre>
      </div>

      <!-- result 变量 -->
      <div v-if="result.result !== undefined && result.result !== null" class="px-3 py-1">
        <div class="text-xs text-gray-500 dark:text-gray-400 mb-0.5">返回值</div>
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
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue'
import {Terminal, Copy} from 'lucide-vue-next'
import type {ExecuteResult} from '@/types/sandbox'

const props = defineProps<{
  result: ExecuteResult
}>()

const collapsed = ref(false)

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatResult(value: unknown): string {
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function copyAll() {
  let text = ''
  if (props.result.stdout) text += props.result.stdout
  if (props.result.stderr) text += (text ? '\n' : '') + props.result.stderr
  if (props.result.result !== undefined && props.result.result !== null) {
    text += (text ? '\n' : '') + formatResult(props.result.result)
  }
  navigator.clipboard.writeText(text)
}
</script>
