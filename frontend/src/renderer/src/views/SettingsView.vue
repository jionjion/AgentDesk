<template>
  <div class="h-screen w-screen flex flex-col overflow-hidden bg-white dark:bg-gray-900">
    <TitleBar />
    <div class="flex flex-1 overflow-hidden">
    <!-- 左侧导航 -->
    <aside class="w-56 border-r border-gray-200 dark:border-gray-700 flex flex-col h-full">
      <!-- 返回按钮 -->
      <div class="px-4 pt-4 pb-2">
        <button
          class="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 transition-colors"
          @click="router.back()"
        >
          <ArrowLeft :size="16" />
          <span>返回应用</span>
        </button>
      </div>

      <!-- 导航分组 -->
      <nav class="flex-1 px-3 py-2 space-y-4 overflow-y-auto">
        <!-- 通用 -->
        <div>
          <div class="px-2 mb-1 text-xs text-gray-400 dark:text-gray-500 font-medium">通用</div>
          <div class="space-y-0.5">
            <button
              v-for="item in generalItems"
              :key="item.key"
              class="flex items-center gap-2 w-full px-2 py-1.5 text-sm rounded-md transition-colors"
              :class="activeSection === item.key
                ? 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 font-medium'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-gray-100'"
              @click="activeSection = item.key"
            >
              <component :is="item.icon" :size="16" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>

        <!-- 数据与隐私 -->
        <div>
          <div class="px-2 mb-1 text-xs text-gray-400 dark:text-gray-500 font-medium">数据与隐私</div>
          <div class="space-y-0.5">
            <button
              v-for="item in privacyItems"
              :key="item.key"
              class="flex items-center gap-2 w-full px-2 py-1.5 text-sm rounded-md transition-colors"
              :class="activeSection === item.key
                ? 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 font-medium'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-gray-100'"
              @click="activeSection = item.key"
            >
              <component :is="item.icon" :size="16" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>

        <!-- 扩展与集成 -->
        <div>
          <div class="px-2 mb-1 text-xs text-gray-400 dark:text-gray-500 font-medium">扩展与集成</div>
          <div class="space-y-0.5">
            <button
              v-for="item in extensionItems"
              :key="item.key"
              class="flex items-center gap-2 w-full px-2 py-1.5 text-sm rounded-md transition-colors"
              :class="activeSection === item.key
                ? 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 font-medium'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-gray-100'"
              @click="activeSection = item.key"
            >
              <component :is="item.icon" :size="16" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>

        <!-- 高级设置 -->
        <div>
          <div class="px-2 mb-1 text-xs text-gray-400 dark:text-gray-500 font-medium">高级设置</div>
          <div class="space-y-0.5">
            <button
              v-for="item in advancedItems"
              :key="item.key"
              class="flex items-center gap-2 w-full px-2 py-1.5 text-sm rounded-md transition-colors"
              :class="activeSection === item.key
                ? 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 font-medium'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-gray-100'"
              @click="activeSection = item.key"
            >
              <component :is="item.icon" :size="16" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>

        <!-- 开发者 -->
        <div>
          <div class="px-2 mb-1 text-xs text-gray-400 dark:text-gray-500 font-medium">开发者</div>
          <div class="space-y-0.5">
            <button
              v-for="item in developerItems"
              :key="item.key"
              class="flex items-center gap-2 w-full px-2 py-1.5 text-sm rounded-md transition-colors"
              :class="activeSection === item.key
                ? 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 font-medium'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-gray-100'"
              @click="activeSection = item.key"
            >
              <component :is="item.icon" :size="16" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>
      </nav>

      <!-- 底部升级方案 -->
      <div class="px-3 py-3 border-t border-gray-200 dark:border-gray-700">
        <button
          class="flex items-center gap-2 w-full px-2 py-1.5 text-sm text-green-600 dark:text-green-400 rounded-md hover:bg-green-50 dark:hover:bg-green-900/20 transition-colors"
          @click="activeSection = 'upgrade'"
        >
          <Sparkles :size="16" />
          <span>升级方案</span>
        </button>
      </div>
    </aside>

    <!-- 右侧内容区 -->
    <main class="flex-1 overflow-y-auto">
      <div class="max-w-2xl mx-auto px-8 py-8">
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-1">{{ activeSectionLabel }}</h1>
        <p class="text-sm text-gray-500 dark:text-gray-400 mb-6">{{ activeSectionDescription }}</p>

        <!-- 占位内容 -->
        <div class="rounded-lg border border-dashed border-gray-300 dark:border-gray-600 p-12 text-center">
          <div class="text-gray-400 dark:text-gray-500 text-sm">设置内容开发中...</div>
        </div>
      </div>
    </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import TitleBar from '@/components/TitleBar.vue'
import {
  ArrowLeft,
  SlidersHorizontal,
  User,
  Monitor,
  Keyboard,
  Shield,
  Link,
  Cpu,
  Box,
  FlaskConical,
  RefreshCw,
  Sparkles
} from 'lucide-vue-next'

const router = useRouter()
const activeSection = ref('preferences')

const generalItems = [
  { key: 'preferences', label: '偏好设置', icon: SlidersHorizontal },
  { key: 'profile', label: '个人资料', icon: User },
  { key: 'system', label: '系统设置', icon: Monitor },
  { key: 'shortcuts', label: '快捷键', icon: Keyboard }
]

const privacyItems = [
  { key: 'privacy', label: '隐私', icon: Shield }
]

const extensionItems = [
  { key: 'connectors', label: '连接器', icon: Link },
  { key: 'mcp', label: 'MCP 服务', icon: Cpu }
]

const advancedItems = [
  { key: 'sandbox', label: '虚拟机沙盒', icon: Box }
]

const developerItems = [
  { key: 'experimental', label: '实验特性', icon: FlaskConical },
  { key: 'update', label: '更新应用', icon: RefreshCw }
]

const sectionMeta: Record<string, { label: string; description: string }> = {
  preferences: { label: '偏好设置', description: '自定义应用行为和外观。' },
  profile: { label: '个人资料', description: '头像、邮箱与订阅档位信息。' },
  system: { label: '系统设置', description: '管理系统级别的配置。' },
  shortcuts: { label: '快捷键', description: '查看和自定义键盘快捷键。' },
  privacy: { label: '隐私', description: '管理数据收集与隐私偏好。' },
  connectors: { label: '连接器', description: '管理外部服务连接。' },
  mcp: { label: 'MCP 服务', description: '配置 MCP 服务器连接。' },
  sandbox: { label: '虚拟机沙盒', description: '配置安全沙盒环境。' },
  experimental: { label: '实验特性', description: '启用或关闭实验性功能。' },
  update: { label: '更新应用', description: '检查并安装应用更新。' },
  upgrade: { label: '升级方案', description: '查看套餐与定价信息。' }
}

const activeSectionLabel = computed(() => sectionMeta[activeSection.value]?.label ?? '')
const activeSectionDescription = computed(() => sectionMeta[activeSection.value]?.description ?? '')
</script>
