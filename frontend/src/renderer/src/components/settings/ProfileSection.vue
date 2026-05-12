<template>
  <div class="space-y-6">
    <!-- 头像 -->
    <div class="flex items-center gap-4">
      <div class="relative cursor-pointer group" @click="avatarInputRef?.click()">
        <div
            class="h-16 w-16 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center overflow-hidden">
          <img v-if="avatarPreview || (form.avatar && !avatarError)"
               :src="avatarPreview || form.avatar"
               alt="头像"
               class="h-full w-full object-cover"
               @error="onImgError"/>
          <UserIcon v-else :size="28" class="text-gray-400"/>
        </div>
        <div
            class="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
          <CameraIcon :size="18" class="text-white"/>
        </div>
        <Loader2 v-if="uploadingAvatar" :size="18"
                 class="absolute inset-0 m-auto text-white animate-spin"/>
      </div>
      <div class="space-y-0.5">
        <p class="text-sm font-medium text-gray-900 dark:text-gray-100">头像</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">点击更换，支持 JPG/PNG/GIF/WebP，最大 2MB</p>
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
      <Input v-model="form.nickname"
             :placeholder="settingsStore.profile?.nickname || '输入你的昵称'"
             @blur="handleNicknameBlur"/>
    </div>

    <!-- 密码 -->
    <div class="flex items-center justify-between">
      <div class="space-y-0.5">
        <Label>登录密码</Label>
        <p class="text-xs text-gray-500 dark:text-gray-400">定期修改密码可以提高账号安全性</p>
      </div>
      <Button variant="outline" size="sm" class="w-24" @click="showPasswordDialog = true">
        修改密码
      </Button>
    </div>

    <!-- 退出登录 -->
    <div class="flex items-center justify-between pt-4 border-t border-gray-200 dark:border-gray-700">
      <p class="text-xs text-gray-500 dark:text-gray-400">退出后需重新登录</p>
      <Button variant="outline" size="sm" class="w-24 text-red-500 hover:text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:text-red-300 dark:hover:bg-red-950/30"
              @click="handleLogout">
        退出登录
      </Button>
    </div>

    <!-- 修改密码弹窗 -->
    <ChangePasswordDialog v-model:open="showPasswordDialog"/>
  </div>
</template>

<script setup lang="ts">
import {reactive, ref, watch} from 'vue'
import {Camera as CameraIcon, Loader2, LogOut, User as UserIcon} from 'lucide-vue-next'
import {useSettingsStore} from '@/stores/settings'
import {useAuthStore} from '@/stores/auth'
import {cacheAvatar} from '@/utils/avatar-cache'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {Label} from '@/components/ui/label'
import ChangePasswordDialog from '@/components/settings/ChangePasswordDialog.vue'

const settingsStore = useSettingsStore()
const authStore = useAuthStore()

const form = reactive({
  nickname: '',
  avatar: ''
})

const uploadingAvatar = ref(false)
const avatarError = ref(false)
const avatarPreview = ref('')
const avatarInputRef = ref<HTMLInputElement | null>(null)
const showPasswordDialog = ref(false)

watch(() => settingsStore.profile, (val) => {
  if (val) {
    form.nickname = val.nickname
    form.avatar = val.avatar
    avatarError.value = false
  }
}, {immediate: true})

function onImgError() {
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

  avatarPreview.value = URL.createObjectURL(file)
  avatarError.value = false

  uploadingAvatar.value = true
  try {
    const profileData = await settingsStore.doUploadAvatar(file)
    form.avatar = profileData.avatar
    const cached = await cacheAvatar(profileData.avatar)
    if (authStore.user) {
      authStore.user.avatar = cached || profileData.avatar
    }
  } catch {
    avatarPreview.value = ''
    alert('头像上传失败，请重试')
  } finally {
    uploadingAvatar.value = false
  }
}

async function handleNicknameBlur() {
  const original = settingsStore.profile?.nickname || ''
  if (form.nickname === original || !form.nickname.trim()) return

  try {
    await settingsStore.saveProfile(form)
    if (authStore.user) {
      authStore.user.nickname = form.nickname
    }
  } catch {
    // 保存失败时恢复原值
    form.nickname = original
  }
}

function handleLogout() {
  authStore.doLogout()
}
</script>
