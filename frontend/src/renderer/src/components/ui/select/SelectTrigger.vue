<script setup lang="ts">
import type {HTMLAttributes} from 'vue'
import {SelectIcon, SelectTrigger, type SelectTriggerProps, SelectValue, useForwardProps} from 'reka-ui'
import {ChevronDown} from 'lucide-vue-next'
import {cn} from '@/lib/utils'

const props = defineProps<SelectTriggerProps & { class?: HTMLAttributes['class'] }>()
const forwarded = useForwardProps(props)
</script>

<template>
  <SelectTrigger
      v-bind="{ ...forwarded, class: undefined }"
      :class="cn(
      'flex h-9 w-full items-center justify-between gap-2 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50 [&>span]:line-clamp-1',
      props.class,
    )"
  >
    <slot>
      <SelectValue/>
    </slot>
    <SelectIcon as-child>
      <ChevronDown class="h-4 w-4 shrink-0 opacity-50"/>
    </SelectIcon>
  </SelectTrigger>
</template>
