<template>
  <div
    class="title-bar flex items-center justify-between h-10 px-3 border-b border-gray-200 bg-white select-none"
    style="-webkit-app-region: drag"
  >
    <!-- 左侧操作区 -->
    <div class="flex items-center gap-2" style="-webkit-app-region: no-drag">
      <el-button text size="small" @click="emit('toggleMenu')">
        <el-icon :size="18"><Menu /></el-icon>
      </el-button>
      <el-button text size="small" @click="appStore.toggleSidebar()">
        <el-icon :size="16"><DArrowLeft v-if="!appStore.sidebarCollapsed" /><DArrowRight v-else /></el-icon>
      </el-button>
    </div>

    <!-- 右侧操作区 -->
    <div class="flex items-center gap-2" style="-webkit-app-region: no-drag">
      <el-button text size="small" class="text-gray-500">
        <el-icon :size="14" class="mr-1"><ChatDotSquare /></el-icon>
        <span class="text-xs">问题反馈</span>
      </el-button>
      <el-button text size="small" circle>
        <el-icon :size="16"><QuestionFilled /></el-icon>
      </el-button>

      <!-- 窗口控制按钮 -->
      <div class="flex items-center ml-2">
        <button
          class="w-8 h-8 flex items-center justify-center hover:bg-gray-100 rounded text-gray-500"
          @click="handleMinimize"
        >
          <el-icon :size="14"><Minus /></el-icon>
        </button>
        <button
          class="w-8 h-8 flex items-center justify-center hover:bg-gray-100 rounded text-gray-500"
          @click="handleMaximize"
        >
          <el-icon :size="14"><FullScreen /></el-icon>
        </button>
        <button
          class="w-8 h-8 flex items-center justify-center hover:bg-red-50 hover:text-red-500 rounded text-gray-500"
          @click="handleClose"
        >
          <el-icon :size="14"><Close /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import {
  Menu,
  DArrowLeft,
  DArrowRight,
  ChatDotSquare,
  QuestionFilled,
  Minus,
  FullScreen,
  Close
} from '@element-plus/icons-vue'

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
