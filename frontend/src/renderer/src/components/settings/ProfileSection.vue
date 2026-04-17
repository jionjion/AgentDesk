<template>
  <div class="space-y-6">
    <!-- 头像 -->
    <div class="flex items-center gap-4">
      <div class="relative cursor-pointer group" @click="avatarInputRef?.click()">
        <div class="h-16 w-16 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center overflow-hidden">
          <img v-if="avatarPreview || (form.avatar && !avatarError)" :src="avatarPreview || form.avatar" alt="头像" class="h-full w-full object-cover" @error="onImgError"/>
          <UserIcon v-else :size="28" class="text-gray-400"/>
        </div>
        <div class="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
          <CameraIcon :size="18" class="text-white"/>
        </div>
        <Loader2 v-if="uploadingAvatar" :size="18" class="absolute inset-0 m-auto text-white animate-spin"/>
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
      <Input v-model="form.nickname" :placeholder="settingsStore.profile?.nickname || '输入你的昵称'"/>
    </div>

    <!-- 修改密码 -->
    <div class="space-y-2">
      <Label>当前密码</Label>
      <Input v-model="passwordForm.oldPassword" type="password" placeholder="请输入当前密码"/>
    </div>
    <div class="space-y-2">
      <Label>新密码</Label>
      <Input v-model="passwordForm.newPassword" type="password" placeholder="至少 6 个字符"/>
    </div>
    <div class="space-y-2">
      <Label>确认密码</Label>
      <Input v-model="passwordForm.confirmPassword" type="password" placeholder="再次输入新密码"/>
    </div>
    <p v-if="passwordError" class="text-sm text-red-500">{{ passwordError }}</p>

    <!-- 保存 -->
    <div class="flex justify-end">
      <Button :disabled="saving" @click="handleSave">
        {{ saving ? '保存中...' : '保存' }}
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {reactive, ref, watch} from 'vue'
import {Camera as CameraIcon, Loader2, User as UserIcon} from 'lucide-vue-next'
import {useSettingsStore} from '@/stores/settings'
import {useAuthStore} from '@/stores/auth'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'

const settingsStore = useSettingsStore()
const authStore = useAuthStore()

const form = reactive({
  nickname: '',
  avatar: ''
})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordError = ref('')
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
}, {immediate: true})

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
  // 如果填写了密码字段，先校验并修改密码
  const hasPassword = passwordForm.oldPassword || passwordForm.newPassword || passwordForm.confirmPassword
  if (hasPassword) {
    passwordError.value = ''
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      passwordError.value = '两次输入的密码不一致'
      return
    }
    if (passwordForm.newPassword.length < 6) {
      passwordError.value = '新密码至少 6 个字符'
      return
    }
  }

  saving.value = true
  try {
    if (hasPassword) {
      await settingsStore.doChangePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
      passwordError.value = ''
    }
    await settingsStore.saveProfile(form)
    if (authStore.user) {
      authStore.user.nickname = form.nickname
      authStore.user.avatar = form.avatar
    }
  } catch (e: any) {
    if (hasPassword) {
      passwordError.value = e.response?.data?.message || '修改失败，请重试'
    }
  } finally {
    saving.value = false
  }
}
</script>
