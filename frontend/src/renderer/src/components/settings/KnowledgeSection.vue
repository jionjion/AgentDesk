<template>
  <div class="space-y-6">
    <!-- 启用开关 -->
    <div class="flex items-center justify-between">
      <div>
        <Label>启用知识库自动检索</Label>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          对话时自动从知识库中检索相关内容，增强 AI 回答质量
        </p>
      </div>
      <Switch :model-value="form.enabled" @update:model-value="onToggleEnabled"/>
    </div>

    <template v-if="form.enabled">
      <!-- topK -->
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <Label>检索结果数量 (Top-K)</Label>
          <span class="text-sm font-mono text-gray-600 dark:text-gray-400">{{ form.topK }}</span>
        </div>
        <Slider
            :model-value="[form.topK]"
            :min="1" :max="20" :step="1"
            @update:model-value="onTopKChange"
        />
        <p class="text-xs text-gray-500 dark:text-gray-400">
          每次检索返回的最大片段数，数量越多上下文越丰富，但可能引入噪音
        </p>
      </div>

      <!-- scoreThreshold -->
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <Label>相似度阈值</Label>
          <span class="text-sm font-mono text-gray-600 dark:text-gray-400">{{ form.scoreThreshold.toFixed(2) }}</span>
        </div>
        <Slider
            :model-value="[form.scoreThreshold * 100]"
            :min="0" :max="100" :step="5"
            @update:model-value="onThresholdChange"
        />
        <p class="text-xs text-gray-500 dark:text-gray-400">
          低于该阈值的检索结果会被过滤。阈值越低命中率越高，但相关性可能降低
        </p>
      </div>

      <!-- 知识库列表概览 -->
      <div class="space-y-2">
        <Label>已创建的知识库</Label>
        <div v-if="loading" class="py-4 text-center text-xs text-gray-400">加载中...</div>
        <div v-else-if="bases.length === 0" class="py-4 text-center text-xs text-gray-400">
          暂无知识库，请前往「知识库」页面创建
        </div>
        <div v-else class="space-y-1.5">
          <div
              v-for="kb in bases" :key="kb.id"
              class="flex items-center justify-between px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50"
          >
            <div class="flex items-center gap-2 min-w-0">
              <Database :size="14" class="shrink-0 text-gray-500"/>
              <span class="text-sm truncate">{{ kb.name }}</span>
            </div>
            <div class="flex items-center gap-2 text-xs text-gray-400 shrink-0">
              <span>{{ kb.docCount }} 文档</span>
              <span>{{ kb.chunkCount }} 片段</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue'
import {Database} from 'lucide-vue-next'
import {Label} from '@/components/ui/label'
import {Switch} from '@/components/ui/switch'
import {Slider} from '@/components/ui/slider'
import {getKnowledgeBases, getKnowledgeSettings, updateKnowledgeSettings} from '@/api/knowledge'
import type {KnowledgeBase} from '@/types/knowledge'

const form = reactive({
  enabled: false,
  topK: 5,
  scoreThreshold: 0.8
})

const bases = ref<KnowledgeBase[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const [settingsRes, basesRes] = await Promise.all([
      getKnowledgeSettings(),
      getKnowledgeBases()
    ])
    form.enabled = settingsRes.data.enabled
    form.topK = settingsRes.data.topK
    form.scoreThreshold = settingsRes.data.scoreThreshold
    bases.value = basesRes.data
  } catch (e) {
    console.error('加载知识库设置失败', e)
  } finally {
    loading.value = false
  }
})

async function onToggleEnabled(val: boolean) {
  form.enabled = val
  await save()
}

async function onTopKChange(val: number[] | undefined) {
  if (!val) return
  form.topK = val[0]
  await save()
}

async function onThresholdChange(val: number[] | undefined) {
  if (!val) return
  form.scoreThreshold = val[0] / 100
  await save()
}

async function save() {
  try {
    await updateKnowledgeSettings({
      enabled: form.enabled,
      topK: form.topK,
      scoreThreshold: form.scoreThreshold
    })
  } catch (e) {
    console.error('保存知识库设置失败', e)
  }
}
</script>
