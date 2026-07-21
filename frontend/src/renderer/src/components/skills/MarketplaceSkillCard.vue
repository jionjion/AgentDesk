<template>
  <article
      class="group flex min-h-56 flex-col rounded-2xl border border-gray-200 bg-white p-4 transition-all hover:-translate-y-0.5 hover:border-violet-200 hover:shadow-md hover:shadow-violet-100/60 dark:border-gray-700 dark:bg-gray-900 dark:hover:border-violet-800 dark:hover:shadow-none"
      @click="$emit('open')"
  >
    <div class="flex items-start gap-3">
      <div class="flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-gray-50 ring-1 ring-gray-100 dark:bg-gray-800 dark:ring-gray-700">
        <img
            v-if="skill.logoUrl && !imageFailed"
            :src="skill.logoUrl"
            :alt="skill.displayName"
            class="h-full w-full object-cover"
            @error="imageFailed = true"
        />
        <Blocks v-else :size="20" class="text-violet-500"/>
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <h3 class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ skill.displayName }}</h3>
          <Badge v-if="skill.installed" variant="outline" class="shrink-0 border-emerald-200 text-[10px] text-emerald-600 dark:border-emerald-800 dark:text-emerald-400">
            已安装
          </Badge>
        </div>
        <p class="mt-0.5 truncate text-[11px] text-gray-400 dark:text-gray-500">
          {{ authorLabel }}
        </p>
      </div>
    </div>

    <p class="mt-3 line-clamp-3 flex-1 text-xs leading-5 text-gray-500 dark:text-gray-400">
      {{ skill.description || '这个技能暂时没有提供说明。' }}
    </p>

    <div class="mt-3 flex items-center gap-3 text-[10px] text-gray-400 dark:text-gray-500">
      <span class="flex items-center gap-1"><Download :size="11"/>{{ formatCount(skill.downloads) }}</span>
      <span class="flex items-center gap-1"><Eye :size="11"/>{{ formatCount(skill.viewCount) }}</span>
      <Badge v-if="skill.category" variant="secondary" class="max-w-32 truncate text-[10px] font-normal">
        {{ skill.category }}
      </Badge>
    </div>

    <div class="mt-4 flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <span class="max-w-52 truncate font-mono text-[10px] text-gray-400">{{ skill.id }}</span>
      <Button
          size="sm"
          class="h-7 rounded-lg px-3 text-xs"
          :variant="skill.installed ? 'outline' : 'default'"
          :disabled="skill.installed || installing"
          @click.stop="$emit('install')"
      >
        <Loader2 v-if="installing" :size="12" class="mr-1 animate-spin"/>
        <Check v-else-if="skill.installed" :size="12" class="mr-1"/>
        <Download v-else :size="12" class="mr-1"/>
        {{ skill.installed ? '已安装' : installing ? '安装中' : '安装' }}
      </Button>
    </div>
  </article>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue'
import {Blocks, Check, Download, Eye, Loader2} from 'lucide-vue-next'
import {Badge} from '@/components/ui/badge'
import {Button} from '@/components/ui/button'
import type {MarketplaceSkill} from '@/types/skill'

const props = defineProps<{
  skill: MarketplaceSkill
  installing?: boolean
}>()

defineEmits<{
  open: []
  install: []
}>()

const imageFailed = ref(false)
const authorLabel = computed(() => props.skill.developer || props.skill.owner || '社区开发者')

function formatCount(value: number): string {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}万`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return String(value || 0)
}
</script>
