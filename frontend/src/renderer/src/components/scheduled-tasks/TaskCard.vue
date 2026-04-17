<template>
  <div
      class="border border-gray-200 dark:border-gray-700 rounded-xl p-5 hover:shadow-sm transition-shadow cursor-pointer group"
      @click="$emit('edit')"
  >
    <div class="flex items-center justify-between mb-3">
      <Switch :checked="task.enabled" @click.stop @update:checked="$emit('toggle-enabled')"/>
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button variant="ghost" size="icon" class="h-8 w-8" @click.stop>
            <MoreHorizontal :size="16"/>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem @click.stop="$emit('edit')">
            <Pencil :size="14" class="mr-2"/>
            编辑
          </DropdownMenuItem>
          <DropdownMenuItem @click.stop="$emit('run-now')">
            <Play :size="14" class="mr-2"/>
            立即执行
          </DropdownMenuItem>
          <DropdownMenuItem class="text-red-500" @click.stop="$emit('delete')">
            <Trash2 :size="14" class="mr-2"/>
            删除
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
    <h3 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-2">{{ task.name }}</h3>
    <p class="text-xs text-gray-400 dark:text-gray-500 line-clamp-2 mb-3">{{ task.description || task.prompt }}</p>
    <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
      <Clock :size="14"/>
      <span>{{ task.scheduleLabel || task.cronExpression }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {Clock, MoreHorizontal, Pencil, Play, Trash2} from 'lucide-vue-next'
import {Button} from '@/components/ui/button'
import {Switch} from '@/components/ui/switch'
import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@/components/ui/dropdown-menu'
import type {ScheduledTask} from '@/types/scheduledTask'

defineProps<{
  task: ScheduledTask
}>()

defineEmits<{
  edit: []
  delete: []
  'toggle-enabled': []
  'run-now': []
}>()
</script>
