<template>
  <div class="h-full flex flex-col">
    <!-- 主内容区 - 欢迎页 -->
    <div class="flex-1 flex flex-col items-center justify-center px-8">
      <!-- Logo + 标语 -->
      <div class="w-16 h-16 rounded-2xl bg-green-50 flex items-center justify-center mb-6">
        <span class="text-3xl">🤖</span>
      </div>
      <h1 class="text-2xl font-bold text-gray-900 mb-2">不止聊天，搞定一切</h1>
      <p class="text-sm text-gray-500 mb-10">本地运行、自主规划、安全可控的 AI 工作搭子</p>

      <!-- 功能卡片 -->
      <div class="flex gap-4 mb-16">
        <div
          v-for="card in featureCards"
          :key="card.title"
          class="w-52 p-5 border border-gray-200 rounded-xl hover:shadow-sm transition-shadow cursor-pointer"
        >
          <div class="w-10 h-10 rounded-lg bg-gray-50 flex items-center justify-center mb-3">
            <el-icon :size="22" class="text-gray-600"><component :is="card.icon" /></el-icon>
          </div>
          <h3 class="text-sm font-medium text-gray-800 mb-1">{{ card.title }}</h3>
          <p class="text-xs text-gray-400">{{ card.description }}</p>
        </div>
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="px-8 pb-6">
      <div class="max-w-2xl mx-auto border border-gray-200 rounded-xl p-4">
        <el-input
          type="textarea"
          :rows="2"
          placeholder="描述任务，/ 调用技能与工具，标准模式经济高效"
          resize="none"
          class="border-none!"
        />
        <div class="flex items-center justify-between mt-3">
          <div class="flex items-center gap-2">
            <el-button text size="small">
              <el-icon class="mr-1"><FolderOpened /></el-icon>
              <span class="text-xs text-gray-500">选择工作目录</span>
            </el-button>
            <el-button text size="small" circle>
              <el-icon :size="16"><Brush /></el-icon>
            </el-button>
            <el-button text size="small" circle>
              <el-icon :size="16"><Paperclip /></el-icon>
            </el-button>
          </div>
          <div class="flex items-center gap-2">
            <el-tag size="small" effect="plain" class="cursor-pointer">
              ⚡ 标准
              <el-icon :size="10" class="ml-1"><ArrowDown /></el-icon>
            </el-tag>
            <el-button type="primary" circle size="small">
              <el-icon><Top /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  FolderOpened,
  Brush,
  Paperclip,
  ArrowDown,
  Top,
  Document,
  Camera,
  DataLine
} from '@element-plus/icons-vue'

const featureCards = [
  { title: '文件整理', description: '智能整理和管理本地文件', icon: Document },
  { title: '内容创作', description: '创作演示文稿、文档和多媒体内容', icon: Camera },
  { title: '文档处理', description: '处理和分析文档数据', icon: DataLine }
]
</script>
