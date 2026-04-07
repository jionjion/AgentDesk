<template>
  <aside class="w-60 border-r border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-900 flex flex-col h-full">
    <!-- 新任务按钮 -->
    <div class="px-3 pt-3 pb-1">
      <button
        class="flex items-center gap-2 px-2 py-1.5 text-sm text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-md w-full"
        @click="handleNewSession"
      >
        <Plus :size="16" class="text-green-600" />
        <span>新任务</span>
      </button>
    </div>

    <!-- 导航菜单 -->
    <nav class="px-3 space-y-0.5">
      <router-link
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="nav-item flex items-center gap-2 px-2 py-1.5 text-sm rounded-md"
        :class="[isActive(item.path) ? 'bg-gray-200/80 dark:bg-gray-700 text-gray-900 dark:text-gray-100 font-medium' : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800']"
      >
        <component :is="item.icon" :size="16" />
        <span>{{ item.label }}</span>
        <Badge v-if="item.badge" variant="secondary" class="ml-auto text-[10px] px-1.5 py-0">
          {{ item.badge }}
        </Badge>
      </router-link>
    </nav>

    <!-- 标签切换 -->
    <div class="px-3 mt-3">
      <div class="flex bg-gray-100 dark:bg-gray-800 rounded-md p-0.5">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="flex-1 text-xs py-1.5 rounded text-center transition-colors"
          :class="[appStore.activeTab === tab.value ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm font-medium' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300']"
          @click="appStore.activeTab = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- 任务/频道列表 -->
    <div class="flex-1 overflow-hidden mt-2">
      <div class="px-3 mb-1">
        <span class="text-xs text-gray-400 dark:text-gray-500">{{ appStore.activeTab === 'tasks' ? '任务' : '频道' }}</span>
      </div>
      <ScrollArea class="h-full">
        <div class="px-2 space-y-0.5">
          <ContextMenu v-for="session in chatStore.sortedSessions" :key="session.id">
            <ContextMenuTrigger as-child>
              <div
                class="group flex items-center gap-1 px-2 py-1.5 text-sm rounded cursor-pointer truncate"
                :class="chatStore.currentSessionId === session.id
                  ? 'bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-400 font-medium'
                  : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'"
                @click="handleSwitchSession(session.id)"
              >
                <Pin v-if="chatStore.isPinned(session.id)" :size="12" class="flex-shrink-0 text-amber-500" />
                <span class="flex-1 truncate">{{ session.title }}</span>
                <X
                  :size="14"
                  class="flex-shrink-0 opacity-0 group-hover:opacity-100 text-gray-400 hover:text-red-500 transition-opacity"
                  @click.stop="handleDeleteSession(session.id)"
                />
              </div>
            </ContextMenuTrigger>
            <ContextMenuContent class="w-40">
              <ContextMenuItem class="cursor-pointer" @select="handleRenameSession(session.id, session.title)">
                <Edit3 :size="14" />
                <span>重命名</span>
              </ContextMenuItem>
              <ContextMenuItem class="cursor-pointer" @select="chatStore.togglePin(session.id)">
                <component :is="chatStore.isPinned(session.id) ? PinOff : Pin" :size="14" />
                <span>{{ chatStore.isPinned(session.id) ? '取消置顶' : '置顶' }}</span>
              </ContextMenuItem>
              <ContextMenuSeparator />
              <ContextMenuItem class="cursor-pointer text-red-600 dark:text-red-400 focus:text-red-600 dark:focus:text-red-400" @select="handleDeleteSession(session.id)">
                <Trash2 :size="14" />
                <span>删除</span>
              </ContextMenuItem>
            </ContextMenuContent>
          </ContextMenu>
          <div v-if="chatStore.sessions.length === 0" class="px-2 py-4 text-center">
            <span class="text-xs text-gray-400">暂无会话</span>
          </div>
        </div>
      </ScrollArea>
    </div>

    <!-- 用户信息 -->
    <div class="px-3 py-3 border-t border-gray-200 dark:border-gray-700 flex items-center gap-2">
      <div class="w-8 h-8 rounded-full bg-amber-100 dark:bg-amber-900/40 flex items-center justify-center">
        <User :size="16" class="text-gray-600 dark:text-gray-300" />
      </div>
      <div class="flex-1 min-w-0">
        <div class="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">{{ appStore.currentUser.name }}</div>
        <div class="text-xs text-blue-500">{{ appStore.currentUser.plan }}</div>
      </div>
      <Popover>
        <PopoverTrigger as-child>
          <Button variant="ghost" size="icon" class="h-8 w-8">
            <SettingsIcon :size="16" class="text-gray-400" />
          </Button>
        </PopoverTrigger>
        <PopoverContent side="top" align="end" class="w-48 p-1 dark:bg-gray-800 dark:border-gray-700">
          <button
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            @click="handleOpenSettings"
          >
            <SettingsIcon :size="16" class="text-gray-500 dark:text-gray-400" />
            <span>设置</span>
          </button>
          <!-- 主题子菜单 -->
          <Popover>
            <PopoverTrigger as-child>
              <button
                class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                <Palette :size="16" class="text-gray-500 dark:text-gray-400" />
                <span>主题</span>
                <ChevronRight :size="14" class="ml-auto text-gray-400" />
              </button>
            </PopoverTrigger>
            <PopoverContent side="right" align="start" class="w-36 p-1 dark:bg-gray-800 dark:border-gray-700">
              <button
                v-for="item in themeOptions"
                :key="item.value"
                class="flex items-center gap-2 w-full px-3 py-2 text-sm rounded-md transition-colors"
                :class="appStore.theme === item.value
                  ? 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/20'
                  : 'text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700'"
                @click="appStore.setTheme(item.value)"
              >
                <component :is="item.icon" :size="16" />
                <span>{{ item.label }}</span>
                <Check v-if="appStore.theme === item.value" :size="14" class="ml-auto" />
              </button>
            </PopoverContent>
          </Popover>
          <button
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            @click="handleOpenHelp"
          >
            <BookOpen :size="16" class="text-gray-500 dark:text-gray-400" />
            <span>帮助文档</span>
          </button>
          <button
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            @click="handleOpenChangelog"
          >
            <FileText :size="16" class="text-gray-500 dark:text-gray-400" />
            <span>更新日志</span>
          </button>
          <button
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 dark:text-gray-200 rounded-md hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            @click="handleOpenAbout"
          >
            <Info :size="16" class="text-gray-500 dark:text-gray-400" />
            <span>关于我们</span>
          </button>
          <div class="my-1 border-t border-gray-200 dark:border-gray-700" />
          <button
            class="flex items-center gap-2 w-full px-3 py-2 text-sm text-red-600 dark:text-red-400 rounded-md hover:bg-red-50 dark:hover:bg-red-900/30 transition-colors"
            @click="handleLogout"
          >
            <LogOut :size="16" class="text-red-500" />
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
  </aside>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import type { ThemeMode } from '@/stores/app'
import { useChatStore } from '@/stores/chat'
import { Plus, Ticket, Timer, MessageCircle, Settings as SettingsIcon, X, BookOpen, FileText, Info, LogOut, User, Sun, Moon, Monitor, Palette, ChevronRight, Check, Edit3, Pin, PinOff, Trash2 } from 'lucide-vue-next'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import { ContextMenu, ContextMenuTrigger, ContextMenuContent, ContextMenuItem, ContextMenuSeparator } from '@/components/ui/context-menu'
import {
  AlertDialog, AlertDialogContent, AlertDialogTitle,
  AlertDialogDescription, AlertDialogFooter, AlertDialogCancel, AlertDialogAction
} from '@/components/ui/alert-dialog'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const chatStore = useChatStore()

const deleteConfirmOpen = ref(false)
const pendingDeleteId = ref<string | null>(null)

const navItems = [
  { path: '/skills', label: '技能', icon: Ticket, badge: '' },
  { path: '/scheduled-tasks', label: '定时任务', icon: Timer, badge: '' },
  { path: '/im-channel', label: 'IM 频道', icon: MessageCircle, badge: 'Beta' }
]

const tabs = [
  { label: '任务', value: 'tasks' as const },
  { label: '频道', value: 'channels' as const }
]

const themeOptions: { label: string; value: ThemeMode; icon: typeof Sun }[] = [
  { label: '亮色', value: 'light', icon: Sun },
  { label: '暗色', value: 'dark', icon: Moon },
  { label: '自动', value: 'auto', icon: Monitor }
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
  const newTitle = window.prompt('重命名会话', currentTitle)
  if (newTitle && newTitle.trim() && newTitle !== currentTitle) {
    chatStore.renameSession(id, newTitle.trim())
  }
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
