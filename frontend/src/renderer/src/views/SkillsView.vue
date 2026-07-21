<template>
  <div class="flex h-full flex-col">
    <div class="flex items-center justify-between border-b border-gray-100 px-6 py-4 dark:border-gray-700">
      <div>
        <h1 class="text-lg font-semibold text-gray-900 dark:text-gray-100">技能中心</h1>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">发现并安装可复用能力，Agent 会在需要时按需加载</p>
      </div>
      <Button variant="outline" size="sm" :disabled="skillsStore.localInstalling" @click="zipFileInput?.click()">
        <Loader2 v-if="skillsStore.localInstalling" :size="15" class="mr-1 animate-spin"/>
        <PackagePlus v-else :size="15" class="mr-1"/>
        从 ZIP 安装
      </Button>
    </div>

    <ScrollArea class="flex-1">
      <div class="mx-auto w-full max-w-5xl px-6 py-6">
        <section class="relative mb-6 overflow-hidden rounded-2xl border border-violet-100 bg-gradient-to-br from-violet-50 via-white to-indigo-50 px-5 py-5 dark:border-violet-900/60 dark:from-violet-950/35 dark:via-gray-900 dark:to-indigo-950/25">
          <div class="pointer-events-none absolute -right-10 -top-12 h-36 w-36 rounded-full bg-violet-200/30 blur-2xl dark:bg-violet-700/10"/>
          <div class="relative flex items-center justify-between gap-6">
            <div class="flex items-start gap-4">
              <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-violet-600 text-white shadow-sm shadow-violet-300 dark:shadow-none">
                <Blocks :size="23"/>
              </div>
              <div>
                <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-100">给 Agent 装上刚好需要的能力</h2>
                <p class="mt-1 max-w-xl text-xs leading-5 text-gray-500 dark:text-gray-400">
                  技能是包含 SKILL.md、参考资料和脚本的能力包。它不会创建另一个 Agent，而是在任务需要时为当前 Agent 提供专业工作流。
                </p>
                <div class="mt-2.5 flex items-center gap-4 text-[11px] text-gray-500 dark:text-gray-400">
                  <span class="flex items-center gap-1"><Search :size="12" class="text-violet-500"/>检索社区</span>
                  <span class="flex items-center gap-1"><Download :size="12" class="text-violet-500"/>安装能力包</span>
                  <span class="flex items-center gap-1"><Sparkles :size="12" class="text-violet-500"/>按需加载</span>
                </div>
              </div>
            </div>
            <button
                class="hidden shrink-0 items-center gap-1 rounded-lg border border-violet-200 bg-white/70 px-3 py-1.5 text-[11px] text-violet-600 transition-colors hover:bg-white md:flex dark:border-violet-800 dark:bg-gray-900/60 dark:text-violet-400"
                @click="openExternal('https://modelscope.cn/skills')"
            >
              ModelScope 技能社区
              <ArrowUpRight :size="12"/>
            </button>
          </div>
        </section>

        <Tabs v-model="activeTab" class="w-full">
          <div class="mb-5 flex items-center justify-between gap-4">
            <TabsList class="h-9">
              <TabsTrigger value="discover" class="gap-1.5 px-4 text-xs">
                <Compass :size="14"/>
                发现技能
              </TabsTrigger>
              <TabsTrigger value="installed" class="gap-1.5 px-4 text-xs">
                <Library :size="14"/>
                已安装
                <span class="ml-0.5 rounded-full bg-gray-200 px-1.5 py-0.5 text-[9px] leading-none dark:bg-gray-700">{{ skillsStore.skills.length }}</span>
              </TabsTrigger>
            </TabsList>

            <div class="relative w-64">
              <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" :size="15"/>
              <Input
                  v-if="activeTab === 'discover'"
                  v-model="skillsStore.marketplaceQuery"
                  placeholder="搜索技能、用途或开发者"
                  class="h-9 rounded-xl pl-8 text-xs"
                  @keydown.enter="skillsStore.searchMarketplace(true)"
              />
              <Input
                  v-else
                  v-model="skillsStore.searchQuery"
                  placeholder="搜索已安装技能"
                  class="h-9 rounded-xl pl-8 text-xs"
              />
            </div>
          </div>

          <TabsContent value="discover" class="mt-0">
            <div class="mb-3 flex items-center justify-between">
              <p class="text-[11px] text-gray-400 dark:text-gray-500">
                来自 ModelScope 的公开技能 · 共 {{ skillsStore.marketplaceTotal.toLocaleString() }} 个结果
              </p>
              <button
                  class="flex items-center gap-1 text-[11px] text-gray-400 transition-colors hover:text-violet-600"
                  :disabled="skillsStore.marketplaceLoading"
                  @click="skillsStore.searchMarketplace(true)"
              >
                <RefreshCw :size="12" :class="skillsStore.marketplaceLoading ? 'animate-spin' : ''"/>
                刷新
              </button>
            </div>

            <div
                v-if="skillsStore.marketplaceError"
                class="mb-4 flex items-center justify-between rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-xs text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-300"
            >
              <span>{{ skillsStore.marketplaceError }}</span>
              <button class="ml-4 shrink-0 font-medium hover:underline" @click="skillsStore.searchMarketplace(true)">重试</button>
            </div>

            <div v-if="skillsStore.marketplaceLoading && skillsStore.marketplaceSkills.length === 0" class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div v-for="index in 6" :key="index" class="h-56 animate-pulse rounded-2xl border border-gray-100 bg-gray-50 dark:border-gray-800 dark:bg-gray-900"/>
            </div>

            <EmptyState
                v-else-if="skillsStore.marketplaceSkills.length === 0 && !skillsStore.marketplaceError"
                :icon="SearchX"
                title="没有找到相关技能"
                description="换一个更宽泛的关键词试试"
            />

            <div v-else class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <MarketplaceSkillCard
                  v-for="skill in skillsStore.marketplaceSkills"
                  :key="skill.id"
                  :skill="skill"
                  :installing="skillsStore.marketplaceInstallingId === skill.id"
                  @open="openMarketplaceDetail(skill)"
                  @install="installMarketplace(skill)"
              />
            </div>

            <div v-if="skillsStore.hasMoreMarketplaceSkills" class="mt-5 flex justify-center">
              <Button
                  variant="outline"
                  size="sm"
                  class="rounded-xl px-5 text-xs"
                  :disabled="skillsStore.marketplaceLoading"
                  @click="skillsStore.loadMoreMarketplace()"
              >
                <Loader2 v-if="skillsStore.marketplaceLoading" :size="13" class="mr-1 animate-spin"/>
                加载更多
              </Button>
            </div>
          </TabsContent>

          <TabsContent value="installed" class="mt-0">
            <div class="mb-3 flex items-center justify-between">
              <p class="text-[11px] text-gray-400 dark:text-gray-500">
                {{ skillsStore.enabledSkills.length }} 个已启用 · 关闭后 Agent 将不再看到该技能
              </p>
              <span v-if="localInstallError" class="text-[11px] text-red-500">{{ localInstallError }}</span>
            </div>

            <div v-if="skillsStore.loading" class="flex items-center justify-center py-20">
              <Loader2 :size="22" class="animate-spin text-violet-500"/>
            </div>

            <EmptyState
                v-else-if="skillsStore.filteredSkills.length === 0"
                :icon="PackageOpen"
                :title="skillsStore.searchQuery ? '没有找到已安装技能' : '还没有安装技能'"
                :description="skillsStore.searchQuery ? '试试其他关键词' : '前往「发现技能」选择需要的能力，或从 ZIP 安装本地技能包'"
                :action-label="skillsStore.searchQuery ? undefined : '发现技能'"
                :action-icon="Compass"
                @action="activeTab = 'discover'"
            />

            <div v-else class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <SkillCard
                  v-for="skill in skillsStore.filteredSkills"
                  :key="skill.id"
                  :skill="skill"
                  @open="selectedInstalledSkill = skill"
                  @delete="requestDelete(skill)"
                  @toggle-enabled="skillsStore.toggleSkillEnabled(skill)"
              />
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </ScrollArea>

    <Dialog :open="!!selectedMarketplaceSkill" @update:open="!$event && (selectedMarketplaceSkill = null)">
      <DialogContent class="max-w-lg">
        <DialogHeader>
          <div class="mb-2 flex items-start gap-3">
            <div class="flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-violet-50 dark:bg-violet-900/25">
              <img v-if="selectedMarketplaceSkill?.logoUrl" :src="selectedMarketplaceSkill.logoUrl" class="h-full w-full object-cover" alt=""/>
              <Blocks v-else :size="20" class="text-violet-500"/>
            </div>
            <div class="min-w-0">
              <DialogTitle class="truncate">{{ selectedMarketplaceSkill?.displayName }}</DialogTitle>
              <DialogDescription class="mt-1 font-mono text-[11px]">{{ selectedMarketplaceSkill?.id }}</DialogDescription>
            </div>
          </div>
        </DialogHeader>
        <p class="whitespace-pre-line text-sm leading-6 text-gray-600 dark:text-gray-300">{{ selectedMarketplaceSkill?.description }}</p>
        <div class="flex flex-wrap gap-1.5">
          <Badge v-for="tag in visibleTags" :key="tag" variant="secondary" class="text-[10px] font-normal">{{ cleanTag(tag) }}</Badge>
        </div>
        <div class="grid grid-cols-2 gap-3 rounded-xl bg-gray-50 p-3 text-xs dark:bg-gray-800/60">
          <div><span class="text-gray-400">开发者</span><p class="mt-0.5 truncate text-gray-700 dark:text-gray-200">{{ marketplaceAuthor }}</p></div>
          <div><span class="text-gray-400">许可证</span><p class="mt-0.5 truncate text-gray-700 dark:text-gray-200">{{ selectedMarketplaceSkill?.license || '未声明' }}</p></div>
          <div><span class="text-gray-400">下载</span><p class="mt-0.5 text-gray-700 dark:text-gray-200">{{ selectedMarketplaceSkill?.downloads.toLocaleString() }}</p></div>
          <div><span class="text-gray-400">更新时间</span><p class="mt-0.5 text-gray-700 dark:text-gray-200">{{ formattedMarketplaceDate }}</p></div>
        </div>
        <DialogFooter class="gap-2">
          <Button variant="outline" @click="openMarketplaceSource(selectedMarketplaceSkill!)">
            查看来源 <ArrowUpRight :size="13" class="ml-1"/>
          </Button>
          <Button
              :disabled="selectedMarketplaceSkill?.installed || !!skillsStore.marketplaceInstallingId"
              @click="installMarketplace(selectedMarketplaceSkill!)"
          >
            <Loader2 v-if="skillsStore.marketplaceInstallingId === selectedMarketplaceSkill?.id" :size="13" class="mr-1 animate-spin"/>
            {{ selectedMarketplaceSkill?.installed ? '已安装' : '安装技能' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!selectedInstalledSkill" @update:open="!$event && (selectedInstalledSkill = null)">
      <DialogContent class="max-w-md">
        <DialogHeader>
          <DialogTitle>{{ selectedInstalledSkill?.name }}</DialogTitle>
          <DialogDescription>{{ installedSourceLabel }}</DialogDescription>
        </DialogHeader>
        <p class="text-sm leading-6 text-gray-600 dark:text-gray-300">{{ selectedInstalledSkill?.description }}</p>
        <div class="flex flex-wrap gap-1.5">
          <Badge v-for="tag in installedVisibleTags" :key="tag" variant="secondary" class="text-[10px] font-normal">{{ cleanTag(tag) }}</Badge>
        </div>
        <div class="flex items-center justify-between rounded-xl bg-gray-50 px-3 py-2.5 dark:bg-gray-800/60">
          <div>
            <p class="text-xs font-medium text-gray-700 dark:text-gray-200">允许 Agent 使用</p>
            <p class="mt-0.5 text-[10px] text-gray-400">技能内容只会在相关任务中加载</p>
          </div>
          <Switch
              v-if="selectedInstalledSkill"
              :model-value="selectedInstalledSkill.enabled"
              @update:model-value="skillsStore.toggleSkillEnabled(selectedInstalledSkill)"
          />
        </div>
      </DialogContent>
    </Dialog>

    <AlertDialog v-model:open="deleteConfirmOpen">
      <AlertDialogContent class="max-w-sm">
        <AlertDialogTitle>卸载这个技能？</AlertDialogTitle>
        <AlertDialogDescription>技能「{{ pendingDeleteSkill?.name }}」的本地文件会被删除；之后仍可从社区重新安装。</AlertDialogDescription>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction class="bg-red-600 hover:bg-red-700" @click="confirmDelete">卸载</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <input ref="zipFileInput" type="file" accept=".zip" class="hidden" @change="onZipFileSelected"/>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {
  ArrowUpRight, Blocks, Compass, Download, Library, Loader2, PackageOpen,
  PackagePlus, RefreshCw, Search, SearchX, Sparkles
} from 'lucide-vue-next'
import {ScrollArea} from '@/components/ui/scroll-area'
import {Input} from '@/components/ui/input'
import {Button} from '@/components/ui/button'
import {Badge} from '@/components/ui/badge'
import {Switch} from '@/components/ui/switch'
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import MarketplaceSkillCard from '@/components/skills/MarketplaceSkillCard.vue'
import SkillCard from '@/components/skills/SkillCard.vue'
import {useSkillsStore} from '@/stores/skills'
import type {MarketplaceSkill, Skill} from '@/types/skill'

const skillsStore = useSkillsStore()
const activeTab = ref<'discover' | 'installed'>('discover')
const selectedMarketplaceSkill = ref<MarketplaceSkill | null>(null)
const selectedInstalledSkill = ref<Skill | null>(null)
const deleteConfirmOpen = ref(false)
const pendingDeleteSkill = ref<Skill | null>(null)
const zipFileInput = ref<HTMLInputElement | null>(null)
const localInstallError = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

const visibleTags = computed(() => selectedMarketplaceSkill.value?.tags.slice(0, 8) ?? [])
const installedVisibleTags = computed(() => selectedInstalledSkill.value?.tags
    .filter(tag => !tag.startsWith('source:'))
    .slice(0, 8) ?? [])
const marketplaceAuthor = computed(() => selectedMarketplaceSkill.value?.developer || selectedMarketplaceSkill.value?.owner || '社区开发者')
const formattedMarketplaceDate = computed(() => formatDate(selectedMarketplaceSkill.value?.lastModified))
const installedSourceLabel = computed(() => {
  const source = selectedInstalledSkill.value?.source
  if (source === 'modelscope') return '从 ModelScope 社区安装'
  if (source === 'local') return '从本地 ZIP 安装'
  if (source === 'legacy') return '旧版兼容指令'
  return 'AgentDesk 内置能力'
})

watch(() => skillsStore.marketplaceQuery, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => skillsStore.searchMarketplace(true), 350)
})

onMounted(() => {
  skillsStore.fetchSkills()
  skillsStore.searchMarketplace(true)
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

function openMarketplaceDetail(skill: MarketplaceSkill) {
  selectedMarketplaceSkill.value = skill
}

async function installMarketplace(skill: MarketplaceSkill) {
  try {
    await skillsStore.installMarketplace(skill)
  } catch {
    // 错误信息由页面顶部统一展示
  }
}

function requestDelete(skill: Skill) {
  pendingDeleteSkill.value = skill
  deleteConfirmOpen.value = true
}

async function confirmDelete() {
  if (!pendingDeleteSkill.value) return
  await skillsStore.deleteSkill(pendingDeleteSkill.value.id)
  deleteConfirmOpen.value = false
  pendingDeleteSkill.value = null
}

async function onZipFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  localInstallError.value = ''
  try {
    await skillsStore.installPackage(file)
    activeTab.value = 'installed'
  } catch (e: any) {
    localInstallError.value = e?.response?.data?.message || e?.message || '本地技能安装失败'
  } finally {
    input.value = ''
  }
}

function openMarketplaceSource(skill: MarketplaceSkill) {
  openExternal(skill.sourceUrl || `https://modelscope.cn/skills/${skill.id}`)
}

function openExternal(url: string) {
  window.electronAPI?.shell.openExternal(url)
}

function cleanTag(tag: string): string {
  const index = tag.indexOf(':')
  return index >= 0 ? tag.substring(index + 1) : tag
}

function formatDate(value?: string): string {
  if (!value) return '未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString('zh-CN')
}
</script>
