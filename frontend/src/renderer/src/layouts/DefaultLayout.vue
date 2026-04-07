<template>
  <div class="h-screen w-screen flex flex-col overflow-hidden bg-white dark:bg-gray-900">
    <TitleBar />
    <div class="flex flex-1 overflow-hidden">
      <AppSidebar v-show="!appStore.sidebarCollapsed" />
      <main class="flex-1 overflow-auto">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import TitleBar from '@/components/TitleBar.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const appStore = useAppStore()
const authStore = useAuthStore()

onMounted(() => {
  appStore.initTheme()
  authStore.fetchUser()
})
</script>
