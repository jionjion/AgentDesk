<template>
  <RouterView v-if="route.meta.layout === 'none'" />
  <DefaultLayout v-else />
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

const route = useRoute()
</script>
