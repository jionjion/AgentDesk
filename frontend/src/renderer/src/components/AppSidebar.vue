<template>
  <aside class="w-60 border-r border-gray-200 bg-gray-50/50 flex flex-col h-full">
    <!-- 新任务按钮 -->
    <div class="px-3 pt-3 pb-1">
      <router-link to="/chat" class="flex items-center gap-2 px-2 py-1.5 text-sm text-gray-700 hover:bg-gray-100 rounded-md">
        <el-icon :size="16" class="text-green-600"><Plus /></el-icon>
        <span>新任务</span>
      </router-link>
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
        <el-icon :size="16"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
        <el-tag v-if="item.badge" size="small" type="info" effect="plain" class="ml-auto scale-75">
          {{ item.badge }}
        </el-tag>
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
      <el-scrollbar>
        <div class="px-2 space-y-0.5">
          <div
            v-for="item in mockTaskList"
            :key="item"
            class="px-2 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded cursor-pointer truncate"
          >
            {{ item }}
          </div>
        </div>
      </el-scrollbar>
    </div>

    <!-- 用户信息 -->
    <div class="px-3 py-3 border-t border-gray-200 flex items-center gap-2">
      <div class="w-8 h-8 rounded-full bg-amber-100 flex items-center justify-center text-sm">
        🐶
      </div>
      <div class="flex-1 min-w-0">
        <div class="text-sm font-medium text-gray-800 truncate">{{ appStore.currentUser.name }}</div>
        <div class="text-xs text-blue-500">{{ appStore.currentUser.plan }}</div>
      </div>
      <el-button text size="small" circle>
        <el-icon :size="16" class="text-gray-400"><Setting /></el-icon>
      </el-button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { Plus, Ticket, Timer, ChatRound, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const appStore = useAppStore()

const navItems = [
  { path: '/skills', label: '技能', icon: Ticket, badge: '' },
  { path: '/scheduled-tasks', label: '定时任务', icon: Timer, badge: '' },
  { path: '/im-channel', label: 'IM 频道', icon: ChatRound, badge: 'Beta' }
]

const tabs = [
  { label: '任务', value: 'tasks' as const },
  { label: '频道', value: 'channels' as const }
]

const mockTaskList = [
  '安排计划测试Spring AI',
  'Image Upload',
  'Markdown文档转SQL问...',
  '青岛北换乘查询',
  '生成一个makdown文档, ...',
  '技术架构咨询',
  '车票工作卡管理',
  '功能测试与流程图设计'
]

function isActive(path: string): boolean {
  return route.path === path
}
</script>
