<template>
  <RouterView v-if="route.meta.layout === 'none'"/>
  <DefaultLayout v-else/>
  <Toaster/>
  <CloseConfirmDialog/>
</template>

<script setup lang="ts">
import {onMounted} from 'vue'
import {useRoute} from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import {Toaster} from '@/components/ui/toast'
import CloseConfirmDialog from '@/components/CloseConfirmDialog.vue'
import {useSandboxStore} from '@/stores/sandbox'

const route = useRoute()
const sandboxStore = useSandboxStore()

// 应用启动时预加载 Python 沙箱
onMounted(() => {
  sandboxStore.initEngine()
})
</script>
