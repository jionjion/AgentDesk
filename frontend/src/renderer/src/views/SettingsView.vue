<template>
  <div class="h-screen w-screen flex flex-col overflow-hidden bg-white dark:bg-gray-900">
    <TitleBar/>
    <div class="flex flex-1 overflow-hidden">
      <!-- 左侧导航 -->
      <aside class="w-72 border-r border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-900 flex flex-col h-full">
        <!-- 导航分组 -->
        <nav class="flex-1 px-3 py-4 space-y-4 overflow-y-auto">
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
                <component :is="item.icon" :size="16"/>
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
                <component :is="item.icon" :size="16"/>
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
                <component :is="item.icon" :size="16"/>
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
                <component :is="item.icon" :size="16"/>
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
                <component :is="item.icon" :size="16"/>
                <span>{{ item.label }}</span>
              </button>
            </div>
          </div>

          <!-- 关于 -->
          <div>
            <div class="px-2 mb-1 text-xs text-gray-400 dark:text-gray-500 font-medium">关于</div>
            <div class="space-y-0.5">
              <button
                  v-for="item in aboutItems"
                  :key="item.key"
                  class="flex items-center gap-2 w-full px-2 py-1.5 text-sm rounded-md transition-colors"
                  :class="activeSection === item.key
                ? 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 font-medium'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-gray-100'"
                  @click="activeSection = item.key"
              >
                <component :is="item.icon" :size="16"/>
                <span>{{ item.label }}</span>
              </button>
            </div>
          </div>
        </nav>

        <!-- 底部区域 -->
        <div class="px-3 py-3 border-t border-gray-200 dark:border-gray-700 flex items-center">
          <a
              href="https://jionjion.github.io/AgentDesk/"
              target="_blank"
              class="flex items-center gap-2 px-2 py-1.5 text-sm text-gray-600 dark:text-gray-400 rounded-md hover:bg-gray-100 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-gray-100 transition-colors"
          >
            <Github :size="16"/>
            <span>GitHub</span>
          </a>
          <div class="ml-auto">
            <Button variant="ghost" size="icon" class="h-8 w-8" @click="router.back()">
              <Home :size="16" class="text-gray-400"/>
            </Button>
          </div>
        </div>
      </aside>

      <!-- 右侧内容区 -->
      <main class="flex-1 overflow-y-auto">
        <div class="max-w-2xl mx-auto px-8 py-8">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-1">{{ activeSectionLabel }}</h1>
          <p class="text-sm text-gray-500 dark:text-gray-400 mb-6">{{ activeSectionDescription }}</p>

          <!-- 动态内容区 -->
          <ProfileSection v-if="activeSection === 'profile'"/>
          <AppSection v-else-if="activeSection === 'preferences'"/>
          <PrivacySection v-else-if="activeSection === 'privacy'"/>
          <MemorySection v-else-if="activeSection === 'memory'"/>
          <KnowledgeSection v-else-if="activeSection === 'knowledge'"/>
          <ObsidianSection v-else-if="activeSection === 'obsidian'"/>
          <McpSection v-else-if="activeSection === 'mcp'"/>
          <UpdateSection v-else-if="activeSection === 'update'"/>
          <AboutSection v-else-if="activeSection === 'about'"/>
          <div v-else class="rounded-lg border border-dashed border-gray-300 dark:border-gray-600 p-12 text-center">
            <div class="text-gray-400 dark:text-gray-500 text-sm">开发中...</div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import TitleBar from '@/components/TitleBar.vue'
import ProfileSection from '@/components/settings/ProfileSection.vue'
import AppSection from '@/components/settings/AppSection.vue'
import PrivacySection from '@/components/settings/PrivacySection.vue'
import MemorySection from '@/components/settings/MemorySection.vue'
import ObsidianSection from '@/components/settings/ObsidianSection.vue'
import McpSection from '@/components/settings/McpSection.vue'
import KnowledgeSection from '@/components/settings/KnowledgeSection.vue'
import UpdateSection from '@/components/settings/UpdateSection.vue'
import AboutSection from '@/components/settings/AboutSection.vue'
import {Button} from '@/components/ui/button'
import {useSettingsStore} from '@/stores/settings'
import {BookOpen, Box, Brain, Cpu, Database, FlaskConical, Github, Home, Info, Link, RefreshCw, Shield, SlidersHorizontal, User} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const settingsStore = useSettingsStore()
const activeSection = ref('preferences')

onMounted(() => {
  if (!settingsStore.loaded) {
    settingsStore.fetchSettings()
  }
  const tab = route.query.tab as string
  if (tab) {
    activeSection.value = tab
  }
})

const generalItems = [
  {key: 'preferences', label: '偏好设置', icon: SlidersHorizontal},
  {key: 'profile', label: '账号设置', icon: User}
]

const privacyItems = [
  {key: 'privacy', label: '隐私', icon: Shield}
]

const extensionItems = [
  {key: 'connectors', label: '连接器', icon: Link},
  {key: 'mcp', label: 'MCP 服务', icon: Cpu},
  {key: 'obsidian', label: 'Obsidian 笔记', icon: BookOpen}
]

const advancedItems = [
  {key: 'knowledge', label: '知识库检索', icon: Database},
  {key: 'memory', label: '长期记忆', icon: Brain},
  {key: 'sandbox', label: '虚拟机沙盒', icon: Box}
]

const developerItems = [
  {key: 'experimental', label: '实验特性', icon: FlaskConical}
]

const aboutItems = [
  {key: 'update', label: '更新应用', icon: RefreshCw},
  {key: 'about', label: '关于应用', icon: Info}
]

const sectionMeta: Record<string, { label: string; description: string }> = {
  preferences: {label: '偏好设置', description: '自定义应用行为和外观。'},
  profile: {label: '账号设置', description: '头像、昵称、密码与退出登录。'},
  privacy: {label: '隐私', description: '管理数据收集与隐私偏好。'},
  connectors: {label: '连接器', description: '管理外部服务连接。'},
  mcp: {label: 'MCP 服务', description: '配置 MCP 服务器连接。'},
  obsidian: {label: 'Obsidian 笔记', description: '配置知识沉淀到本地 Obsidian 笔记库。'},
  knowledge: {label: '知识库检索', description: '配置 RAG 知识库自动检索与参数调优。'},
  memory: {label: '长期记忆', description: '管理 AI 助手的跨会话记忆。'},
  sandbox: {label: '虚拟机沙盒', description: '配置安全沙盒环境。'},
  experimental: {label: '实验特性', description: '启用或关闭实验性功能。'},
  update: {label: '更新应用', description: '检查并安装应用更新。'},
  about: {label: '关于应用', description: '版本信息与相关链接。'}
}

const activeSectionLabel = computed(() => sectionMeta[activeSection.value]?.label ?? '')
const activeSectionDescription = computed(() => sectionMeta[activeSection.value]?.description ?? '')
</script>
