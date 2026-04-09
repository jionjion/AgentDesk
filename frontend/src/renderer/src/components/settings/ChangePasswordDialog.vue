<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>修改密码</DialogTitle>
      </DialogHeader>

      <div class="space-y-4">
        <div class="space-y-2">
          <Label>当前密码</Label>
          <Input v-model="form.oldPassword" type="password" placeholder="请输入当前密码" />
        </div>
        <div class="space-y-2">
          <Label>新密码</Label>
          <Input v-model="form.newPassword" type="password" placeholder="至少 6 个字符" />
        </div>
        <div class="space-y-2">
          <Label>确认密码</Label>
          <Input v-model="form.confirmPassword" type="password" placeholder="再次输入新密码" />
        </div>
        <p v-if="errorMsg" class="text-sm text-red-500">{{ errorMsg }}</p>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="open = false">取消</Button>
        <Button :disabled="saving" @click="handleSubmit">
          {{ saving ? '提交中...' : '确认修改' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const open = defineModel<boolean>('open', { default: false })
const settingsStore = useSettingsStore()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const errorMsg = ref('')
const saving = ref(false)

watch(open, (val) => {
  if (val) {
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    errorMsg.value = ''
  }
})

async function handleSubmit() {
  errorMsg.value = ''

  if (form.newPassword !== form.confirmPassword) {
    errorMsg.value = '两次输入的密码不一致'
    return
  }
  if (form.newPassword.length < 6) {
    errorMsg.value = '新密码至少 6 个字符'
    return
  }

  saving.value = true
  try {
    await settingsStore.doChangePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    open.value = false
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '修改失败，请重试'
  } finally {
    saving.value = false
  }
}
</script>
