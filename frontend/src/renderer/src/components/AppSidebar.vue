<template>
  <aside class="w-60 border-r border-gray-200 bg-gray-50/50 flex flex-col h-full">
    <!-- 新任务按钮 -->
    <div class="px-3 pt-3 pb-1">
      <button
        class="flex items-center gap-2 px-2 py-1.5 text-sm text-gray-700 hover:bg-gray-100 rounded-md w-full"
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
        :class="[isActive(item.path) ? 'bg-gray-200/80 text-gray-900 font-medium' : 'text-gray-600 hover:bg-gray-100']"
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
      <div class="flex bg-gray-100 rounded-md p-0.5">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="flex-1 text-xs py-1.5 rounded text-center transition-colors"
          :class="[appStore.activeTab === tab.value ? 'bg-white text-gray-900 shadow-sm font-medium' : 'text-gray-500 hover:text-gray-700']"
          @click="appStore.activeTab = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- 任务/频道列表 -->
    <div class="flex-1 overflow-hidden mt-2">
      <div class="px-3 mb-1">
        <span class="text-xs text-gray-400">{{ appStore.activeTab === 'tasks' ? '任务' : '频道' }}</span>
      </div>
      <ScrollArea class="h-full">
        <div class="px-2 space-y-0.5">
          <div
            v-for="session in chatStore.sessions"
            :key="session.id"
            class="group flex items-center gap-1 px-2 py-1.5 text-sm rounded cursor-pointer truncate"
            :class="chatStore.currentSessionId === session.id
              ? 'bg-green-50 text-green-700 font-medium'
              : 'text-gray-600 hover:bg-gray-100'"
            @click="handleSwitchSession(session.id)"
            @contextmenu.prevent="handleContextMenu($event, session.id)"
          >
            <span class="flex-1 truncate">{{ session.title }}</span>
            <X
              :size="14"
              class="flex-shrink-0 opacity-0 group-hover:opacity-100 text-gray-400 hover:text-red-500 transition-opacity"
              @click.stop="handleDeleteSession(session.id)"
            />
          </div>
          <div v-if="chatStore.sessions.length === 0" class="px-2 py-4 text-center">
            <span class="text-xs text-gray-400">暂无会话</span>
          </div>
        </div>
      </ScrollArea>
    </div>

    <!-- 用户信息 -->
    <div class="px-3 py-3 border-t border-gray-200 flex items-center gap-2">
      <div class="w-8 h-8 rounded-full bg-amber-100 flex items-center justify-center text-sm">
        &#x1F436;
      </div>
      <div class="flex-1 min-w-0">
        <div class="text-sm font-medium text-gray-800 truncate">{{ appStore.currentUser.name }}</div>
        <div class="text-xs text-blue-500">{{ appStore.currentUser.plan }}</div>
      </div>
      <Button variant="ghost" size="icon" class="h-8 w-8">
        <Settings :size="16" class="text-gray-400" />
      </Button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useChatStore } from '@/stores/chat'
import { Plus, Ticket, Timer, MessageCircle, Settings, X } from 'lucide-vue-next'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const chatStore = useChatStore()

const navItems = [
  { path: '/skills', label: '技能', icon: Ticket, badge: '' },
  { path: '/scheduled-tasks', label: '定时任务', icon: Timer, badge: '' },
  { path: '/im-channel', label: 'IM 频道', icon: MessageCircle, badge: 'Beta' }
]

const tabs = [
  { label: '任务', value: 'tasks' as const },
  { label: '频道', value: 'channels' as const }
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
  await chatStore.removeSession(id)
  if (chatStore.currentSessionId) {
    router.push(`/chat/${chatStore.currentSessionId}`)
  } else {
    router.push('/chat')
  }
}

function handleContextMenu(_event: MouseEvent, _sessionId: string) {
  // 未来可扩展右键菜单 (重命名等)
}

onMounted(() => {
  chatStore.loadSessions()
})
</script>
