<template>
  <RouterView v-if="route.meta.layout === 'none'"/>
  <DefaultLayout v-else/>
  <Toaster/>
  <CloseConfirmDialog/>
</template>

<script setup lang="ts">
import {onMounted, watch} from 'vue'
import {useRoute} from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import {Toaster} from '@/components/ui/toast'
import CloseConfirmDialog from '@/components/CloseConfirmDialog.vue'
import {useSandboxStore} from '@/stores/sandbox'
import {useAuthStore} from '@/stores/auth'
import {useRemoteExecStore} from '@/stores/remoteExec'

const route = useRoute()
const sandboxStore = useSandboxStore()
const authStore = useAuthStore()
const remoteExecStore = useRemoteExecStore()

// 应用启动时预加载 Python 沙箱
onMounted(() => {
  sandboxStore.initEngine()
  // 如果已登录, 立即连接远程执行 WebSocket
  if (authStore.isLoggedIn) {
    remoteExecStore.connect()
  }
})

// 监听登录状态变化: 登录后连接, 登出后断开
watch(() => authStore.isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    remoteExecStore.connect()
  } else {
    remoteExecStore.disconnect()
  }
})
</script>
