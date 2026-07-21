<template>
  <article
      class="group flex min-h-48 flex-col rounded-2xl border border-gray-200 bg-white p-4 transition-all hover:-translate-y-0.5 hover:border-violet-200 hover:shadow-md hover:shadow-violet-100/60 dark:border-gray-700 dark:bg-gray-900 dark:hover:border-violet-800 dark:hover:shadow-none"
      @click="$emit('open')"
  >
    <div class="flex items-start gap-3">
      <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-50 ring-1 ring-violet-100 dark:bg-violet-900/25 dark:ring-violet-800/50">
        <Blocks :size="19" class="text-violet-600 dark:text-violet-400"/>
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <h3 class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ skill.name }}</h3>
          <Badge v-if="skill.builtin" variant="outline" class="shrink-0 text-[10px]">内置</Badge>
        </div>
        <p class="mt-0.5 truncate text-[11px] text-gray-400 dark:text-gray-500">{{ sourceLabel }}</p>
      </div>
    </div>

    <p class="mt-3 line-clamp-3 flex-1 text-xs leading-5 text-gray-500 dark:text-gray-400">
      {{ skill.description }}
    </p>

    <div class="mt-4 flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="flex min-w-0 items-center gap-1.5">
        <Badge v-if="skill.category" variant="secondary" class="max-w-28 truncate text-[10px] font-normal">
          {{ skill.category }}
        </Badge>
        <span v-if="skill.skillType === 'prompt' && !skill.builtin" class="text-[10px] text-amber-600 dark:text-amber-400">旧版指令</span>
      </div>
      <div class="flex items-center gap-2" @click.stop>
        <button
            v-if="!skill.builtin"
            class="rounded-md p-1.5 text-gray-300 opacity-0 transition-all hover:bg-red-50 hover:text-red-500 group-hover:opacity-100 dark:text-gray-600 dark:hover:bg-red-900/20"
            title="卸载技能"
            @click="$emit('delete')"
        >
          <Trash2 :size="14"/>
        </button>
        <span class="text-[11px]" :class="skill.enabled ? 'text-violet-600 dark:text-violet-400' : 'text-gray-400'">
          {{ skill.enabled ? '已启用' : '未启用' }}
        </span>
        <Switch :model-value="skill.enabled" @update:model-value="$emit('toggle-enabled')"/>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Blocks, Trash2} from 'lucide-vue-next'
import {Badge} from '@/components/ui/badge'
import {Switch} from '@/components/ui/switch'
import type {Skill} from '@/types/skill'

const props = defineProps<{ skill: Skill }>()

defineEmits<{
  open: []
  delete: []
  'toggle-enabled': []
}>()

const sourceLabel = computed(() => {
  if (props.skill.source === 'modelscope') return 'ModelScope 社区'
  if (props.skill.source === 'local') return '本地技能包'
  if (props.skill.source === 'legacy') return '旧版兼容技能'
  return 'AgentDesk 内置能力'
})
</script>
