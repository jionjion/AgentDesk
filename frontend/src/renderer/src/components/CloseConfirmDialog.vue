<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <DialogContent class="max-w-sm" @pointer-down-outside.prevent @escape-key-down.prevent>
      <DialogHeader>
        <DialogTitle>关闭窗口</DialogTitle>
        <DialogDescription>请选择关闭窗口时的操作</DialogDescription>
      </DialogHeader>

      <div class="space-y-3 py-2">
        <Button variant="outline" class="w-full justify-start gap-2" @click="handleChoice('minimize')">
          <MonitorDown :size="16"/>
          最小化到系统托盘
        </Button>
        <Button variant="outline" class="w-full justify-start gap-2" @click="handleChoice('quit')">
          <Power :size="16"/>
          退出应用
        </Button>
      </div>

      <div class="flex items-center gap-2">
        <input id="remember" v-model="remember" type="checkbox" class="h-4 w-4 rounded border-gray-300 accent-violet-500 cursor-pointer"/>
        <label for="remember" class="text-sm text-gray-500 dark:text-gray-400 cursor-pointer select-none">记住我的选择</label>
      </div>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue'
import {Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {Button} from '@/components/ui/button'
import {MonitorDown, Power} from 'lucide-vue-next'

const open = ref(false)
const remember = ref(false)

function handleOpenChange(val: boolean) {
  if (!val) {
    // 不允许通过 overlay / ESC 关闭，只能通过按钮选择
    return
  }
  open.value = val
}

async function handleChoice(choice: 'quit' | 'minimize') {
  if (remember.value) {
    await window.electronAPI?.app.setCloseAction(choice === 'minimize' ? 'minimize' : 'quit')
  }
  open.value = false
  window.electronAPI?.app.confirmClose(choice)
}

onMounted(() => {
  window.electronAPI?.app.onShowCloseDialog(() => {
    remember.value = false
    open.value = true
  })
})

onUnmounted(() => {
  window.electronAPI?.app.offShowCloseDialog()
})
</script>
