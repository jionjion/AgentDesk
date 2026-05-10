<template>
  <div class="space-y-6">
    <!-- 主题 -->
    <div class="space-y-2">
      <Label>主题</Label>
      <Select v-model="form.theme">
        <SelectTrigger>
          <SelectValue placeholder="选择主题"/>
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="opt in themeOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </SelectItem>
        </SelectContent>
      </Select>
    </div>

    <!-- 关闭窗口行为 -->
    <div class="space-y-2">
      <Label>关闭窗口时</Label>
      <RadioGroup v-model="closeAction" class="space-y-1">
        <div class="flex items-center gap-2">
          <RadioGroupItem id="close-ask" value="ask"/>
          <Label for="close-ask" class="font-normal cursor-pointer">每次询问</Label>
        </div>
        <div class="flex items-center gap-2">
          <RadioGroupItem id="close-minimize" value="minimize"/>
          <Label for="close-minimize" class="font-normal cursor-pointer">最小化到系统托盘</Label>
        </div>
        <div class="flex items-center gap-2">
          <RadioGroupItem id="close-quit" value="quit"/>
          <Label for="close-quit" class="font-normal cursor-pointer">直接退出</Label>
        </div>
      </RadioGroup>
    </div>

    <!-- 开机自启 -->
    <div class="flex items-center justify-between">
      <Label for="auto-launch" class="cursor-pointer">开机自动启动</Label>
      <Switch id="auto-launch" :model-value="autoLaunch" @update:model-value="onAutoLaunchChange"/>
    </div>

    <!-- 发送键 -->
    <div class="space-y-2">
      <Label>发送快捷键</Label>
      <RadioGroup v-model="form.sendKey" class="flex gap-4">
        <div class="flex items-center gap-2">
          <RadioGroupItem id="send-enter" value="Enter"/>
          <Label for="send-enter" class="font-normal cursor-pointer">Enter</Label>
        </div>
        <div class="flex items-center gap-2">
          <RadioGroupItem id="send-ctrl-enter" value="Ctrl+Enter"/>
          <Label for="send-ctrl-enter" class="font-normal cursor-pointer">Ctrl+Enter</Label>
        </div>
      </RadioGroup>
    </div>

    <!-- 版本信息 -->
    <div v-if="appVersion" class="pt-4 border-t text-xs text-gray-400 dark:text-gray-500">
      当前版本: v{{ appVersion }}
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref, watch} from 'vue'
import {useSettingsStore} from '@/stores/settings'
import type {AppSettings} from '@/types/settings'
import {Label} from '@/components/ui/label'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {RadioGroup, RadioGroupItem} from '@/components/ui/radio-group'
import {Switch} from '@/components/ui/switch'

const settingsStore = useSettingsStore()

const form = reactive<AppSettings>({
  theme: 'auto',
  language: 'zh-CN',
  sendKey: 'Enter',
  fontSize: 14
})
const closeAction = ref<'ask' | 'minimize' | 'quit'>('ask')
const autoLaunch = ref(false)
const appVersion = ref('')

const themeOptions = [
  {label: '跟随系统', value: 'auto'},
  {label: '浅色', value: 'light'},
  {label: '深色', value: 'dark'}
]

watch(() => settingsStore.app, (val) => {
  Object.assign(form, val)
}, {immediate: true})

// 关闭行为独立于后端设置，通过 Electron IPC 读写本地配置
watch(closeAction, async (val) => {
  await window.electronAPI?.app.setCloseAction(val)
})

// 主题、发送键等修改后自动保存到后端
let initialized = false
watch(() => ({...form}), async () => {
  if (!initialized) return
  await settingsStore.saveAppSettings(form)
}, {deep: true})

onMounted(async () => {
  const action = await window.electronAPI?.app.getCloseAction()
  if (action) closeAction.value = action

  const launched = await window.electronAPI?.app.getAutoLaunch()
  if (launched !== undefined) autoLaunch.value = launched

  const version = await window.electronAPI?.app.getVersion()
  if (version) appVersion.value = version

  initialized = true
})

async function onAutoLaunchChange(val: boolean) {
  autoLaunch.value = val
  await window.electronAPI?.app.setAutoLaunch(val)
}
</script>
