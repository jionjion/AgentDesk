<template>
  <div class="h-full flex flex-col">
    <!-- 顶部工具栏 -->
    <div class="flex items-center justify-end px-6 py-4 border-b border-gray-100">
      <div class="flex items-center gap-2">
        <el-button text circle size="default">
          <el-icon><Refresh /></el-icon>
        </el-button>
        <el-button size="default">
          <el-icon class="mr-1"><Connection /></el-icon>
          通过 QoderWork 创建
        </el-button>
        <el-button type="primary" size="default">
          <el-icon class="mr-1"><Plus /></el-icon>
          新建定时任务
        </el-button>
      </div>
    </div>

    <!-- 内容区 -->
    <el-scrollbar>
      <div class="px-6 py-6 max-w-5xl">
        <h1 class="text-2xl font-bold text-gray-900 mb-2">定时任务</h1>
        <p class="text-sm text-gray-500 mb-6">
          按计划自动执行任务，也可随时手动触发。在任意对话中描述你想定期做的事，即可快速创建
        </p>

        <!-- 标签页 + 排序 -->
        <div class="flex items-center justify-between mb-4">
          <el-tabs model-value="my-tasks">
            <el-tab-pane label="我的定时任务" name="my-tasks" />
            <el-tab-pane label="执行记录" name="history" />
          </el-tabs>
          <el-dropdown trigger="click">
            <el-button text size="small" class="text-gray-500">
              <el-icon class="mr-1"><Sort /></el-icon>
              按创建时间倒序
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>按创建时间倒序</el-dropdown-item>
                <el-dropdown-item>按创建时间正序</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 提示条 -->
        <el-alert
          type="info"
          :closable="false"
          class="mb-6"
        >
          <template #default>
            <div class="flex items-center justify-between w-full">
              <span class="text-sm">定时任务仅在电脑保持唤醒时运行</span>
              <div class="flex items-center gap-2">
                <span class="text-sm text-gray-500">保持系统唤醒</span>
                <el-switch size="small" />
              </div>
            </div>
          </template>
        </el-alert>

        <!-- 任务卡片网格 -->
        <div class="grid grid-cols-2 gap-4">
          <div
            v-for="task in mockScheduledTasks"
            :key="task.title"
            class="border border-gray-200 rounded-xl p-5 hover:shadow-sm transition-shadow"
          >
            <div class="flex items-center justify-between mb-3">
              <el-switch size="small" :model-value="task.enabled" />
              <el-button text circle size="small">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
            </div>
            <h3 class="text-sm font-semibold text-gray-800 mb-2">{{ task.title }}</h3>
            <p class="text-xs text-gray-400 line-clamp-2 mb-3">{{ task.description }}</p>
            <div class="flex items-center gap-1 text-xs text-gray-500">
              <el-icon :size="14"><Clock /></el-icon>
              <span>{{ task.schedule }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { Refresh, Connection, Plus, Sort, MoreFilled, Clock } from '@element-plus/icons-vue'

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
</script>
