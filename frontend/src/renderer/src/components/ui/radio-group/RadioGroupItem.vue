<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { RadioGroupIndicator, RadioGroupItem, type RadioGroupItemProps, useForwardProps } from 'reka-ui'
import { Circle } from 'lucide-vue-next'
import { cn } from '@/lib/utils'

const props = defineProps<RadioGroupItemProps & { class?: HTMLAttributes['class'] }>()
const forwarded = useForwardProps(props)
</script>

<template>
  <RadioGroupItem
    v-bind="{ ...forwarded, class: undefined }"
    :class="cn(
      'aspect-square h-4 w-4 rounded-full border border-primary text-primary shadow focus:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
      props.class,
    )"
  >
    <RadioGroupIndicator class="flex items-center justify-center">
      <Circle class="h-3.5 w-3.5 fill-primary" />
    </RadioGroupIndicator>
  </RadioGroupItem>
</template>
