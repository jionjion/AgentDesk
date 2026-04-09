<script setup lang="ts">
import { ToastProvider } from 'reka-ui'
import { useToast } from './use-toast'
import Toast from './Toast.vue'
import ToastTitle from './ToastTitle.vue'
import ToastDescription from './ToastDescription.vue'
import ToastClose from './ToastClose.vue'
import ToastViewport from './ToastViewport.vue'

const { toasts, dismiss } = useToast()
</script>

<template>
  <ToastProvider>
    <Toast
      v-for="t in toasts"
      :key="t.id"
      :variant="t.variant"
      :duration="t.duration ?? 5000"
      :open="t.open"
      @update:open="(value) => { if (!value) dismiss(t.id) }"
    >
      <div class="grid gap-1">
        <ToastTitle v-if="t.title">{{ t.title }}</ToastTitle>
        <ToastDescription v-if="t.description">{{ t.description }}</ToastDescription>
      </div>
      <ToastClose />
    </Toast>
    <ToastViewport />
  </ToastProvider>
</template>
