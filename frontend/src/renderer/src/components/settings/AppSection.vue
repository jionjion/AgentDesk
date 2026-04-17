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
          <RadioGroupItem value="ask" id="close-ask"/>
          <Label for="close-ask" class="font-normal cursor-pointer">每次询问</Label>
        </div>
        <div class="flex items-center gap-2">
          <RadioGroupItem value="minimize" id="close-minimize"/>
          <Label for="close-minimize" class="font-normal cursor-pointer">最小化到系统托盘</Label>
        </div>
        <div class="flex items-center gap-2">
          <RadioGroupItem value="quit" id="close-quit"/>
          <Label for="close-quit" class="font-normal cursor-pointer">直接退出</Label>
        </div>
      </RadioGroup>
    </div>

    <!-- 发送键 -->
    <div class="space-y-2">
      <Label>发送快捷键</Label>
      <RadioGroup v-model="form.sendKey" class="flex gap-4">
        <div class="flex items-center gap-2">
          <RadioGroupItem value="Enter" id="send-enter"/>
          <Label for="send-enter" class="font-normal cursor-pointer">Enter</Label>
        </div>
        <div class="flex items-center gap-2">
          <RadioGroupItem value="Ctrl+Enter" id="send-ctrl-enter"/>
          <Label for="send-ctrl-enter" class="font-normal cursor-pointer">Ctrl+Enter</Label>
        </div>
      </RadioGroup>
    </div>

    <!-- 保存 -->
    <div class="flex justify-end">
      <Button :disabled="saving" @click="handleSave">
        {{ saving ? '保存中...' : '保存' }}
      </Button>
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
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {RadioGroup, RadioGroupItem} from '@/components/ui/radio-group'

const settingsStore = useSettingsStore()

const form = reactive<AppSettings>({
  theme: 'auto',
  language: 'zh-CN',
  sendKey: 'Enter',
  fontSize: 14
})
const saving = ref(false)
const closeAction = ref<'ask' | 'minimize' | 'quit'>('ask')
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

onMounted(async () => {
  const action = await window.electronAPI?.app.getCloseAction()
  if (action) closeAction.value = action

  const version = await window.electronAPI?.app.getVersion()
  if (version) appVersion.value = version
})

async function handleSave() {
  saving.value = true
  try {
    await settingsStore.saveAppSettings(form)
  } finally {
    saving.value = false
  }
}
</script>
