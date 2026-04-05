<template>
  <div class="h-full flex flex-col">
    <!-- 内容区 -->
    <ScrollArea class="flex-1">
      <div class="px-6 py-10 max-w-3xl mx-auto">
        <!-- 插图区域 -->
        <div class="flex justify-center mb-8">
          <div class="flex items-end gap-4">
            <div class="bg-green-100 text-green-700 px-4 py-2 rounded-xl text-sm">
              Sure. Cleaning duplicates now.
            </div>
            <div class="w-16 h-16 rounded-full bg-green-50 flex items-center justify-center text-3xl">
              🤖
            </div>
            <div class="bg-gray-100 text-gray-700 px-4 py-2 rounded-xl text-sm">
              Organize my desktop files please
            </div>
          </div>
        </div>

        <!-- 标题 -->
        <div class="text-center mb-8">
          <h1 class="text-2xl font-bold text-gray-900 mb-3">IM 频道</h1>
          <p class="text-sm text-gray-500">
            配置 IM 频道，让搭子接收来自钉钉、飞书等平台的消息。
          </p>
          <p class="text-sm text-gray-500">
            频道配置信息仅存储在本地，不会上传到云端。
          </p>
        </div>

        <!-- 频道列表 -->
        <div class="space-y-4">
          <div
            v-for="channel in channels"
            :key="channel.name"
            class="flex items-center justify-between p-5 border border-gray-200 rounded-xl hover:shadow-sm transition-shadow"
          >
            <div class="flex items-center gap-4">
              <div
                class="w-10 h-10 rounded-full flex items-center justify-center text-xl"
                :style="{ backgroundColor: channel.bgColor }"
              >
                {{ channel.icon }}
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <span class="text-sm font-medium text-gray-800">{{ channel.name }}</span>
                  <Badge v-if="channel.connected" variant="success">已连接</Badge>
                </div>
                <p class="text-xs text-gray-400 mt-0.5">{{ channel.description }}</p>
              </div>
            </div>
            <div class="flex items-center gap-3">
              <template v-if="channel.connected">
                <span class="text-sm text-blue-500 cursor-pointer hover:underline">配对管理</span>
                <Button variant="ghost" size="icon" class="h-8 w-8">
                  <MoreHorizontal :size="16" />
                </Button>
                <Switch :checked="true" />
              </template>
              <Button v-else variant="outline">配置</Button>
            </div>
          </div>
        </div>
      </div>
    </ScrollArea>
  </div>
</template>

<script setup lang="ts">
import { MoreHorizontal } from 'lucide-vue-next'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'

const channels = [
  {
    name: '钉钉',
    icon: '💬',
    bgColor: '#EFF6FF',
    description: '通过钉钉机器人接收并回复用户消息',
    connected: false
  },
  {
    name: '飞书',
    icon: '🐦',
    bgColor: '#ECFDF5',
    description: '通过飞书机器人接收并回复用户消息',
    connected: true
  },
  {
    name: '微信',
    icon: '💚',
    bgColor: '#F0FDF4',
    description: '通过微信机器人接收并回复用户消息',
    connected: false
  }
]
</script>
