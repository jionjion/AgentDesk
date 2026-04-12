<script setup lang="ts">
import type {HTMLAttributes} from 'vue'
import {AlertDialogAction, type AlertDialogActionProps, useForwardProps} from 'reka-ui'
import {cn} from '@/lib/utils'
import {buttonVariants} from '@/components/ui/button'

const props = withDefaults(
    defineProps<AlertDialogActionProps & { class?: HTMLAttributes['class'] }>(),
    {as: 'button'},
)

const forwardedProps = useForwardProps(props)
</script>

<template>
  <AlertDialogAction
      v-bind="forwardedProps"
      :class="cn(buttonVariants({ variant: 'destructive', size: 'sm' }), props.class)"
  >
    <slot/>
  </AlertDialogAction>
</template>
