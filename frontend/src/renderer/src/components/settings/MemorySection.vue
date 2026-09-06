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
      <div class="grid gap-3 rounded-lg border border-gray-200 dark:border-gray-700 p-3">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-700 dark:text-gray-300">自动学习</p>
            <p class="text-xs text-gray-500">仅从原始用户消息中提取稳定事实，不学习助手输出</p>
          </div>
          <Switch v-model="form.autoLearningEnabled" @update:model-value="saveSettings"/>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-700 dark:text-gray-300">项目记忆隔离</p>
            <p class="text-xs text-gray-500">项目内形成的记忆只在该项目中召回</p>
          </div>
          <Switch v-model="form.projectMemoryEnabled" @update:model-value="saveSettings"/>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-700 dark:text-gray-300">显示记忆来源</p>
            <p class="text-xs text-gray-500">在记忆列表和回答状态中显示范围与来源数量</p>
          </div>
          <Switch v-model="form.showMemorySources" @update:model-value="saveSettings"/>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-700 dark:text-gray-300">敏感信息策略</p>
            <p class="text-xs text-gray-500">密钥、令牌和验证码始终禁止；其他敏感内容仅在再次确认后保存</p>
          </div>
          <span class="text-[10px] px-2 py-1 rounded bg-amber-50 dark:bg-amber-900/20 text-amber-600">仅显式确认</span>
        </div>
      </div>

      <div v-if="summary" class="rounded-lg border border-violet-100 dark:border-violet-900/40 bg-violet-50/40 dark:bg-violet-950/10 p-3 space-y-2">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-gray-700 dark:text-gray-200">我对你的了解</p>
            <p class="text-xs text-gray-500">{{ summary.total }} 条有效记忆 · {{ summary.pinned }} 条置顶</p>
          </div>
          <div class="flex gap-1">
            <button class="text-[10px] px-2 py-1 rounded border border-gray-200 dark:border-gray-700" @click="handleExport('markdown')">导出 MD</button>
            <button class="text-[10px] px-2 py-1 rounded border border-gray-200 dark:border-gray-700" @click="handleExport('json')">导出 JSON</button>
          </div>
        </div>
        <div class="flex flex-wrap gap-1">
          <span v-for="(count, category) in summary.categoryCounts" :key="category" class="text-[10px] px-1.5 py-0.5 rounded bg-white dark:bg-gray-800 text-gray-500">
            {{ categoryLabel(String(category)) }} {{ count }}
          </span>
        </div>
        <ul v-if="summary.highlights.length" class="space-y-1 text-xs text-gray-600 dark:text-gray-300">
          <li v-for="item in summary.highlights.slice(0, 4)" :key="item.id" class="line-clamp-1">• {{ item.memory }}</li>
        </ul>
        <p v-if="operations" class="text-[10px] text-gray-400">
          累计引用 {{ operations.recallEvents }} · 冲突 {{ operations.conflictedMemories }} · 已过期 {{ operations.expiredMemories }} · 待处理任务 {{ (operations.jobCounts.PENDING || 0) + (operations.jobCounts.RETRY || 0) }}
          · 抽取服务 {{ operations.providerCircuitState === 'OPEN' ? '熔断中' : '正常' }}
        </p>
        <div v-if="deadJobs.length" class="space-y-1 border-t border-violet-100 dark:border-violet-900/30 pt-2">
          <div v-for="job in deadJobs" :key="job.id" class="flex items-center justify-between text-[10px] text-red-500">
            <span>失败任务 #{{ job.id }} · 已尝试 {{ job.attempts }} 次</span>
            <button class="text-violet-500" @click="handleRetryJob(job.id)">重新执行</button>
          </div>
        </div>
      </div>

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
            <Plus v-if="!addingMemory" :size="16"/>
            <Loader2 v-else :size="16" class="animate-spin"/>
          </Button>
        </div>
        <div class="grid grid-cols-3 gap-2">
          <select v-model="newScopeType" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs">
            <option value="USER">个人范围</option>
            <option value="PROJECT">项目范围</option>
          </select>
          <select v-model="newProjectId" :disabled="newScopeType !== 'PROJECT'" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs disabled:opacity-50">
            <option value="">选择项目</option>
            <option v-for="project in projectsStore.projects" :key="project.id" :value="project.id">{{ project.name }}</option>
          </select>
          <select v-model="newCategory" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs">
            <option v-for="category in categories" :key="category" :value="category">{{ categoryLabel(category) }}</option>
          </select>
        </div>
        <input v-model="newValidUntil" type="date" class="h-8 w-full rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs" title="可选有效期"/>
      </div>

      <!-- 记忆列表 -->
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <Label>已保存的记忆</Label>
            <span v-if="totalMemories > 0" class="text-xs text-gray-400 dark:text-gray-500 tabular-nums">{{ totalMemories }} 条</span>
          </div>
          <div class="flex items-center gap-1.5">
            <button
                class="p-1 rounded text-gray-400 hover:text-violet-500 hover:bg-violet-50 dark:hover:bg-violet-900/20 transition-colors"
                title="从旧版 Mem0 导入"
                :disabled="importingLegacy"
                @click="handleImportLegacy"
            >
              <Download :size="14" :class="importingLegacy ? 'animate-pulse' : ''"/>
            </button>
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

        <div class="grid grid-cols-[1fr_auto_auto_auto] gap-2">
          <div class="relative">
            <Search :size="13" class="absolute left-2 top-2.5 text-gray-400"/>
            <Input v-model="query" class="h-8 pl-7 text-xs" placeholder="搜索记忆" @keydown.enter="applyFilters"/>
          </div>
          <select v-model="filterScope" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs" @change="applyFilters">
            <option value="">全部范围</option><option value="USER">个人</option><option value="PROJECT">项目</option>
          </select>
          <select v-model="filterCategory" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs" @change="applyFilters">
            <option value="">全部分类</option><option v-for="category in categories" :key="category" :value="category">{{ categoryLabel(category) }}</option>
          </select>
          <select v-model="filterStatus" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs" @change="applyFilters">
            <option value="ACTIVE">有效</option><option value="CONFLICTED">待确认冲突</option><option value="EXPIRED">已过期</option><option value="SUPERSEDED">历史替代</option>
          </select>
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
              <div class="grid grid-cols-2 gap-2 mt-2">
                <select v-model="editScopeType" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs">
                  <option value="USER">个人范围</option><option value="PROJECT">项目范围</option>
                </select>
                <select v-model="editProjectId" :disabled="editScopeType !== 'PROJECT'" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs disabled:opacity-50">
                  <option value="">选择项目</option><option v-for="project in projectsStore.projects" :key="project.id" :value="project.id">{{ project.name }}</option>
                </select>
                <select v-model="editCategory" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs">
                  <option v-for="category in categories" :key="category" :value="category">{{ categoryLabel(category) }}</option>
                </select>
                <input v-model="editValidUntil" type="date" class="h-8 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-2 text-xs" title="有效期；留空表示长期"/>
              </div>
              <label class="mt-2 flex items-center gap-2 text-[11px] text-gray-500">
                优先级 <input v-model.number="editImportance" type="range" min="0" max="1" step="0.1" class="flex-1"/> {{ editImportance.toFixed(1) }}
              </label>
              <div class="flex gap-1.5 mt-2 justify-end">
                <Button variant="ghost" size="sm" class="h-7 text-xs" @click="cancelEdit">取消</Button>
                <Button size="sm" class="h-7 text-xs" :disabled="!editContent.trim() || savingEdit" @click="handleUpdateMemory(item.id)">
                  {{ savingEdit ? '保存中...' : '保存' }}
                </Button>
              </div>
            </template>
            <!-- 查看模式 -->
            <template v-else>
              <div class="flex items-center gap-1.5 mb-1">
                <span class="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-500">{{ item.scopeType === 'PROJECT' ? '项目' : '个人' }}</span>
                <span class="text-[10px] px-1.5 py-0.5 rounded bg-violet-50 dark:bg-violet-900/30 text-violet-500">{{ categoryLabel(item.category) }}</span>
                <span v-if="item.status !== 'ACTIVE'" class="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-500">{{ statusLabel(item.status) }}</span>
                <span v-if="item.sensitivity === 'SENSITIVE'" class="text-[10px] px-1.5 py-0.5 rounded bg-amber-50 dark:bg-amber-900/20 text-amber-600">敏感</span>
                <span v-if="item.validUntil" class="text-[10px] text-gray-400">有效至 {{ formatDate(item.validUntil) }}</span>
                <button
                    v-if="form.showMemorySources && item.sourceCount > 0"
                    class="text-[10px] text-gray-400 hover:text-violet-500"
                    @click="toggleSources(item.id)"
                >
                  {{ item.sourceCount }} 个来源
                </button>
              </div>
              <p class="text-sm text-gray-700 dark:text-gray-300 break-words leading-relaxed pr-20">{{ item.memory }}</p>
              <p v-if="item.updatedAt || item.createdAt" class="text-[10px] text-gray-400 dark:text-gray-500 mt-1">{{ formatTime(item.updatedAt || item.createdAt!) }}</p>
              <div v-if="expandedSourceId === item.id" class="mt-2 rounded-md bg-gray-50 dark:bg-gray-800 p-2 space-y-1.5">
                <Loader2 v-if="loadingSources" :size="13" class="animate-spin text-gray-400"/>
                <div v-for="source in sourcesByMemory[item.id] || []" :key="source.id" class="text-[11px] text-gray-500 dark:text-gray-400">
                  <div class="flex items-center gap-1.5">
                    <span class="font-medium">{{ sourceTypeLabel(source.sourceType) }}</span>
                    <span v-if="source.sessionId" class="opacity-60">会话 {{ source.sessionId }}</span>
                  </div>
                  <p v-if="source.evidenceExcerpt" class="mt-0.5 line-clamp-2">{{ source.evidenceExcerpt }}</p>
                  <button class="text-[10px] text-red-400 hover:text-red-500" @click="handleRevokeSource(item.id, source.id)">撤销来源</button>
                </div>
              </div>
              <div v-if="expandedRevisionId === item.id" class="mt-2 rounded-md bg-gray-50 dark:bg-gray-800 p-2 space-y-1.5">
                <Loader2 v-if="loadingRevisions" :size="13" class="animate-spin text-gray-400"/>
                <div v-for="revision in revisionsByMemory[item.id] || []" :key="revision.id" class="flex items-start justify-between gap-2 text-[11px] text-gray-500">
                  <div><span class="font-medium">{{ revision.reason }}</span><p class="line-clamp-1">{{ revision.oldContent || '无正文' }}</p></div>
                  <button v-if="revision.oldContent" class="text-violet-500 shrink-0" @click="handleRestore(item.id, revision.id)">恢复</button>
                </div>
              </div>
              <div v-if="item.status === 'CONFLICTED'" class="mt-2 flex gap-2 text-[11px]">
                <button class="text-violet-500" @click="handleResolveConflict(item.id, 'KEEP_NEW')">采用新内容</button>
                <button class="text-gray-500" @click="handleResolveConflict(item.id, 'KEEP_OLD')">保留旧内容</button>
              </div>
              <!-- 操作按钮 -->
              <div class="absolute right-2 top-1/2 -translate-y-1/2 flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                <button
                    class="p-1.5 rounded-md text-gray-400 hover:text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
                    title="修订历史"
                    @click="toggleRevisions(item.id)"
                >
                  <History :size="13"/>
                </button>
                <button
                    class="p-1.5 rounded-md transition-colors"
                    :class="item.pinned ? 'text-amber-500 bg-amber-50 dark:bg-amber-900/20' : 'text-gray-400 hover:text-amber-500 hover:bg-amber-50 dark:hover:bg-amber-900/20'"
                    :title="item.pinned ? '取消置顶' : '置顶'"
                    @click="handlePin(item)"
                >
                  <Pin :size="13"/>
                </button>
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
        <div v-if="totalMemories > pageSize" class="flex items-center justify-center gap-3 text-xs text-gray-500">
          <Button variant="ghost" size="sm" class="h-7" :disabled="page === 0" @click="changePage(page - 1)">上一页</Button>
          <span>第 {{ page + 1 }} / {{ Math.ceil(totalMemories / pageSize) }} 页</span>
          <Button variant="ghost" size="sm" class="h-7" :disabled="(page + 1) * pageSize >= totalMemories" @click="changePage(page + 1)">下一页</Button>
        </div>
      </div>
    </template>

    <!-- 清空确认弹窗 -->
    <AlertDialog :open="confirmClearAll" @update:open="confirmClearAll = $event">
      <AlertDialogContent>
        <div class="space-y-2 mb-4">
          <AlertDialogTitle>确认清空所有记忆？</AlertDialogTitle>
          <AlertDialogDescription>所有范围和状态的记忆都会立即停止使用；界面中无法撤销，审计删除标记按系统保留策略处理。</AlertDialogDescription>
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
import type {MemoryItem, MemoryJob, MemoryOperations, MemoryRevision, MemorySource, MemorySummary} from '@/types/memory'
import {addMemory, deleteAllMemories, deleteMemory, exportMemories, getMemoryOperations, getMemorySummary, importLegacyMemories, listMemoryItems, listMemoryJobs, listMemoryRevisions, listMemorySources, resolveMemoryConflict, restoreMemoryRevision, retryMemoryJob, revokeMemorySource, setMemoryPinned, updateMemory} from '@/api/memory'
import {useProjectsStore} from '@/stores/projects'
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {Input} from '@/components/ui/input'
import {Switch} from '@/components/ui/switch'
import {AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import {Brain, Download, History, Loader2, Pencil, Pin, Plus, RefreshCw, Search, Trash2, X} from 'lucide-vue-next'
import {memoryCategoryLabel as categoryLabel} from '@/utils/memory'

const settingsStore = useSettingsStore()
const projectsStore = useProjectsStore()

const categories = ['PROFILE', 'PREFERENCE', 'WORKFLOW', 'TOOL_PREFERENCE', 'PROJECT_FACT', 'DECISION', 'GLOSSARY', 'STAKEHOLDER', 'CONSTRAINT', 'ONGOING_STATE', 'SCHEDULE', 'EPISODE_SUMMARY', 'OTHER']

const form = ref({
  schemaVersion: 2,
  enabled: false,
  autoLearningEnabled: true,
  projectMemoryEnabled: true,
  showMemorySources: true,
  sensitiveMemoryPolicy: 'EXPLICIT_ONLY' as const
})

// 编辑状态
const editingId = ref<string | null>(null)
const editContent = ref('')
const editScopeType = ref<'USER' | 'PROJECT'>('USER')
const editProjectId = ref('')
const editCategory = ref('OTHER')
const editValidUntil = ref('')
const editImportance = ref(0.5)
const savingEdit = ref(false)

const memories = ref<MemoryItem[]>([])
const loadingMemories = ref(false)
const newMemoryContent = ref('')
const newScopeType = ref<'USER' | 'PROJECT'>('USER')
const newProjectId = ref('')
const newCategory = ref('PREFERENCE')
const newValidUntil = ref('')
const addingMemory = ref(false)
const confirmClearAll = ref(false)
const importingLegacy = ref(false)
const expandedSourceId = ref<string | null>(null)
const loadingSources = ref(false)
const sourcesByMemory = ref<Record<string, MemorySource[]>>({})
const revisionsByMemory = ref<Record<string, MemoryRevision[]>>({})
const expandedRevisionId = ref<string | null>(null)
const loadingRevisions = ref(false)
const summary = ref<MemorySummary | null>(null)
const operations = ref<MemoryOperations | null>(null)
const deadJobs = ref<MemoryJob[]>([])
const query = ref('')
const filterScope = ref('')
const filterCategory = ref('')
const filterStatus = ref('ACTIVE')
const page = ref(0)
const pageSize = 20
const totalMemories = ref(0)

watch(() => settingsStore.memory, (val) => {
  form.value = {...val}
}, {immediate: true})

onMounted(() => {
  if (!projectsStore.loaded) void projectsStore.load()
  if (form.value.enabled) {
    loadMemories()
  }
})

/** 格式化时间 */
function formatTime(dateStr: string): string {
  try {
    const numeric = Number(dateStr)
    const d = new Date(Number.isFinite(numeric) && numeric > 0 ? numeric : dateStr)
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
    await saveSettings()
    if (form.value.enabled) {
      await loadMemories()
    } else {
      memories.value = []
      totalMemories.value = 0
      summary.value = null
    }
  } catch { /* handled by interceptor */
  }
}

function formatDate(timestamp: number): string {
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

async function saveSettings() {
  await settingsStore.saveMemorySettings({...form.value})
}

function sourceTypeLabel(sourceType: string): string {
  return ({MANUAL: '手动添加', CHAT: '对话学习', LEGACY_IMPORT: '旧版导入'} as Record<string, string>)[sourceType] || sourceType
}

function statusLabel(status: string): string {
  return ({CONFLICTED: '待确认', EXPIRED: '已过期', SUPERSEDED: '已替代', DELETED: '已删除'} as Record<string, string>)[status] || status
}

async function toggleSources(memoryId: string) {
  if (expandedSourceId.value === memoryId) {
    expandedSourceId.value = null
    return
  }
  expandedSourceId.value = memoryId
  if (sourcesByMemory.value[memoryId]) return
  loadingSources.value = true
  try {
    const res = await listMemorySources(memoryId)
    sourcesByMemory.value[memoryId] = res.data
  } finally {
    loadingSources.value = false
  }
}

async function toggleRevisions(memoryId: string) {
  if (expandedRevisionId.value === memoryId) {
    expandedRevisionId.value = null
    return
  }
  expandedRevisionId.value = memoryId
  if (revisionsByMemory.value[memoryId]) return
  loadingRevisions.value = true
  try {
    const res = await listMemoryRevisions(memoryId)
    revisionsByMemory.value[memoryId] = res.data
  } finally {
    loadingRevisions.value = false
  }
}

async function loadMemories() {
  loadingMemories.value = true
  try {
    const [itemsRes, summaryRes, operationsRes, deadJobsRes] = await Promise.all([
      listMemoryItems({scopeType: filterScope.value || undefined, category: filterCategory.value || undefined, status: filterStatus.value || undefined, query: query.value || undefined, page: page.value, pageSize}),
      getMemorySummary(),
      getMemoryOperations(),
      listMemoryJobs('DEAD')
    ])
    memories.value = itemsRes.data.items
    totalMemories.value = itemsRes.data.total
    summary.value = summaryRes.data
    operations.value = operationsRes.data
    deadJobs.value = deadJobsRes.data
  } catch {
    memories.value = []
    totalMemories.value = 0
  } finally {
    loadingMemories.value = false
  }
}

async function handleAddMemory() {
  const content = newMemoryContent.value.trim()
  if (!content) return
  addingMemory.value = true
  try {
    const payload = {
      content,
      scopeType: newScopeType.value,
      scopeId: newScopeType.value === 'PROJECT' ? newProjectId.value : null,
      category: newCategory.value,
      validUntil: newValidUntil.value ? new Date(`${newValidUntil.value}T23:59:59`).getTime() : null
    }
    try {
      await addMemory(payload)
    } catch (error: unknown) {
      const status = (error as {response?: {status?: number}})?.response?.status
      if (status === 409 && window.confirm('该内容可能包含敏感信息。确认仍要保存吗？')) {
        await addMemory({...payload, sensitiveConfirmed: true})
      } else {
        throw error
      }
    }
    newMemoryContent.value = ''
    newValidUntil.value = ''
    await loadMemories()
  } finally {
    addingMemory.value = false
  }
}

async function handleDeleteMemory(memoryId: string) {
  try {
    await deleteMemory(memoryId)
    memories.value = memories.value.filter(m => m.id !== memoryId)
    totalMemories.value = Math.max(0, totalMemories.value - 1)
  } catch { /* toast handled by interceptor */
  }
}

async function handlePin(item: MemoryItem) {
  const res = await setMemoryPinned(item.id, !item.pinned)
  Object.assign(item, res.data)
  memories.value.sort((a, b) => Number(b.pinned) - Number(a.pinned))
}

async function handleImportLegacy() {
  importingLegacy.value = true
  try {
    await importLegacyMemories()
    await loadMemories()
  } finally {
    importingLegacy.value = false
  }
}

/** 开始编辑一条记忆 */
function startEdit(item: MemoryItem) {
  editingId.value = item.id
  editContent.value = item.memory
  editScopeType.value = item.scopeType
  editProjectId.value = item.scopeId || ''
  editCategory.value = item.category
  editValidUntil.value = item.validUntil ? new Date(item.validUntil).toISOString().slice(0, 10) : ''
  editImportance.value = item.importance
}

/** 取消编辑 */
function cancelEdit() {
  editingId.value = null
  editContent.value = ''
  editValidUntil.value = ''
}

/** 提交记忆修改 */
async function handleUpdateMemory(memoryId: string) {
  const content = editContent.value.trim()
  if (!content) return
  savingEdit.value = true
  try {
    const payload = {
      content,
      scopeType: editScopeType.value,
      scopeId: editScopeType.value === 'PROJECT' ? editProjectId.value : null,
      category: editCategory.value,
      importance: editImportance.value,
      validUntil: editValidUntil.value ? new Date(`${editValidUntil.value}T23:59:59`).getTime() : 0
    }
    let res
    try {
      res = await updateMemory(memoryId, payload)
    } catch (error: unknown) {
      const status = (error as {response?: {status?: number}})?.response?.status
      if (status === 409 && window.confirm('修改后的内容可能包含敏感信息。确认仍要保存吗？')) {
        res = await updateMemory(memoryId, {...payload, sensitiveConfirmed: true})
      } else {
        throw error
      }
    }
    const item = memories.value.find(m => m.id === memoryId)
    if (item) Object.assign(item, res.data)
    cancelEdit()
  } finally {
    savingEdit.value = false
  }
}

async function handleClearAll() {
  try {
    await deleteAllMemories()
    memories.value = []
    totalMemories.value = 0
    summary.value = null
  } catch { /* toast handled by interceptor */
  }
  confirmClearAll.value = false
}

function applyFilters() {
  page.value = 0
  void loadMemories()
}

function changePage(nextPage: number) {
  page.value = nextPage
  void loadMemories()
}

async function handleRevokeSource(memoryId: string, sourceId: string) {
  if (!window.confirm('撤销此来源？如果它是最后一个来源，该记忆也会停止使用。')) return
  await revokeMemorySource(memoryId, sourceId)
  sourcesByMemory.value[memoryId] = (sourcesByMemory.value[memoryId] || []).filter(item => item.id !== sourceId)
  await loadMemories()
}

async function handleRestore(memoryId: string, revisionId: string) {
  const res = await restoreMemoryRevision(memoryId, revisionId)
  const item = memories.value.find(memory => memory.id === memoryId)
  if (item) Object.assign(item, res.data)
  delete revisionsByMemory.value[memoryId]
  expandedRevisionId.value = null
  await toggleRevisions(memoryId)
}

async function handleResolveConflict(memoryId: string, action: 'KEEP_NEW' | 'KEEP_OLD') {
  await resolveMemoryConflict(memoryId, action)
  await loadMemories()
}

async function handleExport(format: 'markdown' | 'json') {
  const response = await exportMemories(format)
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = format === 'json' ? 'agentdesk-memories.json' : 'agentdesk-memories.md'
  anchor.click()
  URL.revokeObjectURL(url)
}

async function handleRetryJob(jobId: number) {
  await retryMemoryJob(jobId)
  await loadMemories()
}
</script>
