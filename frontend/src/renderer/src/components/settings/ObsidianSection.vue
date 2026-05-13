<template>
  <div class="space-y-6">
    <!-- Vault 路径 -->
    <div class="space-y-2">
      <Label>Obsidian Vault 路径</Label>
      <p class="text-xs text-gray-500 dark:text-gray-400">
        选择你的 Obsidian 笔记库目录，沉淀的知识将写入此目录
      </p>
      <div class="flex gap-2">
        <Input
            v-model="form.vaultPath"
            placeholder="点击浏览选择目录..."
            class="flex-1"
            readonly
        />
        <Button size="sm" variant="outline" @click="handleBrowse">
          <FolderOpen :size="14" class="mr-1"/>
          浏览
        </Button>
        <Button
            size="sm"
            variant="outline"
            :disabled="!form.vaultPath || validating"
            @click="handleValidate"
        >
          <Loader2 v-if="validating" :size="14" class="mr-1 animate-spin"/>
          <CheckCircle2 v-else :size="14" class="mr-1"/>
          验证
        </Button>
      </div>
      <!-- 验证结果 -->
      <p v-if="validateResult" class="text-xs" :class="validateResult.valid ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400'">
        {{ validateResult.message }}
      </p>
    </div>

    <!-- 默认分类 -->
    <div class="space-y-2">
      <Label>默认分类目录</Label>
      <p class="text-xs text-gray-500 dark:text-gray-400">
        笔记将保存在 Vault 下的此目录中
      </p>
      <Input
          v-model="form.defaultCategory"
          placeholder="AgentDesk"
          @blur="saveSettings"
          @keydown.enter="($event.target as HTMLInputElement)?.blur()"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue'
import {useSettingsStore} from '@/stores/settings'
import {validateVaultPath} from '@/api/obsidian'
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {Input} from '@/components/ui/input'
import {CheckCircle2, FolderOpen, Loader2} from 'lucide-vue-next'

const settingsStore = useSettingsStore()

const form = ref({
  vaultPath: '',
  defaultCategory: 'AgentDesk'
})

const validating = ref(false)
const validateResult = ref<{ valid: boolean; message: string } | null>(null)

watch(() => settingsStore.obsidian, (val) => {
  form.value.vaultPath = val.vaultPath || ''
  form.value.defaultCategory = val.defaultCategory || 'AgentDesk'
}, {immediate: true})

async function handleBrowse() {
  const path = await window.electronAPI?.dialog.openDirectory()
  if (path) {
    form.value.vaultPath = path
    validateResult.value = null
    await saveSettings()
  }
}

async function handleValidate() {
  if (!form.value.vaultPath) return
  validating.value = true
  try {
    const res = await validateVaultPath(form.value.vaultPath)
    validateResult.value = res.data
  } catch {
    validateResult.value = {valid: false, message: '验证请求失败'}
  } finally {
    validating.value = false
  }
}

async function saveSettings() {
  try {
    await settingsStore.saveObsidianSettings({
      vaultPath: form.value.vaultPath || null,
      autoExportOnSessionEnd: false,
      defaultCategory: form.value.defaultCategory || 'AgentDesk'
    })
  } catch { /* handled by interceptor */ }
}
</script>
