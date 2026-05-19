<template>
  <Popover v-model:open="open">
    <PopoverTrigger as-child>
      <Button
          variant="ghost" size="sm"
          class="text-xs gap-1 h-7 px-2"
          :class="selectedIds.length > 0 ? 'text-violet-600 dark:text-violet-400' : 'text-gray-500 dark:text-gray-400'"
      >
        <Database :size="14"/>
        <span v-if="selectedIds.length === 0">知识库</span>
        <span v-else>{{ selectedIds.length }} 个知识库</span>
        <ChevronDown :size="12"/>
      </Button>
    </PopoverTrigger>
    <PopoverContent align="start" side="top" class="w-56 p-0">
      <div class="px-3 py-2 border-b border-gray-200 dark:border-gray-700">
        <p class="text-xs text-gray-500 dark:text-gray-400">选择检索的知识库</p>
      </div>
      <div class="max-h-48 overflow-y-auto py-1">
        <div
            v-for="kb in bases" :key="kb.id"
            class="flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            @click="toggle(kb.id)"
        >
          <div class="w-4 h-4 rounded border flex items-center justify-center"
               :class="selectedIds.includes(kb.id) ? 'bg-violet-500 border-violet-500' : 'border-gray-300 dark:border-gray-600'">
            <Check v-if="selectedIds.includes(kb.id)" :size="10" class="text-white"/>
          </div>
          <span class="text-sm flex-1 truncate">{{ kb.name }}</span>
          <span class="text-[10px] text-gray-400">{{ kb.chunkCount }}</span>
        </div>
        <div v-if="bases.length === 0" class="px-3 py-4 text-xs text-gray-400 text-center">
          暂无知识库
        </div>
      </div>
    </PopoverContent>
  </Popover>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {Check, ChevronDown, Database} from 'lucide-vue-next'
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover'
import {Button} from '@/components/ui/button'
import {useKnowledgeStore} from '@/stores/knowledge'
import {storeToRefs} from 'pinia'

const knowledgeStore = useKnowledgeStore()
const {bases} = storeToRefs(knowledgeStore)

const open = ref(false)
const selectedIds = defineModel<number[]>({default: () => []})

onMounted(() => {
  if (bases.value.length === 0) {
    knowledgeStore.loadBases()
  }
})

watch(open, (val) => {
  if (val && bases.value.length === 0) {
    knowledgeStore.loadBases()
  }
})

function toggle(id: number) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) {
    selectedIds.value = selectedIds.value.filter(i => i !== id)
  } else {
    selectedIds.value = [...selectedIds.value, id]
  }
}
</script>
