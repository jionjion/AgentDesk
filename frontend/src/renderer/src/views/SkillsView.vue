<template>
  <div class="h-full flex flex-col">
    <!-- 顶部工具栏 -->
    <div class="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-700">
      <div class="flex items-center gap-3">
        <div class="relative w-60">
          <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" :size="16"/>
          <Input v-model="skillsStore.searchQuery" placeholder="搜索技能" class="pl-8"/>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <Button variant="outline" @click="handleImport">
          <Upload :size="16" class="mr-1"/>
          导入技能
        </Button>
        <Button @click="openCreateDialog">
          <Plus :size="16" class="mr-1"/>
          创建技能
        </Button>
      </div>
    </div>

    <!-- 内容区 -->
    <ScrollArea class="flex-1">
      <div class="px-6 py-6 max-w-5xl">
        <h1 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">技能</h1>
        <p class="text-sm text-gray-500 dark:text-gray-400 mb-6 font-normal">管理技能，在对话中扩展 Agent 的能力。启用的技能会自动注册为子代理。</p>

        <!-- 分类过滤 -->
        <div class="flex items-center gap-2 mb-4 flex-wrap">
          <Badge
              :variant="!skillsStore.selectedCategory ? 'default' : 'secondary'"
              class="cursor-pointer"
              @click="skillsStore.selectedCategory = null"
          >
            全部
          </Badge>
          <Badge
              v-for="cat in skillsStore.categories"
              :key="cat"
              :variant="skillsStore.selectedCategory === cat ? 'default' : 'secondary'"
              class="cursor-pointer"
              @click="skillsStore.selectedCategory = skillsStore.selectedCategory === cat ? null : cat"
          >
            {{ cat }}
          </Badge>
        </div>

        <!-- 技能卡片网格 / 空状态 -->
        <div v-if="skillsStore.loading" class="flex items-center justify-center py-16">
          <Loader2 :size="24" class="animate-spin text-gray-400"/>
        </div>
        <EmptyState
            v-else-if="skillsStore.filteredSkills.length === 0"
            :icon="Sparkles"
            title="还没有技能"
            :description="skillsStore.searchQuery ? '未找到匹配的技能' : '创建你的第一个技能，扩展 Agent 能力'"
            action-label="创建技能"
            :action-icon="Plus"
            @action="openCreateDialog"
        />
        <div v-else class="grid grid-cols-3 gap-4">
          <SkillCard
              v-for="skill in skillsStore.filteredSkills"
              :key="skill.id"
              :skill="skill"
              @edit="openEditDialog(skill)"
              @delete="handleDelete(skill)"
              @toggle-enabled="skillsStore.toggleSkillEnabled(skill)"
          />
        </div>
      </div>
    </ScrollArea>

    <!-- 创建/编辑对话框 -->
    <SkillFormDialog
        :open="formDialogOpen"
        :skill="editingSkill"
        @close="formDialogOpen = false"
        @saved="formDialogOpen = false"
    />

    <!-- 删除确认 -->
    <AlertDialog v-model:open="deleteConfirmOpen">
      <AlertDialogContent class="max-w-sm">
        <AlertDialogTitle>确认删除</AlertDialogTitle>
        <AlertDialogDescription>删除后无法恢复，确定要删除技能「{{ pendingDeleteSkill?.name }}」吗？</AlertDialogDescription>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction @click="confirmDelete">删除</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {Loader2, Plus, Search, Sparkles, Upload} from 'lucide-vue-next'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Input} from '@/components/ui/input'
import {Button} from '@/components/ui/button'
import {Badge} from '@/components/ui/badge'
import {AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import SkillCard from '@/components/skills/SkillCard.vue'
import SkillFormDialog from '@/components/skills/SkillFormDialog.vue'
import {useSkillsStore} from '@/stores/skills'
import type {Skill} from '@/types/skill'

const skillsStore = useSkillsStore()

const formDialogOpen = ref(false)
const editingSkill = ref<Skill | null>(null)
const deleteConfirmOpen = ref(false)
const pendingDeleteSkill = ref<Skill | null>(null)

function openCreateDialog() {
  editingSkill.value = null
  formDialogOpen.value = true
}

function openEditDialog(skill: Skill) {
  editingSkill.value = skill
  formDialogOpen.value = true
}

function handleDelete(skill: Skill) {
  pendingDeleteSkill.value = skill
  deleteConfirmOpen.value = true
}

async function confirmDelete() {
  if (!pendingDeleteSkill.value) return
  try {
    await skillsStore.deleteSkill(pendingDeleteSkill.value.id)
  } catch (e) {
    console.error('删除技能失败', e)
  }
  deleteConfirmOpen.value = false
  pendingDeleteSkill.value = null
}

async function handleImport() {
  try {
    const filePaths = await window.electronAPI.dialog.openFile()
    if (!filePaths || filePaths.length === 0) return
    const data = await window.electronAPI.fs.readFile(filePaths[0])
    const text = new TextDecoder().decode(data)
    const imported = JSON.parse(text)
    if (imported.id && imported.name && imported.description && imported.systemPrompt) {
      await skillsStore.saveSkill(imported)
    }
  } catch (e) {
    console.error('导入技能失败', e)
  }
}

onMounted(() => {
  skillsStore.fetchSkills()
})
</script>
