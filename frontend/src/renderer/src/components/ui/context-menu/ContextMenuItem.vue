<script setup lang="ts">
import type {HTMLAttributes} from 'vue'
import {ContextMenuItem, type ContextMenuItemProps, useForwardProps} from 'reka-ui'
import {cn} from '@/lib/utils'

const props = defineProps<ContextMenuItemProps & { class?: HTMLAttributes['class'], inset?: boolean }>()

const forwardedProps = useForwardProps(props)
</script>

<template>
  <ContextMenuItem
      v-bind="forwardedProps"
      :class="
      cn(
        'relative flex cursor-default select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&>svg]:size-4 [&>svg]:shrink-0',
        inset && 'pl-8',
        props.class,
      )
    "
  >
    <slot/>
  </ContextMenuItem>
</template>
