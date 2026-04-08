<template>
  <div
    class="title-bar flex items-center justify-between h-10 px-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 select-none"
    style="-webkit-app-region: drag"
  >
    <!-- 左侧操作区 -->
    <div class="flex items-center gap-2" style="-webkit-app-region: no-drag">
      <Button variant="ghost" size="icon" class="h-8 w-8" @click="emit('toggleMenu')">
        <Menu :size="18" />
      </Button>
      <Button variant="ghost" size="icon" class="h-8 w-8" @click="appStore.toggleSidebar()">
        <ChevronsLeft v-if="!appStore.sidebarCollapsed" :size="16" />
        <ChevronsRight v-else :size="16" />
      </Button>
    </div>

    <!-- 右侧操作区 -->
    <div class="flex items-center gap-2" style="-webkit-app-region: no-drag">
      <Popover>
        <PopoverTrigger as-child>
          <Button variant="ghost" size="icon" class="h-8 w-8">
            <CircleHelp :size="16" />
          </Button>
        </PopoverTrigger>
        <PopoverContent class="w-auto p-3 text-xs text-gray-600 dark:text-gray-400">
          <div class="space-y-1">
            <div>版本: v{{ appVersion }}</div>
            <div>构建日期: {{ buildDate }}</div>
          </div>
        </PopoverContent>
      </Popover>

      <!-- 窗口控制按钮 -->
      <div class="flex items-center ml-2">
        <button
          class="w-8 h-8 flex items-center justify-center hover:bg-gray-100 dark:hover:bg-gray-800 rounded text-gray-500 dark:text-gray-400"
          @click="handleMinimize"
        >
          <Minus :size="14" />
        </button>
        <button
          class="w-8 h-8 flex items-center justify-center hover:bg-gray-100 dark:hover:bg-gray-800 rounded text-gray-500 dark:text-gray-400"
          @click="handleMaximize"
        >
          <Maximize2 :size="14" />
        </button>
        <button
          class="w-8 h-8 flex items-center justify-center hover:bg-red-50 dark:hover:bg-red-900/30 hover:text-red-500 dark:hover:text-red-400 rounded text-gray-500 dark:text-gray-400"
          @click="handleClose"
        >
          <X :size="14" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import {
  Menu,
  ChevronsLeft,
  ChevronsRight,
  CircleHelp,
  Minus,
  Maximize2,
  X
} from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'

declare const __APP_VERSION__: string
declare const __BUILD_DATE__: string

const appVersion = __APP_VERSION__
const buildDate = __BUILD_DATE__

const appStore = useAppStore()
const emit = defineEmits<{ toggleMenu: [] }>()

function handleMinimize() {
  window.electronAPI?.window.minimize()
}

function handleMaximize() {
  window.electronAPI?.window.maximize()
}

function handleClose() {
  window.electronAPI?.window.close()
}
</script>
