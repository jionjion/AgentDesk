<template>
  <div class="space-y-6">
    <!-- 启用开关 -->
    <div class="flex items-center justify-between">
      <div>
        <Label>启用长期记忆</Label>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          AI 会记住你的偏好，跨会话保持个性化体验
        </p>
      </div>
      <Switch v-model="form.enabled" @update:model-value="onToggleEnabled"/>
    </div>

    <template v-if="form.enabled">
      <!-- 添加记忆 -->
      <div class="space-y-2">
        <Label>添加记忆</Label>
        <div class="flex gap-2">
          <Input
              v-model="newMemoryContent"
              placeholder="例如：我喜欢用 Java 和 Spring Boot"
              class="flex-1"
              @keydown.enter="handleAddMemory"
          />
          <Button size="sm" :disabled="!newMemoryContent.trim() || addingMemory" @click="handleAddMemory">
            <Plus :size="16" v-if="!addingMemory"/>
            <Loader2 :size="16" class="animate-spin" v-else/>
          </Button>
        </div>
      </div>

      <!-- 记忆列表 -->
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <Label>已保存的记忆</Label>
            <span v-if="memories.length > 0" class="text-xs text-gray-400 dark:text-gray-500 tabular-nums">{{ memories.length }} 条</span>
          </div>
          <div class="flex items-center gap-1.5">
            <button
                class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                title="刷新"
                :disabled="loadingMemories"
                @click="loadMemories"
            >
              <RefreshCw :size="14" :class="loadingMemories ? 'animate-spin' : ''"/>
            </button>
            <button
                v-if="memories.length > 0"
                class="p-1 rounded text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                title="清空全部"
                @click="confirmClearAll = true"
            >
              <Trash2 :size="14"/>
            </button>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="loadingMemories" class="py-8 text-center">
          <Loader2 :size="20" class="animate-spin text-gray-400 mx-auto"/>
        </div>

        <!-- 空状态 -->
        <div
            v-else-if="memories.length === 0"
            class="py-8 text-center text-sm text-gray-400 dark:text-gray-500"
        >
          <Brain :size="24" class="mx-auto mb-2 text-gray-300 dark:text-gray-600"/>
          <p>暂无记忆，和 AI 对话或手动添加即可</p>
        </div>

        <!-- 记忆条目 -->
        <div v-else class="max-h-[400px] overflow-y-auto rounded-lg border border-gray-200 dark:border-gray-700 divide-y divide-gray-100 dark:divide-gray-800">
          <div
              v-for="item in memories"
              :key="item.id"
              class="group relative px-3.5 py-3 hover:bg-gray-50/80 dark:hover:bg-gray-800/40 transition-colors"
          >
            <!-- 编辑模式 -->
            <template v-if="editingId === item.id">
              <textarea
                  v-model="editContent"
                  rows="2"
                  class="w-full text-sm p-2 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 resize-none focus:outline-none focus:ring-1 focus:ring-violet-500"
              />
              <div class="flex gap-1.5 mt-2 justify-end">
                <Button variant="ghost" size="sm" class="h-7 text-xs" @click="cancelEdit">取消</Button>
                <Button size="sm" class="h-7 text-xs" :disabled="!editContent.trim() || savingEdit" @click="handleUpdateMemory(item.id)">
                  {{ savingEdit ? '保存中...' : '保存' }}
                </Button>
              </div>
            </template>
            <!-- 查看模式 -->
            <template v-else>
              <p class="text-sm text-gray-700 dark:text-gray-300 break-words leading-relaxed pr-14">{{ item.memory }}</p>
              <p v-if="item.updatedAt || item.createdAt" class="text-[10px] text-gray-400 dark:text-gray-500 mt-1">{{ formatTime(item.updatedAt || item.createdAt!) }}</p>
              <!-- 操作按钮 -->
              <div class="absolute right-2 top-1/2 -translate-y-1/2 flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                <button
                    class="p-1.5 rounded-md text-gray-400 hover:text-violet-500 hover:bg-violet-50 dark:hover:bg-violet-900/20 transition-colors"
                    title="编辑"
                    @click="startEdit(item)"
                >
                  <Pencil :size="13"/>
                </button>
                <button
                    class="p-1.5 rounded-md text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                    title="删除"
                    @click="handleDeleteMemory(item.id)"
                >
                  <X :size="13"/>
                </button>
              </div>
            </template>
          </div>
        </div>
      </div>
    </template>

    <!-- 清空确认弹窗 -->
    <AlertDialog :open="confirmClearAll" @update:open="confirmClearAll = $event">
      <AlertDialogContent>
        <div class="space-y-2 mb-4">
          <AlertDialogTitle>确认清空所有记忆？</AlertDialogTitle>
          <AlertDialogDescription>此操作不可撤销，所有记忆将被永久删除。</AlertDialogDescription>
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction class="bg-red-600 hover:bg-red-700" @click="handleClearAll">
            确认清空
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useSettingsStore} from '@/stores/settings'
import type {MemoryItem} from '@/types/memory'
import {addMemory, deleteAllMemories, deleteMemory, listMemories, updateMemory} from '@/api/memory'
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {Input} from '@/components/ui/input'
import {Switch} from '@/components/ui/switch'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogTitle
} from '@/components/ui/alert-dialog'
import {Brain, Loader2, Pencil, Plus, RefreshCw, Trash2, X} from 'lucide-vue-next'

const settingsStore = useSettingsStore()

const form = ref({enabled: false})

// 编辑状态
const editingId = ref<string | null>(null)
const editContent = ref('')
const savingEdit = ref(false)

const memories = ref<MemoryItem[]>([])
const loadingMemories = ref(false)
const newMemoryContent = ref('')
const addingMemory = ref(false)
const confirmClearAll = ref(false)

watch(() => settingsStore.memory, (val) => {
  form.value.enabled = val.enabled
}, {immediate: true})

onMounted(() => {
  if (form.value.enabled) {
    loadMemories()
  }
})

/** 格式化时间 */
function formatTime(dateStr: string): string {
  try {
    const d = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return `${diffMin} 分钟前`
    const diffHour = Math.floor(diffMin / 60)
    if (diffHour < 24) return `${diffHour} 小时前`
    const diffDay = Math.floor(diffHour / 24)
    if (diffDay < 30) return `${diffDay} 天前`
    return d.toLocaleDateString('zh-CN', {year: 'numeric', month: '2-digit', day: '2-digit'})
  } catch {
    return dateStr
  }
}

/** 开关切换: 立即自动保存 */
async function onToggleEnabled(_val: boolean) {
  try {
    await settingsStore.saveMemorySettings({enabled: form.value.enabled})
    if (form.value.enabled) {
      await loadMemories()
    } else {
      memories.value = []
    }
  } catch { /* handled by interceptor */ }
}

async function loadMemories() {
  loadingMemories.value = true
  try {
    const res = await listMemories()
    memories.value = res.data
  } catch {
    memories.value = []
  } finally {
    loadingMemories.value = false
  }
}

async function handleAddMemory() {
  const content = newMemoryContent.value.trim()
  if (!content) return
  addingMemory.value = true
  try {
    await addMemory(content)
    newMemoryContent.value = ''
    await loadMemories()
  } finally {
    addingMemory.value = false
  }
}

async function handleDeleteMemory(memoryId: string) {
  try {
    await deleteMemory(memoryId)
    memories.value = memories.value.filter(m => m.id !== memoryId)
  } catch { /* toast handled by interceptor */ }
}

/** 开始编辑一条记忆 */
function startEdit(item: MemoryItem) {
  editingId.value = item.id
  editContent.value = item.memory
}

/** 取消编辑 */
function cancelEdit() {
  editingId.value = null
  editContent.value = ''
}

/** 提交记忆修改 */
async function handleUpdateMemory(memoryId: string) {
  const content = editContent.value.trim()
  if (!content) return
  savingEdit.value = true
  try {
    await updateMemory(memoryId, content)
    const item = memories.value.find(m => m.id === memoryId)
    if (item) {
      item.memory = content
      item.updatedAt = new Date().toISOString()
    }
    cancelEdit()
  } finally {
    savingEdit.value = false
  }
}

async function handleClearAll() {
  try {
    await deleteAllMemories()
    memories.value = []
  } catch { /* toast handled by interceptor */ }
  confirmClearAll.value = false
}
</script>
