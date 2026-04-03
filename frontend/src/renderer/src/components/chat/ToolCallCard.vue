<template>
  <div class="my-2 mx-11 border border-gray-200 rounded-lg overflow-hidden">
    <!-- 头部 -->
    <div class="flex items-center gap-2 px-3 py-2 bg-gray-50 border-b border-gray-200">
      <el-icon :size="14" class="text-gray-500"><SetUp /></el-icon>
      <span class="text-xs font-medium text-gray-700">{{ message.toolName }}</span>
      <span v-if="message.status === 'calling'" class="ml-auto">
        <el-icon :size="12" class="animate-spin text-blue-500"><Loading /></el-icon>
      </span>
      <el-icon v-else :size="14" class="ml-auto text-green-500"><CircleCheckFilled /></el-icon>
    </div>
    <!-- 参数 -->
    <div v-if="hasArguments" class="px-3 py-2 text-xs">
      <div
        class="flex items-center gap-1 cursor-pointer text-gray-500 hover:text-gray-700"
        @click="showArgs = !showArgs"
      >
        <el-icon :size="10"><ArrowRight :class="showArgs ? 'rotate-90' : ''" /></el-icon>
        <span>参数</span>
      </div>
      <pre v-if="showArgs" class="mt-1 p-2 bg-gray-50 rounded text-xs text-gray-600 overflow-x-auto">{{ formattedArgs }}</pre>
    </div>
    <!-- 结果 -->
    <div v-if="message.result" class="px-3 py-2 border-t border-gray-100">
      <div class="text-xs text-gray-500 mb-1">结果</div>
      <div class="text-xs text-gray-700 whitespace-pre-wrap">{{ message.result }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { SetUp, Loading, CircleCheckFilled, ArrowRight } from '@element-plus/icons-vue'
import type { ToolCallMessage } from '@/types/chat'

const props = defineProps<{
  message: ToolCallMessage
}>()

const showArgs = ref(false)

const hasArguments = computed(() =>
  props.message.arguments && Object.keys(props.message.arguments).length > 0
)

const formattedArgs = computed(() =>
  JSON.stringify(props.message.arguments, null, 2)
)
</script>
