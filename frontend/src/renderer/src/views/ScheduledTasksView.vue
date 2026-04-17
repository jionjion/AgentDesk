<template>
  <div class="h-full flex flex-col">
    <!-- 顶部工具栏 -->
    <div class="flex items-center justify-end px-6 py-4 border-b border-gray-100 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <Button variant="ghost" size="icon" @click="handleRefresh">
          <RefreshCw :size="16"/>
        </Button>
        <Button @click="openCreateDialog">
          <Plus :size="16" class="mr-1"/>
          新建定时任务
        </Button>
      </div>
    </div>

    <!-- 内容区 -->
    <ScrollArea class="flex-1">
      <div class="px-6 py-6 max-w-5xl">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">定时任务</h1>
        <p class="text-sm text-gray-500 dark:text-gray-400 mb-6">
          按计划自动执行任务，也可随时手动触发。在任意对话中描述你想定期做的事，即可快速创建
        </p>

        <!-- 标签页 + 排序 -->
        <div class="flex items-center justify-between mb-4">
          <Tabs v-model="store.activeTab">
            <TabsList>
              <TabsTrigger value="my-tasks" @click="store.activeTab = 'my-tasks'">我的定时任务</TabsTrigger>
              <TabsTrigger value="history" @click="handleSwitchToHistory">执行记录</TabsTrigger>
            </TabsList>
          </Tabs>
          <DropdownMenu v-if="store.activeTab === 'my-tasks'">
            <DropdownMenuTrigger as-child>
              <Button variant="ghost" size="sm" class="text-gray-500 dark:text-gray-400">
                <ArrowUpDown :size="14" class="mr-1"/>
                {{ store.sortOrder === 'desc' ? '按创建时间倒序' : '按创建时间正序' }}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem @click="store.sortOrder = 'desc'">按创建时间倒序</DropdownMenuItem>
              <DropdownMenuItem @click="store.sortOrder = 'asc'">按创建时间正序</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <!-- 我的定时任务 -->
        <template v-if="store.activeTab === 'my-tasks'">
          <div v-if="store.loading" class="flex items-center justify-center py-16">
            <Loader2 :size="24" class="animate-spin text-gray-400"/>
          </div>
          <EmptyState
              v-else-if="store.sortedTasks.length === 0"
              :icon="CalendarClock"
              title="还没有定时任务"
              description="创建定时任务，让搭子按时为你工作"
              action-label="新建定时任务"
              :action-icon="Plus"
              @action="openCreateDialog"
          />
          <div v-else class="grid grid-cols-2 gap-4">
            <TaskCard
                v-for="task in store.sortedTasks"
                :key="task.id"
                :task="task"
                @edit="openEditDialog(task)"
                @delete="handleDelete(task)"
                @toggle-enabled="store.toggleEnabled(task)"
                @run-now="handleRunNow(task)"
            />
          </div>
        </template>

        <!-- 执行记录 -->
        <template v-if="store.activeTab === 'history'">
          <TaskLogList :logs="store.logs" :loading="store.logsLoading"/>
        </template>
      </div>
    </ScrollArea>

    <!-- 创建/编辑对话框 -->
    <TaskFormDialog
        :open="formDialogOpen"
        :task="editingTask"
        @close="formDialogOpen = false"
        @saved="formDialogOpen = false"
    />

    <!-- 删除确认 -->
    <AlertDialog v-model:open="deleteConfirmOpen">
      <AlertDialogContent class="max-w-sm">
        <AlertDialogTitle>确认删除</AlertDialogTitle>
        <AlertDialogDescription>删除后无法恢复，确定要删除定时任务「{{ pendingDeleteTask?.name }}」吗？</AlertDialogDescription>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction @click="confirmDelete">删除</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {ArrowUpDown, CalendarClock, Loader2, Plus, RefreshCw} from 'lucide-vue-next'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Button} from '@/components/ui/button'
import {Tabs, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@/components/ui/dropdown-menu'
import {AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import TaskCard from '@/components/scheduled-tasks/TaskCard.vue'
import TaskFormDialog from '@/components/scheduled-tasks/TaskFormDialog.vue'
import TaskLogList from '@/components/scheduled-tasks/TaskLogList.vue'
import {useScheduledTasksStore} from '@/stores/scheduledTasks'
import {useSkillsStore} from '@/stores/skills'
import type {ScheduledTask} from '@/types/scheduledTask'

const store = useScheduledTasksStore()
const skillsStore = useSkillsStore()

const formDialogOpen = ref(false)
const editingTask = ref<ScheduledTask | null>(null)
const deleteConfirmOpen = ref(false)
const pendingDeleteTask = ref<ScheduledTask | null>(null)

function openCreateDialog() {
  editingTask.value = null
  formDialogOpen.value = true
}

function openEditDialog(task: ScheduledTask) {
  editingTask.value = task
  formDialogOpen.value = true
}

function handleDelete(task: ScheduledTask) {
  pendingDeleteTask.value = task
  deleteConfirmOpen.value = true
}

async function confirmDelete() {
  if (!pendingDeleteTask.value) return
  try {
    await store.deleteTask(pendingDeleteTask.value.id)
  } catch (e) {
    console.error('删除定时任务失败', e)
  }
  deleteConfirmOpen.value = false
  pendingDeleteTask.value = null
}

async function handleRunNow(task: ScheduledTask) {
  try {
    await store.runNow(task)
  } catch (e) {
    console.error('执行定时任务失败', e)
  }
}

function handleRefresh() {
  if (store.activeTab === 'my-tasks') {
    store.fetchTasks()
  } else {
    store.fetchLogs()
  }
}

function handleSwitchToHistory() {
  store.activeTab = 'history'
  if (store.logs.length === 0) {
    store.fetchLogs()
  }
}

onMounted(() => {
  store.fetchTasks()
  skillsStore.fetchSkills()
})
</script>
