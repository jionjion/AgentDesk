<template>
  <RouterView v-if="route.meta.layout === 'none'" />
  <DefaultLayout v-else />
  <Toaster />
  <CloseConfirmDialog />
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { Toaster } from '@/components/ui/toast'
import CloseConfirmDialog from '@/components/CloseConfirmDialog.vue'

const route = useRoute()
</script>
