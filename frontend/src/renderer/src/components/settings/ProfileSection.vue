<template>
  <div class="space-y-6">
    <!-- 头像 -->
    <div class="flex items-center gap-4">
      <div class="relative cursor-pointer group" @click="avatarInputRef?.click()">
        <div class="h-16 w-16 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center overflow-hidden">
          <img v-if="avatarPreview || (form.avatar && !avatarError)" :src="avatarPreview || form.avatar" alt="头像" class="h-full w-full object-cover" @error="onImgError" />
          <UserIcon v-else :size="28" class="text-gray-400" />
        </div>
        <div class="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
          <CameraIcon :size="18" class="text-white" />
        </div>
        <Loader2 v-if="uploadingAvatar" :size="18" class="absolute inset-0 m-auto text-white animate-spin" />
      </div>
      <div class="space-y-1">
        <p class="text-sm font-medium text-gray-900 dark:text-gray-100">头像</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">点击更换头像图片</p>
      </div>
      <input
        ref="avatarInputRef"
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        class="hidden"
        @change="handleAvatarSelect"
      />
    </div>

    <!-- 昵称 -->
    <div class="space-y-2">
      <Label>昵称</Label>
      <Input v-model="form.nickname" placeholder="输入你的昵称" />
    </div>

    <!-- 修改密码 -->
    <div>
      <Button variant="outline" @click="showPasswordDialog = true">
        <KeyRound :size="16" class="mr-1" />
        修改密码
      </Button>
    </div>

    <!-- 保存 -->
    <div class="flex justify-end">
      <Button :disabled="saving" @click="handleSave">
        {{ saving ? '保存中...' : '保存' }}
      </Button>
    </div>

    <ChangePasswordDialog v-model:open="showPasswordDialog" />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { User as UserIcon, KeyRound, Camera as CameraIcon, Loader2 } from 'lucide-vue-next'
import { useSettingsStore } from '@/stores/settings'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import ChangePasswordDialog from './ChangePasswordDialog.vue'

const settingsStore = useSettingsStore()
const authStore = useAuthStore()

const form = reactive({
  nickname: '',
  avatar: ''
})
const showPasswordDialog = ref(false)
const saving = ref(false)
const uploadingAvatar = ref(false)
const avatarError = ref(false)
const avatarPreview = ref('')
const avatarInputRef = ref<HTMLInputElement | null>(null)

watch(() => settingsStore.profile, (val) => {
  if (val) {
    form.nickname = val.nickname
    form.avatar = val.avatar
    avatarError.value = false
  }
}, { immediate: true })

function onImgError() {
  // blob URL 不会出错，只有远程 URL 可能失败
  if (!avatarPreview.value) {
    avatarError.value = true
  }
}

async function handleAvatarSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''

  if (file.size > 2 * 1024 * 1024) {
    alert('图片大小不能超过 2MB')
    return
  }

  // 立即用本地 blob URL 预览
  avatarPreview.value = URL.createObjectURL(file)
  avatarError.value = false

  uploadingAvatar.value = true
  try {
    const profileData = await settingsStore.doUploadAvatar(file)
    form.avatar = profileData.avatar
    // 同步到 auth store
    if (authStore.user) {
      authStore.user.avatar = profileData.avatar
    }
  } catch {
    // 上传失败，清除预览
    avatarPreview.value = ''
    alert('头像上传失败，请重试')
  } finally {
    uploadingAvatar.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await settingsStore.saveProfile(form)
    // 同步到 auth store
    if (authStore.user) {
      authStore.user.nickname = form.nickname
      authStore.user.avatar = form.avatar
    }
  } finally {
    saving.value = false
  }
}
</script>
