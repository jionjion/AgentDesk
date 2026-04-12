<template>
  <aside class="w-60 border-r border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-900 flex flex-col h-full">
    <!-- 新任务按钮 -->
    <div class="px-3 pt-3 pb-1">
      <button
          class="flex items-center gap-2 px-2 py-1.5 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-md w-full"
          @click="handleNewSession"
      >
        <Plus :size="16" class="text-violet-600"/>
        <span>新任务</span>
      </button>
    </div>

    <!-- 导航菜单 -->
    <nav class="px-3 space-y-0.5">
      <template v-for="item in navItems" :key="item.path">
        <div
            v-if="item.disabled"
            class="nav-item flex items-center gap-2 px-2 py-1.5 text-sm rounded-md text-gray-400 dark:text-gray-500 cursor-not-allowed opacity-60"
        >
          <component :is="item.icon" :size="16"/>
          <span>{{ item.label }}</span>
          <Badge v-if="item.badge" variant="secondary" class="ml-auto text-[10px] px-1.5 py-0 bg-gray-200 text-gray-500 dark:bg-gray-700 dark:text-gray-400">
            {{ item.badge }}
          </Badge>
        </div>
        <router-link
            v-else
            :to="item.path"
            class="nav-item flex items-center gap-2 px-2 py-1.5 text-sm rounded-md"
            :class="[isActive(item.path) ? 'bg-gray-200/80 dark:bg-gray-700 text-gray-900 dark:text-gray-100 font-medium' : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800']"
        >
          <component :is="item.icon" :size="16"/>
          <span>{{ item.label }}</span>
          <Badge v-if="item.badge" variant="secondary" class="ml-auto text-[10px] px-1.5 py-0 bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
            {{ item.badge }}
          </Badge>
        </router-link>
      </template>
    </nav>

    <!-- 任务列表 -->
    <div class="flex-1 overflow-hidden mt-2">
      <div class="px-3 mb-1 flex items-center justify-between">
        <span class="text-xs text-gray-400 dark:text-gray-500">任务</span>
        <button
            class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
            @click="showFilter = !showFilter"
        >
          <Search :size="12"/>
        </button>
      </div>
      <div v-if="showFilter" class="px-3 mb-1">
        <div class="flex items-center gap-1 px-2 py-1 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-md">
          <Search :size="12" class="text-gray-400 flex-shrink-0"/>
          <input
              ref="filterInputRef"
              v-model="filterKeyword"
              type="text"
              placeholder="过滤会话..."
              class="flex-1 bg-transparent border-none outline-none text-xs text-gray-700 dark:text-gray-300 placeholder-gray-400"
          />
          <button
              v-if="filterKeyword"
              class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              @click="filterKeyword = ''"
          >
            <X :size="12"/>
          </button>
        </div>
      </div>
      <ScrollArea class="h-full">
        <div class="px-2 space-y-0.5">
          <ContextMenu v-for="session in filteredSessions" :key="session.id">
            <ContextMenuTrigger as-child>
              <div
                  class="group flex items-center gap-1 px-2 py-1.5 text-sm rounded cursor-pointer truncate"
                  :class="chatStore.currentSessionId === session.id
                  ? 'bg-violet-50 dark:bg-violet-900/30 text-violet-700 dark:text-violet-400 font-medium'
                  : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'"
                  @click="handleSwitchSession(session.id)"
              >
                <Pin v-if="chatStore.isPinned(session.id)" :size="12" class="flex-shrink-0 text-amber-500"/>
                <span class="flex-1 truncate">{{ session.title }}</span>
              </div>
            </ContextMenuTrigger>
            <ContextMenuContent class="w-40">
              <ContextMenuItem class="cursor-pointer" @select="handleRenameSession(session.id, session.title)">
                <Edit3 :size="14"/>
                <span>重命名</span>
              </ContextMenuItem>
              <ContextMenuItem class="cursor-pointer" @select="chatStore.togglePin(session.id)">
                <component :is="chatStore.isPinned(session.id) ? PinOff : Pin" :size="14"/>
                <span>{{ chatStore.isPinned(session.id) ? '取消置顶' : '置顶' }}</span>
              </ContextMenuItem>
              <ContextMenuItem class="cursor-pointer" @select="chatStore.exportSession(session.id)">
                <Download :size="14"/>
                <span>导出</span>
              </ContextMenuItem>
              <ContextMenuSeparator/>
              <ContextMenuItem class="cursor-pointer text-red-600 dark:text-red-400 focus:text-red-600 dark:focus:text-red-400" @select="handleDeleteSession(session.id)">
                <Trash2 :size="14"/>
                <span>删除</span>
              </ContextMenuItem>
            </ContextMenuContent>
          </ContextMenu>
          <div v-if="filteredSessions.length === 0" class="px-2 py-4 text-center">
            <span class="text-xs text-gray-400">{{ filterKeyword ? '无匹配会话' : '暂无会话' }}</span>
          </div>
        </div>
      </ScrollArea>
    </div>

    <!-- 用户信息 -->
    <div class="px-3 py-3 border-t border-gray-200 dark:border-gray-700 flex items-center gap-2">
      <div class="w-8 h-8 rounded-full bg-amber-100 dark:bg-amber-900/40 flex items-center justify-center overflow-hidden">
        <img v-if="appStore.currentUser.avatar && !avatarError" :src="appStore.currentUser.avatar" alt="头像" class="h-full w-full object-cover" @error="avatarError = true"/>
        <User v-else :size="16" class="text-gray-600 dark:text-gray-300"/>
      </div>
      <div class="flex-1 min-w-0">
        <div class="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">{{ appStore.currentUser.name }}</div>
        <div class="text-xs text-violet-500">{{ appStore.currentUser.plan }}</div>
      </div>
      <Popover>
        <PopoverTrigger as-child>
          <Button variant="ghost" size="icon" class="h-8 w-8">
            <SettingsIcon :size="16" class="text-gray-400"/>
          </Button>
        </PopoverTrigger>
        <PopoverContent side="top" align="end" class="w-48 p-1 dark:bg-gray-800 dark:border-gray-700">
          <button
              class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              @click="handleOpenSettings"
          >
            <SettingsIcon :size="16" class="text-gray-500 dark:text-gray-400"/>
            <span>设置</span>
          </button>
          <!-- 主题子菜单 -->
          <Popover>
            <PopoverTrigger as-child>
              <button
                  class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                <Palette :size="16" class="text-gray-500 dark:text-gray-400"/>
                <span>主题</span>
                <ChevronRight :size="14" class="ml-auto text-gray-400"/>
              </button>
            </PopoverTrigger>
            <PopoverContent side="right" align="start" class="w-36 p-1 dark:bg-gray-800 dark:border-gray-700">
              <button
                  v-for="item in themeOptions"
                  :key="item.value"
                  class="flex items-center gap-2 w-full px-3 py-2 text-sm rounded-md transition-colors"
                  :class="appStore.theme === item.value
                  ? 'text-violet-600 dark:text-violet-400 bg-violet-50 dark:bg-violet-900/20'
                  : 'text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700'"
                  @click="appStore.setTheme(item.value)"
              >
                <component :is="item.icon" :size="16"/>
                <span>{{ item.label }}</span>
                <Check v-if="appStore.theme === item.value" :size="14" class="ml-auto"/>
              </button>
            </PopoverContent>
          </Popover>
          <button
              class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              @click="handleOpenHelp"
          >
            <BookOpen :size="16" class="text-gray-500 dark:text-gray-400"/>
            <span>帮助文档</span>
          </button>
          <button
              class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              @click="handleOpenChangelog"
          >
            <FileText :size="16" class="text-gray-500 dark:text-gray-400"/>
            <span>更新日志</span>
          </button>
          <button
              class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              @click="handleOpenAbout"
          >
            <Info :size="16" class="text-gray-500 dark:text-gray-400"/>
            <span>关于我们</span>
          </button>
          <div class="my-1 border-t border-gray-200 dark:border-gray-700"/>
          <button
              class="flex items-center gap-2 w-full px-3 py-2 text-sm text-red-600 dark:text-red-400 rounded-md hover:bg-red-50 dark:hover:bg-red-900/30 transition-colors"
              @click="handleLogout"
          >
            <LogOut :size="16" class="text-red-500"/>
            <span>退出登录</span>
          </button>
        </PopoverContent>
      </Popover>
    </div>

    <!-- 删除确认对话框 -->
    <AlertDialog v-model:open="deleteConfirmOpen">
      <AlertDialogContent class="max-w-sm">
        <AlertDialogTitle>确认删除</AlertDialogTitle>
        <AlertDialogDescription>删除后无法恢复，确定要删除该会话吗？</AlertDialogDescription>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction @click="confirmDeleteSession">删除</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <!-- 重命名对话框 -->
    <Dialog v-model:open="renameDialogOpen">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>重命名会话</DialogTitle>
        </DialogHeader>
        <Input
            v-model="renameTitle"
            placeholder="请输入新标题"
            @keydown.enter="confirmRename"
        />
        <DialogFooter class="gap-2">
          <Button variant="outline" @click="renameDialogOpen = false">取消</Button>
          <Button @click="confirmRename">确认</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </aside>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import type {ThemeMode} from '@/stores/app'
import {useAppStore} from '@/stores/app'
import {useChatStore} from '@/stores/chat'
import {BookOpen, Check, ChevronRight, Download, Edit3, FileText, Info, LogOut, Monitor, Moon, Palette, Pin, PinOff, Plus, Search, Settings as SettingsIcon, Sun, Ticket, Timer, Trash2, User, X} from 'lucide-vue-next'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Badge} from '@/components/ui/badge'
import {Button} from '@/components/ui/button'
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover'
import {ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuSeparator, ContextMenuTrigger} from '@/components/ui/context-menu'
import {AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import {Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {Input} from '@/components/ui/input'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const chatStore = useChatStore()

const deleteConfirmOpen = ref(false)
const pendingDeleteId = ref<string | null>(null)
const renameDialogOpen = ref(false)
const renameSessionId = ref<string | null>(null)
const renameTitle = ref('')
const showFilter = ref(false)
const filterKeyword = ref('')
const filterInputRef = ref<HTMLInputElement>()
const avatarError = ref(false)

const filteredSessions = computed(() => {
  const kw = filterKeyword.value.trim().toLowerCase()
  if (!kw) return chatStore.sortedSessions
  return chatStore.sortedSessions.filter(s => s.title.toLowerCase().includes(kw))
})

watch(showFilter, (val) => {
  if (val) {
    nextTick(() => filterInputRef.value?.focus())
  } else {
    filterKeyword.value = ''
  }
})

watch(() => appStore.currentUser.avatar, () => {
  avatarError.value = false
})

const navItems = [
  {path: '/skills', label: '技能', icon: Ticket, badge: '即将推出', disabled: true},
  {path: '/scheduled-tasks', label: '定时任务', icon: Timer, badge: '即将推出', disabled: true}
]

const themeOptions: { label: string; value: ThemeMode; icon: typeof Sun }[] = [
  {label: '亮色', value: 'light', icon: Sun},
  {label: '暗色', value: 'dark', icon: Moon},
  {label: '自动', value: 'auto', icon: Monitor}
]

function isActive(path: string): boolean {
  return route.path === path
}

function handleNewSession() {
  chatStore.currentSessionId = null
  router.push('/chat')
}

function handleSwitchSession(id: string) {
  chatStore.switchSession(id)
  router.push(`/chat/${id}`)
}

async function handleDeleteSession(id: string) {
  pendingDeleteId.value = id
  deleteConfirmOpen.value = true
}

async function confirmDeleteSession() {
  if (!pendingDeleteId.value) return
  const id = pendingDeleteId.value
  deleteConfirmOpen.value = false
  pendingDeleteId.value = null
  await chatStore.removeSession(id)
  if (chatStore.currentSessionId) {
    router.push(`/chat/${chatStore.currentSessionId}`)
  } else {
    router.push('/chat')
  }
}

function handleRenameSession(id: string, currentTitle: string) {
  renameSessionId.value = id
  renameTitle.value = currentTitle
  renameDialogOpen.value = true
}

function confirmRename() {
  if (!renameSessionId.value) return
  const newTitle = renameTitle.value.trim()
  if (newTitle) {
    chatStore.renameSession(renameSessionId.value, newTitle)
  }
  renameDialogOpen.value = false
  renameSessionId.value = null
}

function handleOpenSettings() {
  router.push('/settings')
}

function handleOpenHelp() {
  window.open('https://github.com/jionjion', '_blank')
}

function handleOpenChangelog() {
  window.open('https://github.com/', '_blank')
}

function handleOpenAbout() {
  window.open('https://github.com/', '_blank')
}

function handleLogout() {
  router.push('/login')
}

onMounted(() => {
  chatStore.loadSessions()
})
</script>
