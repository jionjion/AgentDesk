<script setup lang="ts">
import type {HTMLAttributes} from 'vue'
import {ToastViewport, type ToastViewportProps} from 'reka-ui'
import {cn} from '@/lib/utils'

const props = defineProps<ToastViewportProps & { class?: HTMLAttributes['class'] }>()
</script>

<template>
  <ToastViewport
      :class="cn(
      'fixed top-6 right-6 z-[100] w-full md:max-w-[420px]',
      props.class,
    )"
  />
</template>
