<template>
  <div class="h-full flex flex-col">
    <!-- 顶部工具栏 -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-gray-100">
      <div class="flex items-center gap-3">
        <div class="relative w-60">
          <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" :size="16" />
          <Input placeholder="搜索技能" class="pl-8" />
        </div>
      </div>
      <div class="flex items-center gap-2">
        <Button variant="outline">
          <Link :size="16" class="mr-1" />
          通过搭子创建
        </Button>
        <Button>
          <Download :size="16" class="mr-1" />
          安装技能
        </Button>
      </div>
    </div>

    <!-- 内容区 -->
    <ScrollArea class="flex-1">
      <div class="px-6 py-6 max-w-5xl">
        <h1 class="text-2xl font-bold text-gray-900 mb-2">技能</h1>
        <p class="text-sm text-gray-500 mb-6">安装与管理技能，在对话中扩展搭子的能力。</p>

        <!-- 横幅 -->
        <div class="bg-amber-50 rounded-xl p-6 mb-6">
          <h2 class="text-lg font-semibold text-gray-800 mb-1">为你精选的职场技能</h2>
          <p class="text-sm text-gray-500">涵盖写作、效率、设计、数据分析等多种特色，一键安装。</p>
        </div>

        <!-- 标签页 -->
        <Tabs default-value="marketplace" class="mb-4">
          <TabsList>
            <TabsTrigger value="marketplace">技能广场</TabsTrigger>
            <TabsTrigger value="builtin">内置</TabsTrigger>
            <TabsTrigger value="installed">
              用户安装 <Badge variant="secondary" class="ml-1.5">1</Badge>
            </TabsTrigger>
          </TabsList>
        </Tabs>

        <div class="mb-4">
          <span class="text-sm font-medium text-gray-800">官方精选</span>
          <span class="text-xs text-gray-400 ml-4 cursor-pointer hover:text-gray-600">全部</span>
        </div>

        <!-- 技能卡片网格 -->
        <div class="grid grid-cols-3 gap-4">
          <div
            v-for="skill in mockSkills"
            :key="skill.name"
            class="border border-gray-200 rounded-xl p-4 hover:shadow-sm transition-shadow cursor-pointer"
          >
            <div class="flex items-start gap-3 mb-2">
              <div
                class="w-10 h-10 rounded-lg flex items-center justify-center text-lg shrink-0"
                :style="{ backgroundColor: skill.bgColor }"
              >
                {{ skill.icon }}
              </div>
              <div class="min-w-0">
                <h3 class="text-sm font-medium text-gray-800 truncate">{{ skill.name }}</h3>
                <p class="text-xs text-gray-400 mt-1 line-clamp-2">{{ skill.description }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </ScrollArea>
  </div>
</template>

<script setup lang="ts">
import { Search, Link, Download } from 'lucide-vue-next'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'

const mockSkills = [
  { name: '深度研究', icon: '🔍', bgColor: '#EEF2FF', description: '综合多来源搜索引擎和深度浏览器助手' },
  { name: '技术图表生成', icon: '📊', bgColor: '#F0FDF4', description: '帮你编写名立意图、流程图和时序图' },
  { name: 'UI 设计师', icon: '🎨', bgColor: '#FFF7ED', description: 'Web UI 设计和原型专家' },
  { name: '搭子演示文稿', icon: '📑', bgColor: '#FDF2F8', description: '生成搭子风格演示文稿' },
  { name: 'Notion 信息助手', icon: '📝', bgColor: '#ECFDF5', description: '解析整合文本数据生成 Notion 风格内容' },
  { name: '定制周历生成器', icon: '📅', bgColor: '#EFF6FF', description: '帮助生成定制化的周历' }
]
</script>
