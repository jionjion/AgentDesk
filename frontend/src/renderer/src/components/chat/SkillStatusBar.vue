<template>
  <div v-if="skillsStore.enabledSkills.length > 0" class="mb-2">
    <!-- 收起状态栏 -->
    <div
        class="flex items-center border border-gray-200 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800 overflow-hidden cursor-pointer select-none"
        :class="expanded ? 'rounded-b-none' : ''"
        @click="expanded = !expanded"
    >
      <div class="flex items-center gap-1.5 px-3 py-2 text-xs flex-1 min-w-0">
        <Zap :size="13" class="shrink-0 text-amber-500"/>
        <span class="font-medium text-gray-700 dark:text-gray-300">已启用技能</span>
        <span class="font-mono shrink-0 text-gray-500 dark:text-gray-400">
          {{ skillsStore.enabledSkills.length }}
        </span>

        <!-- 折叠时：显示技能名列表 -->
        <template v-if="!expanded">
          <span class="mx-1 text-gray-300 dark:text-gray-600">|</span>
          <span class="text-amber-600 dark:text-amber-400 truncate">
            {{ skillsStore.enabledSkills.map(s => s.name).join(', ') }}
          </span>
        </template>
      </div>

      <ChevronRight
          :size="14"
          class="shrink-0 mr-2 text-gray-400 transition-transform"
          :class="expanded ? 'rotate-90' : ''"
      />
    </div>

    <!-- 展开列表 -->
    <div
        v-if="expanded"
        class="border border-t-0 border-gray-200 dark:border-gray-700 rounded-b-lg bg-white dark:bg-gray-900 max-h-40 overflow-y-auto"
    >
      <div class="py-1">
        <div
            v-for="skill in skillsStore.enabledSkills"
            :key="skill.id"
            class="flex items-center gap-2.5 px-3 py-1.5"
        >
          <Zap :size="14" class="shrink-0 text-amber-500"/>
          <span class="text-xs flex-1 truncate text-gray-700 dark:text-gray-300">{{ skill.name }}</span>
          <span class="text-[10px] text-gray-400 truncate max-w-[150px]">{{ skill.description }}</span>
          <button
              class="shrink-0 text-gray-400 hover:text-red-500 transition-colors"
              @click.stop="skillsStore.toggleSkillEnabled(skill)"
          >
            <X :size="12"/>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue'
import {ChevronRight, X, Zap} from 'lucide-vue-next'
import {useSkillsStore} from '@/stores/skills'

const skillsStore = useSkillsStore()
const expanded = ref(false)
</script>
