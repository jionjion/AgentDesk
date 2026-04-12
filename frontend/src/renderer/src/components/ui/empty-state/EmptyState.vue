<template>
  <div class="flex flex-col items-center justify-center py-20 px-8">
    <!-- 图标 -->
    <div class="w-16 h-16 rounded-2xl bg-gray-50 dark:bg-gray-800 flex items-center justify-center mb-5">
      <component :is="icon" :size="32" class="text-gray-400 dark:text-gray-500"/>
    </div>
    <!-- 标题 -->
    <h3 class="text-lg font-semibold text-gray-700 dark:text-gray-300 mb-2">{{ title }}</h3>
    <!-- 描述 -->
    <p class="text-sm text-gray-400 dark:text-gray-500 text-center max-w-xs mb-6">{{ description }}</p>
    <!-- 操作按钮 -->
    <Button v-if="actionLabel" variant="outline" @click="$emit('action')">
      <component v-if="actionIcon" :is="actionIcon" :size="16" class="mr-1.5"/>
      {{ actionLabel }}
    </Button>
  </div>
</template>

<script setup lang="ts">
import type {Component} from 'vue'
import {Button} from '@/components/ui/button'

defineProps<{
  icon: Component
  title: string
  description: string
  actionLabel?: string
  actionIcon?: Component
}>()

defineEmits<{
  action: []
}>()
</script>
