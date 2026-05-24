<template>
  <div class="h-full flex flex-col">
    <!-- 顶部工具栏 -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-700">
      <div>
        <h1 class="text-lg font-semibold text-gray-900 dark:text-gray-100">知识库</h1>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">管理本地文档，聊天时自动检索相关知识</p>
      </div>
      <Button @click="showCreateDialog = true">
        <Plus :size="16" class="mr-1"/>
        新建知识库
      </Button>
    </div>

    <!-- 内容区 -->
    <ScrollArea class="flex-1">
      <div class="px-6 py-6 max-w-5xl">
<!-- 加载态 -->
        <div v-if="knowledgeStore.isLoading" class="flex items-center justify-center py-16">
          <Loader2 :size="24" class="animate-spin text-gray-400"/>
        </div>

        <!-- 空状态 -->
        <EmptyState
            v-else-if="knowledgeStore.bases.length === 0"
            :icon="BookOpen"
            title="暂无知识库"
            description="创建一个知识库，上传文档后即可在聊天中自动检索"
            action-label="创建第一个知识库"
            :action-icon="Plus"
            @action="showCreateDialog = true"
        />

        <!-- 知识库卡片网格 -->
        <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div
              v-for="kb in knowledgeStore.bases"
              :key="kb.id"
              class="border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:shadow-sm transition-shadow cursor-pointer group"
              @click="openKnowledgeBase(kb)"
          >
            <div class="flex items-start justify-between mb-3">
              <div class="flex items-center gap-2">
                <div class="w-9 h-9 rounded-lg bg-violet-100 dark:bg-violet-900/30 flex items-center justify-center">
                  <BookOpen :size="18" class="text-violet-600 dark:text-violet-400"/>
                </div>
                <div>
                  <h3 class="font-medium text-gray-900 dark:text-gray-100 text-sm">{{ kb.name }}</h3>
                </div>
              </div>
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <button
                      class="p-1 rounded opacity-0 group-hover:opacity-100 hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-all"
                      @click.stop
                  >
                    <MoreHorizontal :size="16"/>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem class="text-red-500 cursor-pointer" @click.stop="handleDeleteBase(kb)">
                    <Trash2 :size="14" class="mr-2"/>删除
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
            <p v-if="kb.description" class="text-xs text-gray-500 dark:text-gray-400 mb-3 line-clamp-2">{{ kb.description }}</p>
            <div class="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
              <span class="flex items-center gap-1">
                <FileText :size="12"/>
                {{ kb.docCount }} 篇文档
              </span>
              <span class="flex items-center gap-1">
                <Layers :size="12"/>
                {{ kb.chunkCount }} 个分块
              </span>
            </div>
          </div>
        </div>
      </div>
    </ScrollArea>

    <!-- 知识库详情面板 (文档列表) -->
    <Dialog v-model:open="showDetail">
      <DialogContent class="max-w-2xl max-h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>{{ currentBase?.name }}</DialogTitle>
          <DialogDescription v-if="currentBase?.description">{{ currentBase.description }}</DialogDescription>
        </DialogHeader>

        <!-- 上传区域 -->
        <div
            class="border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg p-4 text-center hover:border-violet-400 dark:hover:border-violet-600 transition-colors cursor-pointer"
            :class="dragOver ? 'border-violet-500 bg-violet-50 dark:bg-violet-900/10' : ''"
            @click="triggerFileInput"
            @dragover.prevent="dragOver = true"
            @dragleave.prevent="dragOver = false"
            @drop.prevent="handleDrop"
        >
          <Upload :size="24" class="mx-auto text-gray-400 mb-2"/>
          <p class="text-sm text-gray-500 dark:text-gray-400">点击或拖拽文件上传</p>
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">支持 PDF, DOCX, TXT, MD, CSV (最大 50MB)</p>
          <input ref="fileInputRef" type="file" class="hidden" accept=".pdf,.docx,.txt,.md,.csv" multiple @change="handleFileSelect"/>
        </div>

        <!-- 文档列表 -->
        <div class="flex-1 overflow-y-auto mt-4 space-y-2">
          <div v-if="knowledgeStore.documents.length === 0" class="text-center py-8 text-sm text-gray-400">
            暂无文档，请上传文件
          </div>
          <div
              v-for="doc in knowledgeStore.documents"
              :key="doc.id"
              class="flex items-center gap-3 px-3 py-2.5 rounded-lg border border-gray-100 dark:border-gray-700 bg-white dark:bg-gray-800"
          >
            <FileText :size="18" class="text-gray-400 shrink-0"/>
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium text-gray-700 dark:text-gray-200 truncate">{{ doc.fileName }}</div>
              <div class="text-xs text-gray-400 flex items-center gap-2 mt-0.5">
                <span>{{ formatFileSize(doc.fileSize) }}</span>
                <span v-if="doc.chunkCount > 0">{{ doc.chunkCount }} 块</span>
              </div>
            </div>
            <!-- 状态 -->
            <span v-if="doc.status === 'pending'" class="text-xs px-2 py-0.5 rounded-full bg-gray-100 dark:bg-gray-700 text-gray-500">等待中</span>
            <span v-else-if="doc.status === 'processing'" class="text-xs px-2 py-0.5 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 flex items-center gap-1">
              <Loader2 :size="10" class="animate-spin"/>处理中
            </span>
            <span v-else-if="doc.status === 'done'" class="text-xs px-2 py-0.5 rounded-full bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400">已完成</span>
            <span v-else class="text-xs px-2 py-0.5 rounded-full bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400" :title="doc.errorMessage">失败</span>
            <!-- 删除 -->
            <button
                class="p-1 rounded hover:bg-red-50 dark:hover:bg-red-900/20 text-gray-400 hover:text-red-500 transition-colors"
                @click="handleDeleteDoc(doc.id)"
            >
              <Trash2 :size="14"/>
            </button>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    <!-- 创建知识库对话框 -->
    <Dialog v-model:open="showCreateDialog">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>新建知识库</DialogTitle>
        </DialogHeader>
        <div class="space-y-3">
          <div class="space-y-1.5">
            <Label>名称</Label>
            <Input v-model="createForm.name" placeholder="例如: 产品文档"/>
          </div>
          <div class="space-y-1.5">
            <Label>描述 (可选)</Label>
            <Textarea v-model="createForm.description" placeholder="简要描述知识库用途" rows="3" class="resize-none"/>
          </div>
        </div>
        <DialogFooter class="gap-2 mt-4">
          <Button variant="outline" @click="showCreateDialog = false">取消</Button>
          <Button :disabled="!createForm.name.trim()" @click="handleCreate">创建</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 删除确认 -->
    <AlertDialog v-model:open="deleteConfirmOpen">
      <AlertDialogContent class="max-w-sm">
        <AlertDialogTitle>确认删除</AlertDialogTitle>
        <AlertDialogDescription>删除知识库后，其中的所有文档和索引将永久丢失，无法恢复。</AlertDialogDescription>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction @click="confirmDelete">删除</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue'
import {BookOpen, FileText, Layers, Loader2, MoreHorizontal, Plus, Trash2, Upload} from 'lucide-vue-next'
import {useKnowledgeStore} from '@/stores/knowledge'
import type {KnowledgeBase} from '@/types/knowledge'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import {Textarea} from '@/components/ui/textarea'
import {Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@/components/ui/dropdown-menu'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'

const knowledgeStore = useKnowledgeStore()

const showCreateDialog = ref(false)
const showDetail = ref(false)
const deleteConfirmOpen = ref(false)
const currentBase = ref<KnowledgeBase | null>(null)
const pendingDeleteId = ref<number | null>(null)
const dragOver = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const createForm = reactive({name: '', description: ''})

onMounted(() => {
    knowledgeStore.loadBases()
})

function openKnowledgeBase(kb: KnowledgeBase) {
    currentBase.value = kb
    showDetail.value = true
    knowledgeStore.loadDocuments(kb.id)
}

async function handleCreate() {
    if (!createForm.name.trim()) return
    await knowledgeStore.createBase(createForm.name.trim(), createForm.description.trim() || undefined)
    showCreateDialog.value = false
    createForm.name = ''
    createForm.description = ''
}

function handleDeleteBase(kb: KnowledgeBase) {
    pendingDeleteId.value = kb.id
    deleteConfirmOpen.value = true
}

async function confirmDelete() {
    if (pendingDeleteId.value) {
        await knowledgeStore.deleteBase(pendingDeleteId.value)
        pendingDeleteId.value = null
    }
    deleteConfirmOpen.value = false
}

function triggerFileInput() {
    fileInputRef.value?.click()
}

async function handleFileSelect(e: Event) {
    const input = e.target as HTMLInputElement
    if (!input.files || !currentBase.value) return
    await uploadFiles(Array.from(input.files))
    input.value = ''
}

async function handleDrop(e: DragEvent) {
    dragOver.value = false
    if (!e.dataTransfer?.files || !currentBase.value) return
    await uploadFiles(Array.from(e.dataTransfer.files))
}

async function uploadFiles(files: File[]) {
    if (!currentBase.value) return
    for (const file of files) {
        try {
            const doc = await knowledgeStore.uploadDoc(currentBase.value.id, file)
            knowledgeStore.pollDocumentStatus(currentBase.value.id, doc.id)
        } catch (e) {
            console.error('上传失败', e)
        }
    }
}

async function handleDeleteDoc(docId: number) {
    if (!currentBase.value) return
    await knowledgeStore.deleteDoc(currentBase.value.id, docId)
    await knowledgeStore.loadBases()
}

function formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + 'B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
    return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}
</script>
