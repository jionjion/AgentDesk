<template>
  <div
      class="border border-gray-200 dark:border-gray-700 rounded-xl p-4 hover:shadow-sm transition-shadow cursor-pointer group"
      @click="$emit('edit')"
  >
    <div class="flex items-start gap-3 mb-2">
      <div class="w-10 h-10 rounded-lg bg-gray-100 dark:bg-gray-800 flex items-center justify-center shrink-0">
        <component :is="Sparkles" :size="20" class="text-gray-600 dark:text-gray-400"/>
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <h3 class="text-sm font-normal text-gray-800 dark:text-gray-200 truncate">{{ skill.name }}</h3>
          <Badge v-if="skill.builtin" variant="outline" class="text-[10px] shrink-0">内置</Badge>
          <Badge v-if="skill.category" variant="secondary" class="text-[10px] shrink-0">{{ skill.category }}</Badge>
        </div>
        <p class="text-xs text-gray-400 dark:text-gray-500 mt-1 line-clamp-2" :title="skill.description">{{ skill.description }}</p>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="flex items-center justify-between mt-3 pt-3 border-t border-gray-100 dark:border-gray-800">
      <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <Button v-if="!skill.builtin" variant="ghost" size="icon" class="h-7 w-7" @click.stop="$emit('edit')">
          <Pencil :size="14"/>
        </Button>
        <Button v-if="!skill.builtin" variant="ghost" size="icon" class="h-7 w-7 text-red-500 hover:text-red-600" @click.stop="$emit('delete')">
          <Trash2 :size="14"/>
        </Button>
      </div>
      <Button
          :variant="skill.enabled ? 'default' : 'outline'"
          size="sm"
          class="h-7 text-xs"
          @click.stop="$emit('toggle-enabled')"
      >
        <Zap :size="12" class="mr-1"/>
        {{ skill.enabled ? '已启用' : '启用' }}
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {Pencil, Sparkles, Trash2, Zap} from 'lucide-vue-next'
import {Button} from '@/components/ui/button'
import {Badge} from '@/components/ui/badge'
import type {Skill} from '@/types/skill'

defineProps<{
  skill: Skill
}>()

defineEmits<{
  edit: []
  delete: []
  'toggle-enabled': []
}>()
</script>
