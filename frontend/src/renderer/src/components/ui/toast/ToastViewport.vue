<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { ToastViewport, type ToastViewportProps } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = defineProps<ToastViewportProps & { class?: HTMLAttributes['class'] }>()
</script>

<template>
  <ToastViewport
    :class="cn(
      'fixed top-0 right-0 z-[100] flex max-h-screen w-full flex-col-reverse gap-2 p-4 sm:flex-col md:max-w-[420px]',
      props.class,
    )"
  />
</template>
