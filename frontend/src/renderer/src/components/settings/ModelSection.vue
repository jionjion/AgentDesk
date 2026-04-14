<template>
  <div class="space-y-6">
    <!-- 模型选择 -->
    <div class="space-y-2">
      <Label>模型</Label>
      <Select v-model="form.modelId">
        <SelectTrigger>
          <SelectValue placeholder="选择模型"/>
        </SelectTrigger>
        <SelectContent>
          <template v-for="(models, group) in groupedModels" :key="group">
            <div class="px-2 py-1.5 text-xs font-medium text-gray-400">{{ group }}</div>
            <SelectItem v-for="m in models" :key="m.id" :value="m.id">
              {{ m.displayName }}
            </SelectItem>
          </template>
        </SelectContent>
      </Select>
    </div>

    <!-- Temperature -->
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <Label>Temperature</Label>
        <span class="text-sm text-gray-500 dark:text-gray-400">{{ form.temperature }}</span>
      </div>
      <Slider v-model="temperatureValue" :min="0" :max="100" :step="1"/>
    </div>

    <!-- 最大 Token -->
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <Label>最大 Token</Label>
        <span class="text-sm text-gray-500 dark:text-gray-400">{{ form.maxTokens }}</span>
      </div>
      <Slider v-model="maxTokensValue" :min="256" :max="65536" :step="256"/>
    </div>

    <!-- Top P -->
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <Label>Top P</Label>
        <span class="text-sm text-gray-500 dark:text-gray-400">{{ form.topP }}</span>
      </div>
      <Slider v-model="topPValue" :min="0" :max="100" :step="1"/>
    </div>

    <!-- 深度思考 -->
    <div class="flex items-center justify-between">
      <div>
        <Label>深度思考</Label>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">启用后模型会展示推理过程（需模型支持）</p>
      </div>
      <Switch v-model:checked="form.enableThinking"/>
    </div>

    <!-- 系统提示词 -->
    <div class="space-y-2">
      <Label>系统提示词</Label>
      <Textarea v-model="form.systemPrompt" :rows="4" placeholder="输入系统提示词（可选）"/>
    </div>

    <!-- 保存 -->
    <div class="flex justify-end">
      <Button :disabled="saving" @click="handleSave">
        {{ saving ? '保存中...' : '保存' }}
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {useSettingsStore} from '@/stores/settings'
import type {ModelSettings} from '@/types/settings'
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {Slider} from '@/components/ui/slider'
import {Switch} from '@/components/ui/switch'
import {Textarea} from '@/components/ui/textarea'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'

const settingsStore = useSettingsStore()

const form = reactive<ModelSettings>({
  modelId: 'qwen3.6-plus',
  temperature: 0.7,
  maxTokens: 4096,
  topP: 0.9,
  enableThinking: false,
  systemPrompt: ''
})
const saving = ref(false)

const groupedModels = computed(() => settingsStore.groupedModels)

onMounted(() => {
  if (Object.keys(settingsStore.groupedModels).length === 0) {
    settingsStore.fetchModels()
  }
})

// Slider needs array values; temperature/topP are 0-1 mapped to 0-100
const temperatureValue = computed({
  get: () => [Math.round(form.temperature * 100)],
  set: (val: number[]) => {
    form.temperature = Math.round(val[0]) / 100
  }
})

const maxTokensValue = computed({
  get: () => [form.maxTokens],
  set: (val: number[]) => {
    form.maxTokens = val[0]
  }
})

const topPValue = computed({
  get: () => [Math.round(form.topP * 100)],
  set: (val: number[]) => {
    form.topP = Math.round(val[0]) / 100
  }
})

watch(() => settingsStore.model, (val) => {
  Object.assign(form, val)
}, {immediate: true})

async function handleSave() {
  saving.value = true
  try {
    await settingsStore.saveModelSettings(form)
  } finally {
    saving.value = false
  }
}
</script>
