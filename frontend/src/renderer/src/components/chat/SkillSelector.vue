<template>
  <Popover v-model:open="popoverOpen">
    <PopoverTrigger as-child>
      <Button variant="ghost" size="icon" class="h-8 w-8 relative">
        <Zap :size="16"/>
        <span
            v-if="skillsStore.enabledSkills.length > 0"
            class="absolute -top-0.5 -right-0.5 w-4 h-4 bg-violet-500 text-white text-[10px] font-medium rounded-full flex items-center justify-center"
        >
          {{ skillsStore.enabledSkills.length }}
        </span>
      </Button>
    </PopoverTrigger>
    <PopoverContent align="start" class="w-72 p-0">
      <!-- 搜索 -->
      <div class="p-2 border-b border-gray-100 dark:border-gray-700">
        <div class="relative">
          <Search class="absolute left-2 top-1/2 -translate-y-1/2 text-muted-foreground" :size="14"/>
          <Input v-model="query" placeholder="搜索技能..." class="pl-7 h-8 text-xs"/>
        </div>
      </div>

      <!-- 技能列表 -->
      <ScrollArea class="max-h-64">
        <div v-if="filtered.length === 0" class="px-3 py-4 text-center text-xs text-gray-400">
          {{ skillsStore.skills.length === 0 ? '暂无技能，请先创建' : '未找到匹配技能' }}
        </div>
        <div v-else class="p-1">
          <div
              v-for="skill in filtered"
              :key="skill.id"
              class="flex items-center gap-2.5 px-2.5 py-2 rounded-md cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              @click="skillsStore.toggleSkillEnabled(skill)"
          >
            <div
                class="w-4 h-4 rounded border flex items-center justify-center shrink-0"
                :class="skill.enabled
                  ? 'bg-violet-500 border-violet-500'
                  : 'border-gray-300 dark:border-gray-600'"
            >
              <Check v-if="skill.enabled" :size="10" class="text-white"/>
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-xs font-medium text-gray-800 dark:text-gray-200 truncate">{{ skill.name }}</div>
              <div class="text-[10px] text-gray-400 truncate">{{ skill.description }}</div>
            </div>
          </div>
        </div>
      </ScrollArea>
    </PopoverContent>
  </Popover>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {Check, Search, Zap} from 'lucide-vue-next'
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover'
import {Button} from '@/components/ui/button'
import {Input} from '@/components/ui/input'
import {ScrollArea} from '@/components/ui/scroll-area'
import {useSkillsStore} from '@/stores/skills'

const skillsStore = useSkillsStore()
const popoverOpen = ref(false)
const query = ref('')

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return skillsStore.skills
  return skillsStore.skills.filter(s =>
      s.name.toLowerCase().includes(q) || s.description.toLowerCase().includes(q)
  )
})

onMounted(() => {
  if (skillsStore.skills.length === 0) {
    skillsStore.fetchSkills()
  }
})
</script>
