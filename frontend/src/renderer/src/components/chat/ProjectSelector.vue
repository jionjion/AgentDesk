<template>
  <Popover v-model:open="open">
    <PopoverTrigger as-child>
      <Button variant="ghost" size="sm" class="text-xs gap-1 h-8 px-2">
        <FolderOpen :size="14" :class="currentProject ? 'text-violet-500' : ''"/>
        <span class="text-xs" :class="currentProject ? 'text-violet-600 dark:text-violet-400' : 'text-gray-500 dark:text-gray-400'">
          {{ currentProject ? currentProject.name : '选择项目' }}
        </span>
        <ChevronDown :size="12" class="text-gray-400"/>
      </Button>
    </PopoverTrigger>
    <PopoverContent align="start" side="top" class="w-80 p-0">
      <!-- 当前项目详情 -->
      <div v-if="currentProject" class="px-3 py-2.5 border-b border-gray-200 dark:border-gray-700 space-y-1">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium truncate">{{ currentProject.name }}</span>
          <button
              class="text-[11px] text-gray-400 hover:text-red-500 shrink-0"
              title="解除当前会话与项目的绑定"
              @click="unbindSession"
          >
            解绑
          </button>
        </div>
        <p class="text-[11px] text-gray-500 dark:text-gray-400 truncate">
          路径：{{ currentLocation?.rootPath || '本机未绑定目录' }}
        </p>
        <p class="text-[11px] text-gray-500 dark:text-gray-400">
          在线状态 / Python：未检测（待接入）
        </p>
        <Button
            v-if="!currentLocation"
            variant="outline" size="sm" class="w-full h-7 text-xs mt-1"
            @click="bindCurrentToFolder"
        >
          绑定本机目录
        </Button>
      </div>

      <!-- 项目列表 -->
      <div class="max-h-52 overflow-y-auto py-1">
        <div
            v-for="project in projectsStore.projects" :key="project.id"
            class="flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            @click="switchProject(project.id)"
        >
          <Check
              :size="12"
              :class="project.id === currentProjectId ? 'text-violet-500' : 'text-transparent'"
          />
          <div class="flex-1 min-w-0">
            <p class="text-sm truncate">{{ project.name }}</p>
            <p class="text-[10px] text-gray-400 truncate">
              {{ projectsStore.locations[project.id]?.rootPath || '本机未绑定目录' }}
            </p>
          </div>
        </div>
        <div v-if="projectsStore.projects.length === 0" class="px-3 py-4 text-xs text-gray-400 text-center">
          暂无项目
        </div>
      </div>

      <!-- 操作 -->
      <div class="border-t border-gray-200 dark:border-gray-700 py-1">
        <button
            class="w-full flex items-center gap-2 px-3 py-2 text-xs text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800"
            @click="createFromFolder"
        >
          <FolderPlus :size="14"/>
          从文件夹创建项目
        </button>
      </div>
    </PopoverContent>
  </Popover>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {Check, ChevronDown, FolderOpen, FolderPlus} from 'lucide-vue-next'
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover'
import {Button} from '@/components/ui/button'
import {useProjectsStore} from '@/stores/projects'
import {useChatStore} from '@/stores/chat'
import {useToast} from '@/components/ui/toast'

const projectsStore = useProjectsStore()
const chatStore = useChatStore()
const {toast} = useToast()

const open = ref(false)

const currentSession = computed(() =>
    chatStore.sessions.find(s => s.id === chatStore.currentSessionId)
)
/** 已有会话取会话绑定的项目; 新对话 (无会话) 取待绑定项目 */
const currentProjectId = computed(() =>
    currentSession.value ? currentSession.value.projectId ?? null : chatStore.pendingProjectId
)
const currentProject = computed(() =>
    currentProjectId.value ? projectsStore.projectMap[currentProjectId.value] ?? null : null
)
const currentLocation = computed(() =>
    currentProjectId.value ? projectsStore.locations[currentProjectId.value] ?? null : null
)

onMounted(() => {
  if (!projectsStore.loaded) {
    void projectsStore.load()
  }
})

/** 从文件夹创建项目并绑定到当前会话 */
async function createFromFolder() {
  const dir = await window.electronAPI?.dialog.openDirectory()
  if (!dir) return
  try {
    const name = dir.split(/[/\\]/).filter(Boolean).pop() || dir
    const project = await projectsStore.createWithLocation(name, dir)
    await bindToCurrentSession(project.id)
    toast({title: `已创建项目 ${project.name}`})
  } catch (e) {
    toast({title: '创建项目失败', description: String(e), variant: 'destructive'})
  }
}

/** 为当前项目绑定本机目录 */
async function bindCurrentToFolder() {
  if (!currentProjectId.value) return
  const dir = await window.electronAPI?.dialog.openDirectory()
  if (!dir) return
  try {
    await projectsStore.bindLocation(currentProjectId.value, dir)
    toast({title: '已绑定本机目录'})
  } catch (e) {
    toast({title: '绑定目录失败', description: String(e), variant: 'destructive'})
  }
}

/** 切换当前会话绑定的项目 */
async function switchProject(projectId: string) {
  if (projectId === currentProjectId.value) {
    open.value = false
    return
  }
  await bindToCurrentSession(projectId)
  open.value = false
}

/** 解除当前会话与项目的绑定 */
async function unbindSession() {
  await bindToCurrentSession(null)
  open.value = false
}

async function bindToCurrentSession(projectId: string | null) {
  const sessionId = chatStore.currentSessionId
  if (!sessionId) {
    // 新对话尚未创建会话: 记录为待绑定项目, 首次提问时随会话创建一并绑定
    chatStore.pendingProjectId = projectId
    if (projectId) projectsStore.rememberLastProject(projectId)
    return
  }
  try {
    await projectsStore.bindSession(sessionId, projectId)
    const session = chatStore.sessions.find(s => s.id === sessionId)
    if (session) {
      session.projectId = projectId
      session.projectName = projectId ? projectsStore.projectMap[projectId]?.name ?? null : null
    }
  } catch (e) {
    toast({title: '切换项目失败', description: String(e), variant: 'destructive'})
  }
}
</script>
