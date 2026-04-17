<template>
  <div
      v-if="showMenu && filteredSkills.length > 0"
      class="absolute bottom-full mb-1 left-0 right-0 border border-gray-200 dark:border-gray-700 rounded-xl bg-white dark:bg-gray-800 shadow-lg z-20 overflow-hidden"
  >
    <div class="px-2.5 py-1.5 border-b border-gray-100 dark:border-gray-700">
      <span class="text-[11px] text-gray-400">选择技能 (已启用的再次选择可关闭)</span>
    </div>
    <div class="max-h-52 overflow-y-auto py-0.5">
      <div
          v-for="(skill, idx) in filteredSkills"
          :key="skill.name"
          class="flex items-center gap-2 px-2.5 py-1.5 cursor-pointer transition-colors"
          :class="idx === selectedIndex ? 'bg-violet-50 dark:bg-violet-900/20' : 'hover:bg-gray-50 dark:hover:bg-gray-800'"
          @click="selectSkill(skill)"
          @mouseenter="$emit('update:selectedIndex', idx)"
      >
        <Zap :size="12" class="shrink-0" :class="skill.enabled ? 'text-violet-500' : 'text-amber-500'"/>
        <span class="text-xs text-gray-800 dark:text-gray-200 shrink-0">/{{ skill.name }}</span>
        <span v-if="skill.enabled" class="text-[10px] text-violet-500 shrink-0">已启用</span>
        <span class="text-[11px] text-gray-400 truncate">{{ skill.description }}</span>
        <kbd v-if="idx === selectedIndex" class="text-[10px] text-gray-400 bg-gray-100 dark:bg-gray-700 px-1 py-0.5 rounded shrink-0 ml-auto">Enter</kbd>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {Zap} from 'lucide-vue-next'
import type {Skill} from '@/types/skill'

defineProps<{
  showMenu: boolean
  selectedIndex: number
  filteredSkills: Skill[]
}>()

const emit = defineEmits<{
  select: [skill: Skill]
  'update:selectedIndex': [idx: number]
}>()

function selectSkill(skill: Skill) {
  emit('select', skill)
}
</script>
