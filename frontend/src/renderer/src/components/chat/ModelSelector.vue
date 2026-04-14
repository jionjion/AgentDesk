<template>
  <Popover v-model:open="open">
    <PopoverTrigger as-child>
      <Button variant="ghost" size="sm" class="text-xs text-gray-500 dark:text-gray-400 gap-1 h-7 px-2">
        <span>{{ settingsStore.currentModelDef?.displayName || settingsStore.model.modelId }}</span>
        <ChevronDown :size="12"/>
      </Button>
    </PopoverTrigger>
    <PopoverContent align="end" side="top" class="w-64 p-0">
      <div class="max-h-80 overflow-y-auto py-1">
        <template v-for="(models, group) in settingsStore.groupedModels" :key="group">
          <div class="px-3 py-1.5 text-xs font-medium text-gray-400 dark:text-gray-500">{{ group }}</div>
          <div
              v-for="m in models"
              :key="m.id"
              class="flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              :class="{ 'bg-blue-50 dark:bg-blue-900/20': m.id === settingsStore.model.modelId }"
              @click="handleSelect(m.id)"
          >
            <span class="text-sm flex-1 truncate">{{ m.displayName }}</span>
            <Image v-if="m.inputModalities.includes('image')" :size="14" class="shrink-0 text-blue-500 opacity-70" title="图片"/>
            <Lightbulb v-if="m.supportsReasoning" :size="14" class="shrink-0 text-amber-500 opacity-70" title="思考"/>
            <AudioLines v-if="m.inputModalities.includes('audio')" :size="14" class="shrink-0 text-emerald-500 opacity-70" title="语音"/>
          </div>
        </template>
        <div v-if="Object.keys(settingsStore.groupedModels).length === 0"
             class="px-3 py-4 text-xs text-gray-400 text-center">
          加载中...
        </div>
      </div>
    </PopoverContent>
  </Popover>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {AudioLines, ChevronDown, Image, Lightbulb} from 'lucide-vue-next'
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover'
import {Button} from '@/components/ui/button'
import {useSettingsStore} from '@/stores/settings'

const settingsStore = useSettingsStore()
const open = ref(false)

onMounted(() => {
  settingsStore.fetchModels()
})

// 每次打开下拉时，尝试从后端刷新模型列表
watch(open, (val) => {
  if (val) settingsStore.fetchModels()
})

async function handleSelect(modelId: string) {
  if (modelId === settingsStore.model.modelId) {
    open.value = false
    return
  }
  await settingsStore.switchModel(modelId)
  open.value = false
}
</script>
