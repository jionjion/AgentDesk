<template>
  <div class="space-y-6">
    <!-- 品牌卡片 -->
    <div class="rounded-lg border bg-card p-6">
      <div class="flex items-center gap-4">
        <img src="@/assets/icon_255.png" alt="AgentDesk" class="w-16 h-16 rounded-xl shadow-sm"/>
        <div class="flex-1 min-w-0">
          <h2 class="text-xl font-bold tracking-tight">AgentDesk</h2>
          <p class="text-sm text-muted-foreground mt-0.5">智能体本地工作台</p>
        </div>
        <Badge variant="secondary" class="shrink-0">
          v{{ appVersion || '...' }}
        </Badge>
      </div>
    </div>

    <!-- 相关链接 -->
    <div class="rounded-lg border bg-card p-4 space-y-4">
      <div class="flex items-center gap-2">
        <Link2 :size="16" class="text-muted-foreground"/>
        <span class="text-sm font-medium">相关链接</span>
      </div>
      <div class="grid grid-cols-2 gap-2">
        <button
            v-for="link in links"
            :key="link.url"
            class="flex items-start gap-3 p-3 rounded-md text-left hover:bg-accent transition-colors"
            @click="openExternal(link.url)"
        >
          <component :is="link.icon" :size="18" class="mt-0.5 shrink-0 text-primary"/>
          <div class="min-w-0">
            <p class="text-sm font-medium truncate">{{ link.label }}</p>
            <p class="text-xs text-muted-foreground truncate">{{ link.description }}</p>
          </div>
        </button>
      </div>
    </div>

    <!-- 版权信息 -->
    <Separator/>
    <div class="text-center space-y-1">
      <p class="text-xs text-muted-foreground">Apache-2.0 License</p>
      <p class="text-xs text-muted-foreground">Copyright © 2024-2026 Jion Jion</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {Badge} from '@/components/ui/badge'
import {Separator} from '@/components/ui/separator'
import {BookOpen, FileText, Github, Link2, User} from 'lucide-vue-next'

const appVersion = ref('')

const links = [
  {label: '帮助文档', description: '使用指南与 API 文档', icon: BookOpen, url: 'https://jionjion.github.io/AgentDesk'},
  {label: '更新日志', description: '版本变更记录', icon: FileText, url: 'https://github.com/jionjion/AgentDesk/blob/main/CHANGELOG.md'},
  {label: 'GitHub 仓库', description: '源代码与问题反馈', icon: Github, url: 'https://github.com/jionjion/AgentDesk'},
  {label: '关于作者', description: 'Jion Jion', icon: User, url: 'https://github.com/jionjion'}
]

function openExternal(url: string) {
  window.electronAPI?.shell.openExternal(url)
}

onMounted(async () => {
  const version = await window.electronAPI?.app.getVersion()
  if (version) appVersion.value = version
})
</script>
