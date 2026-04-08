<template>
  <div class="h-full flex flex-col">
    <!-- 顶部工具栏 -->
    <div class="flex items-center justify-end px-6 py-4 border-b border-gray-100 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <Button variant="ghost" size="icon">
          <RefreshCw :size="16" />
        </Button>
        <Button variant="outline">
          <Link :size="16" class="mr-1" />
          通过搭子创建
        </Button>
        <Button>
          <Plus :size="16" class="mr-1" />
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
          <Tabs default-value="my-tasks">
            <TabsList>
              <TabsTrigger value="my-tasks">我的定时任务</TabsTrigger>
              <TabsTrigger value="history">执行记录</TabsTrigger>
            </TabsList>
          </Tabs>
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="ghost" size="sm" class="text-gray-500 dark:text-gray-400">
                <ArrowUpDown :size="14" class="mr-1" />
                按创建时间倒序
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem>按创建时间倒序</DropdownMenuItem>
              <DropdownMenuItem>按创建时间正序</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <!-- 提示条 -->
        <Alert class="mb-6">
          <AlertDescription>
            <div class="flex items-center justify-between w-full">
              <span class="text-sm">定时任务仅在电脑保持唤醒时运行</span>
              <div class="flex items-center gap-2">
                <span class="text-sm text-gray-500 dark:text-gray-400">保持系统唤醒</span>
                <Switch />
              </div>
            </div>
          </AlertDescription>
        </Alert>

        <!-- 任务卡片网格 / 空状态 -->
        <EmptyState
          v-if="tasks.length === 0"
          :icon="CalendarClock"
          title="还没有定时任务"
          description="创建定时任务，让搭子按时为你工作"
          action-label="新建定时任务"
          :action-icon="Plus"
          @action="() => {}"
        />
        <div v-else class="grid grid-cols-2 gap-4">
          <div
            v-for="task in tasks"
            :key="task.title"
            class="border border-gray-200 dark:border-gray-700 rounded-xl p-5 hover:shadow-sm transition-shadow"
          >
            <div class="flex items-center justify-between mb-3">
              <Switch :checked="task.enabled" />
              <Button variant="ghost" size="icon" class="h-8 w-8">
                <MoreHorizontal :size="16" />
              </Button>
            </div>
            <h3 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-2">{{ task.title }}</h3>
            <p class="text-xs text-gray-400 dark:text-gray-500 line-clamp-2 mb-3">{{ task.description }}</p>
            <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
              <Clock :size="14" />
              <span>{{ task.schedule }}</span>
            </div>
          </div>
        </div>
      </div>
    </ScrollArea>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RefreshCw, Link, Plus, ArrowUpDown, MoreHorizontal, Clock, CalendarClock } from 'lucide-vue-next'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from '@/components/ui/dropdown-menu'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Switch } from '@/components/ui/switch'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'

const mockScheduledTasks = [
  {
    title: '每周竞品动态追踪',
    description: '请帮我追踪以下竞品的最新动态：- Cursor - Windsurf - GitHub Copilot 追踪内容：1. 上周是否有新版本发布或功能更新（检查官...',
    schedule: '每周一 10:00',
    enabled: false
  },
  {
    title: '每日数据报表更新',
    description: '请帮我处理每日数据更新：1. 读取工作目录中最新的 Excel/CSV 数据文件 2. 与前一天的数据对比，计算关键指标的日环比变化 3. 生...',
    schedule: '每天 09:30',
    enabled: false
  },
  {
    title: '每日下载文件夹清理',
    description: '请帮我整理「下载」文件夹：1. 扫描 ~/Downloads 目录中今天新增的文件 2. 按以下规则归档：- 图片文件（jpg/png/gif/svg）→...',
    schedule: '每天 18:30',
    enabled: false
  },
  {
    title: '午间充电站',
    description: '午休时间到了！帮我放松一下：请从以下内容中随机挑 2-3 个给我看：1. 一个近期有趣的开源项目（简短介绍它做什么、为什么有...',
    schedule: '工作日 12:30',
    enabled: false
  }
]

const tasks = ref(mockScheduledTasks)
</script>
