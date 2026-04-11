<template>
  <div class="flex-1 flex flex-col gap-6 px-8 py-4 animate-pulse">
    <!-- 模拟 3 组对话骨架 (user + assistant 交替) -->
    <template v-for="i in 3" :key="i">
      <!-- 用户消息 (右侧) -->
      <div class="flex gap-3 flex-row-reverse">
        <div class="w-8 h-8 rounded-full bg-violet-200 dark:bg-violet-900/30 shrink-0" />
        <div class="max-w-[60%] space-y-2">
          <div class="h-4 bg-violet-100 dark:bg-violet-900/20 rounded-full" :style="{ width: userWidths[i - 1] }" />
        </div>
      </div>
      <!-- 助手消息 (左侧) -->
      <div class="flex gap-3">
        <div class="w-8 h-8 rounded-full bg-violet-100 dark:bg-violet-900/30 shrink-0" />
        <div class="max-w-[70%] space-y-2">
          <div
            v-for="j in assistantLines[i - 1]"
            :key="j"
            class="h-4 bg-gray-100 dark:bg-gray-700 rounded-full"
            :style="{ width: lineWidth(i, j) }"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
// 预定义宽度，避免每次渲染随机变化
const userWidths = ['45%', '60%', '35%']
const assistantLines = [3, 4, 2]

function lineWidth(group: number, line: number): string {
  const widths = [
    ['100%', '85%', '60%'],
    ['90%', '100%', '75%', '40%'],
    ['100%', '55%']
  ]
  return widths[group - 1]?.[line - 1] || '80%'
}
</script>
