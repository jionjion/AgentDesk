<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { AlertDialogOverlay, type AlertDialogOverlayProps } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = defineProps<AlertDialogOverlayProps & { class?: HTMLAttributes['class'] }>()
</script>

<template>
  <AlertDialogOverlay
    :class="cn(
      'fixed inset-0 z-50 bg-black/30 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
      props.class,
    )"
  />
</template>
