<template>
  <Button variant="ghost" size="icon" class="h-8 w-8 relative" @click="$emit('trigger')">
    <Zap :size="16"/>
    <span
        v-if="skillsStore.enabledSkills.length > 0"
        class="absolute -top-0.5 -right-0.5 w-4 h-4 bg-violet-500 text-white text-[10px] font-medium rounded-full flex items-center justify-center"
    >
      {{ skillsStore.enabledSkills.length }}
    </span>
  </Button>
</template>

<script setup lang="ts">
import {onMounted} from 'vue'
import {Zap} from 'lucide-vue-next'
import {Button} from '@/components/ui/button'
import {useSkillsStore} from '@/stores/skills'

defineEmits<{
  trigger: []
}>()

const skillsStore = useSkillsStore()

onMounted(() => {
  if (skillsStore.skills.length === 0) {
    skillsStore.fetchSkills()
  }
})
</script>
