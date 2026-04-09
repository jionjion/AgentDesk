<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { ToastRoot, type ToastRootEmits, type ToastRootProps, useForwardPropsEmits } from 'reka-ui'
import { type ToastVariants, toastVariants } from '.'
import { cn } from '@/lib/utils'

const props = defineProps<
  ToastRootProps & {
    class?: HTMLAttributes['class']
    variant?: ToastVariants['variant']
  }
>()

const emits = defineEmits<ToastRootEmits>()
const forwarded = useForwardPropsEmits(props, emits)
</script>

<template>
  <ToastRoot
    v-bind="{ ...forwarded, class: undefined }"
    :class="cn(toastVariants({ variant }), props.class)"
    @update:open="(value) => { if (!value) emits('update:open', value) }"
  >
    <slot />
  </ToastRoot>
</template>
