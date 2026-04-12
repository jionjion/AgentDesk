<script setup lang="ts">
import type {HTMLAttributes} from 'vue'
import {AlertDialogCancel, type AlertDialogCancelProps, useForwardProps} from 'reka-ui'
import {cn} from '@/lib/utils'
import {buttonVariants} from '@/components/ui/button'

const props = withDefaults(
    defineProps<AlertDialogCancelProps & { class?: HTMLAttributes['class'] }>(),
    {as: 'button'},
)

const forwardedProps = useForwardProps(props)
</script>

<template>
  <AlertDialogCancel
      v-bind="forwardedProps"
      :class="cn(buttonVariants({ variant: 'outline', size: 'sm' }), props.class)"
  >
    <slot/>
  </AlertDialogCancel>
</template>
