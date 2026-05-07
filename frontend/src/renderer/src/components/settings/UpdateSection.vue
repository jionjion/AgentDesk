<template>
  <div class="space-y-6">
    <!-- 版本信息卡片 -->
    <div class="rounded-lg border bg-card p-4 space-y-4">
      <div class="flex items-center justify-between">
        <div class="space-y-0.5">
          <p class="text-sm text-muted-foreground">当前版本</p>
          <p class="text-lg font-semibold tracking-tight">v{{ currentVersion || '...' }}</p>
        </div>
        <Button
          v-if="status === 'idle' || status === 'up-to-date' || status === 'error'"
          variant="outline"
          size="sm"
          @click="checkForUpdate"
        >
          <RefreshCw class="h-3.5 w-3.5 mr-1.5"/>
          检查更新
        </Button>
      </div>

      <!-- 检查中 -->
      <div v-if="status === 'checking'" class="flex items-center gap-2 text-sm text-muted-foreground pt-2 border-t">
        <RefreshCw class="h-4 w-4 animate-spin"/>
        <span>正在检查更新...</span>
      </div>

      <!-- 已是最新 -->
      <div v-else-if="status === 'up-to-date'" class="flex items-center gap-2 text-sm pt-2 border-t">
        <CheckCircle class="h-4 w-4 text-green-500"/>
        <span class="text-muted-foreground">已是最新版本</span>
      </div>

      <!-- 错误 -->
      <div v-else-if="status === 'error'" class="pt-2 border-t space-y-1">
        <div class="flex items-center gap-2 text-sm text-destructive">
          <AlertCircle class="h-4 w-4"/>
          <span>检查更新失败</span>
        </div>
        <p class="text-xs text-muted-foreground pl-6">{{ errorMessage }}</p>
      </div>
    </div>

    <!-- 新版本可用 -->
    <div v-if="status === 'available'" class="rounded-lg border border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-950/30 p-4 space-y-3">
      <div class="flex items-center gap-2">
        <ArrowUpCircle class="h-5 w-5 text-green-600 dark:text-green-400"/>
        <span class="text-sm font-medium text-green-700 dark:text-green-300">发现新版本</span>
      </div>
      <div class="pl-7 space-y-1">
        <p class="text-base font-semibold">v{{ updateInfo.version }}</p>
        <p v-if="updateInfo.releaseDate" class="text-xs text-muted-foreground">
          发布于 {{ formatDate(updateInfo.releaseDate) }}
        </p>
      </div>
      <div class="pl-7">
        <Button size="sm" @click="downloadUpdate">
          <Download class="h-3.5 w-3.5 mr-1.5"/>
          下载更新
        </Button>
      </div>
    </div>

    <!-- 下载中 -->
    <div v-if="status === 'downloading'" class="rounded-lg border bg-card p-4 space-y-3">
      <div class="flex items-center justify-between text-sm">
        <div class="flex items-center gap-2 text-muted-foreground">
          <Download class="h-4 w-4 animate-bounce"/>
          <span>正在下载更新</span>
        </div>
        <span class="font-medium tabular-nums">{{ progress.percent }}%</span>
      </div>
      <div class="w-full bg-secondary rounded-full h-2 overflow-hidden">
        <div
          class="bg-primary h-full rounded-full transition-all duration-300 ease-out"
          :style="{ width: progress.percent + '%' }"
        />
      </div>
      <p class="text-xs text-muted-foreground text-right tabular-nums">
        {{ formatBytes(progress.transferred) }} / {{ formatBytes(progress.total) }}
      </p>
    </div>

    <!-- 下载完成 -->
    <div v-if="status === 'downloaded'" class="rounded-lg border border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-950/30 p-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <CheckCircle class="h-5 w-5 text-blue-600 dark:text-blue-400"/>
          <span class="text-sm font-medium text-blue-700 dark:text-blue-300">更新已就绪</span>
        </div>
        <Button size="sm" @click="installUpdate">
          <RefreshCw class="h-3.5 w-3.5 mr-1.5"/>
          重启安装
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted, reactive, ref} from 'vue'
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
