import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const activeTab = ref<'tasks' | 'channels'>('tasks')
  const currentUser = ref({
    id: '1',
    name: 'Jion Jion',
    avatar: '',
    plan: 'Pro Plan'
  })

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    sidebarCollapsed,
    activeTab,
    currentUser,
    toggleSidebar
  }
})
