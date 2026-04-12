<template>
  <div class="my-2 mx-11 border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
    <!-- 头部 -->
    <div class="flex items-center gap-2 px-3 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <Settings2 :size="14" class="text-gray-500 dark:text-gray-400"/>
      <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ message.toolName }}</span>
      <span v-if="message.status === 'calling'" class="ml-auto">
        <Loader2 :size="12" class="animate-spin text-violet-500"/>
      </span>
      <CheckCircle2 v-else :size="14" class="ml-auto text-green-500"/>
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
      <pre v-if="showArgs" class="mt-1 p-2 bg-gray-50 dark:bg-gray-800 rounded text-xs text-gray-600 dark:text-gray-400 overflow-x-auto">{{ formattedArgs }}</pre>
    </div>
    <!-- 结果 -->
    <div v-if="message.result" class="px-3 py-2 border-t border-gray-100 dark:border-gray-700">
      <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">结果</div>
      <div class="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ message.result }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import {CheckCircle2, ChevronRight, Loader2, Settings2} from 'lucide-vue-next'
import type {ToolCallMessage} from '@/types/chat'

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
