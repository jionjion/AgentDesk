<template>
  <AlertDialog :open="true" @update:open="handleOpenChange">
    <AlertDialogContent class="max-w-sm">
      <AlertDialogTitle>沉淀到 Obsidian</AlertDialogTitle>
      <AlertDialogDescription>选择分类目录，将此回答保存到你的笔记库</AlertDialogDescription>

      <div class="space-y-3 mt-3">
        <div class="space-y-1.5">
          <Label class="text-xs">分类目录</Label>
          <div class="relative">
            <Input
                v-model="category"
                placeholder="输入或选择分类..."
                @focus="showSuggestions = true"
                @blur="hideSuggestions"
            />
            <!-- 自动补全 -->
            <div
                v-if="showSuggestions && filteredCategories.length > 0"
                class="absolute top-full left-0 right-0 mt-1 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-md shadow-lg z-10 max-h-32 overflow-y-auto"
            >
              <button
                  v-for="cat in filteredCategories"
                  :key="cat"
                  class="w-full px-3 py-1.5 text-sm text-left hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300"
                  @mousedown.prevent="selectCategory(cat)"
              >
                {{ cat }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <AlertDialogFooter class="mt-4">
        <AlertDialogCancel :disabled="exporting">取消</AlertDialogCancel>
        <Button size="sm" :disabled="exporting" @click="handleExport">
          <Loader2 v-if="exporting" :size="14" class="mr-1 animate-spin"/>
          沉淀
        </Button>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useSettingsStore} from '@/stores/settings'
import {exportMessageToObsidian, getObsidianCategories} from '@/api/obsidian'
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {Input} from '@/components/ui/input'
import {AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import {Loader2} from 'lucide-vue-next'

const props = defineProps<{
  messageId: string | number
}>()

const emit = defineEmits<{
  close: []
}>()

const settingsStore = useSettingsStore()

const category = ref(settingsStore.obsidian.defaultCategory || 'AgentDesk')
const exporting = ref(false)
const showSuggestions = ref(false)
const categories = ref<string[]>([])

const filteredCategories = computed(() => {
  const input = category.value.toLowerCase()
  if (!input) return categories.value
  return categories.value.filter(c => c.toLowerCase().includes(input))
})

onMounted(async () => {
  try {
    const res = await getObsidianCategories()
    categories.value = res.data
  } catch {
    // 获取分类失败不影响使用
  }
})

function selectCategory(cat: string) {
  category.value = cat
  showSuggestions.value = false
}

function hideSuggestions() {
  setTimeout(() => {
    showSuggestions.value = false
  }, 150)
}

function handleOpenChange(open: boolean) {
  if (!open) emit('close')
}

async function handleExport() {
  const id = typeof props.messageId === 'string' ? parseInt(props.messageId) : props.messageId
  if (isNaN(id)) {
    // 消息尚未持久化到后端，无法导出
    emit('close')
    return
  }
  exporting.value = true
  try {
    await exportMessageToObsidian(id, category.value || undefined)
    emit('close')
  } catch {
    // error handled by interceptor
  } finally {
    exporting.value = false
  }
}
</script>
