<template>
  <div class="space-y-6">
    <!-- 当前版本 -->
    <div class="space-y-1">
      <Label class="text-sm text-muted-foreground">当前版本</Label>
      <p class="text-base font-medium">v{{ currentVersion || '...' }}</p>
    </div>

    <!-- 状态信息 -->
    <div class="space-y-3">
      <!-- 检查中 -->
      <div v-if="status === 'checking'" class="flex items-center gap-2 text-sm text-muted-foreground">
        <RefreshCw class="h-4 w-4 animate-spin"/>
        <span>正在检查更新...</span>
      </div>

      <!-- 有新版本 -->
      <div v-else-if="status === 'available'" class="space-y-2">
        <div class="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
          <ArrowUpCircle class="h-4 w-4"/>
          <span>发现新版本: v{{ updateInfo.version }}</span>
        </div>
        <p v-if="updateInfo.releaseDate" class="text-xs text-muted-foreground">
          发布时间: {{ formatDate(updateInfo.releaseDate) }}
        </p>
      </div>

      <!-- 已是最新 -->
      <div v-else-if="status === 'up-to-date'" class="flex items-center gap-2 text-sm text-muted-foreground">
        <CheckCircle class="h-4 w-4 text-green-500"/>
        <span>已是最新版本</span>
      </div>

      <!-- 下载中 -->
      <div v-else-if="status === 'downloading'" class="space-y-2">
        <div class="flex items-center gap-2 text-sm text-muted-foreground">
          <Download class="h-4 w-4 animate-bounce"/>
          <span>正在下载更新... {{ progress.percent }}%</span>
        </div>
        <div class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
          <div
            class="bg-blue-500 h-2 rounded-full transition-all duration-300"
            :style="{ width: progress.percent + '%' }"
          />
        </div>
        <p class="text-xs text-muted-foreground">
          {{ formatBytes(progress.transferred) }} / {{ formatBytes(progress.total) }}
        </p>
      </div>

      <!-- 下载完成 -->
      <div v-else-if="status === 'downloaded'" class="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
        <CheckCircle class="h-4 w-4"/>
        <span>更新已下载，重启后生效</span>
      </div>

      <!-- 错误 -->
      <div v-else-if="status === 'error'" class="space-y-1">
        <div class="flex items-center gap-2 text-sm text-red-500">
          <AlertCircle class="h-4 w-4"/>
          <span>检查更新失败</span>
        </div>
        <p class="text-xs text-muted-foreground">{{ errorMessage }}</p>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="flex gap-3">
      <Button
        v-if="status === 'idle' || status === 'up-to-date' || status === 'error'"
        variant="outline"
        size="sm"
        :disabled="status === 'checking'"
        @click="checkForUpdate"
      >
        <RefreshCw class="h-4 w-4 mr-1"/>
        检查更新
      </Button>

      <Button
        v-if="status === 'available'"
        size="sm"
        @click="downloadUpdate"
      >
        <Download class="h-4 w-4 mr-1"/>
        下载更新
      </Button>

      <Button
        v-if="status === 'downloaded'"
        size="sm"
        @click="installUpdate"
      >
        <RefreshCw class="h-4 w-4 mr-1"/>
        立即重启安装
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted, reactive, ref} from 'vue'
import {Label} from '@/components/ui/label'
import {Button} from '@/components/ui/button'
import {RefreshCw, ArrowUpCircle, CheckCircle, Download, AlertCircle} from 'lucide-vue-next'

type UpdateStatus = 'idle' | 'checking' | 'available' | 'up-to-date' | 'downloading' | 'downloaded' | 'error'

const currentVersion = ref('')
const status = ref<UpdateStatus>('idle')
const errorMessage = ref('')
const updateInfo = reactive({ version: '', releaseDate: '', releaseNotes: '' })
const progress = reactive({ percent: 0, transferred: 0, total: 0 })

function formatDate(dateStr: string): string {
  try {
    return new Date(dateStr).toLocaleDateString('zh-CN')
  } catch {
    return dateStr
  }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function checkForUpdate() {
  status.value = 'checking'
  errorMessage.value = ''
  try {
    await window.electronAPI?.updater.check()
  } catch (e: unknown) {
    status.value = 'error'
    errorMessage.value = e instanceof Error ? e.message : '未知错误'
  }
}

async function downloadUpdate() {
  status.value = 'downloading'
  progress.percent = 0
  progress.transferred = 0
  progress.total = 0
  try {
    await window.electronAPI?.updater.download()
  } catch (e: unknown) {
    status.value = 'error'
    errorMessage.value = e instanceof Error ? e.message : '下载失败'
  }
}

function installUpdate() {
  window.electronAPI?.updater.install()
}

onMounted(async () => {
  const version = await window.electronAPI?.app.getVersion()
  if (version) currentVersion.value = version

  window.electronAPI?.updater.onChecking(() => {
    status.value = 'checking'
  })

  window.electronAPI?.updater.onAvailable((_event, info) => {
    status.value = 'available'
    updateInfo.version = info.version
    updateInfo.releaseDate = info.releaseDate
    updateInfo.releaseNotes = info.releaseNotes
  })

  window.electronAPI?.updater.onNotAvailable(() => {
    status.value = 'up-to-date'
  })

  window.electronAPI?.updater.onProgress((_event, prog) => {
    status.value = 'downloading'
    progress.percent = prog.percent
    progress.transferred = prog.transferred
    progress.total = prog.total
  })

  window.electronAPI?.updater.onDownloaded(() => {
    status.value = 'downloaded'
  })

  window.electronAPI?.updater.onError((_event, message) => {
    status.value = 'error'
    errorMessage.value = message
  })
})

onUnmounted(() => {
  window.electronAPI?.updater.removeAllListeners()
})
</script>
