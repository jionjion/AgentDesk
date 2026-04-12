<script setup lang="ts">
import {ref} from 'vue'
import {ToastProvider} from 'reka-ui'
import {useToast} from './use-toast'
import Toast from './Toast.vue'
import ToastTitle from './ToastTitle.vue'
import ToastDescription from './ToastDescription.vue'
import ToastClose from './ToastClose.vue'
import ToastViewport from './ToastViewport.vue'

const {toasts, dismiss} = useToast()
const hovered = ref(false)

/** 堆叠参数 */
const OFFSET_Y = 14     // 每层向下偏移 px
const SCALE_STEP = 0.06 // 每层缩小比例
const MAX_VISIBLE = 3   // 最多显示几层堆叠

function stackStyle(index: number, total: number) {
  if (index >= MAX_VISIBLE) {
    return {opacity: 0, pointerEvents: 'none' as const}
  }
  if (hovered.value) {
    // hover 展开：每层向下平移，恢复原始大小
    return {
      transform: `translateY(${index * 100}%) translateY(${index * 12}px)`,
      opacity: 1,
      zIndex: total - index,
    }
  }
  return {
    transform: `translateY(${index * OFFSET_Y}px) scale(${1 - index * SCALE_STEP})`,
    opacity: 1 - index * 0.3,
    zIndex: total - index,
  }
}
</script>

<template>
  <ToastProvider>
    <Toast
        v-for="(t, i) in toasts"
        :key="t.id"
        :variant="t.variant"
        :duration="t.duration ?? 5000"
        :open="t.open"
        :style="stackStyle(i, toasts.length)"
        @update:open="(value) => { if (!value) dismiss(t.id) }"
    >
      <div class="grid gap-1">
        <ToastTitle v-if="t.title">{{ t.title }}</ToastTitle>
        <ToastDescription v-if="t.description">{{ t.description }}</ToastDescription>
      </div>
      <ToastClose/>
    </Toast>
    <ToastViewport @mouseenter="hovered = true" @mouseleave="hovered = false"/>
  </ToastProvider>
</template>
