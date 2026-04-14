<template>
  <Dialog :open="open" @update:open="$emit('close')">
    <DialogContent class="max-w-lg max-h-[85vh] overflow-y-auto">
      <DialogHeader>
        <DialogTitle>{{ isEdit ? '编辑技能' : '创建技能' }}</DialogTitle>
      </DialogHeader>

      <div class="space-y-4">
        <!-- ID -->
        <div class="space-y-1.5">
          <Label>技能 ID</Label>
          <Input
              v-model="form.id"
              placeholder="skill-id（小写字母、数字、连字符）"
              :disabled="isEdit"
          />
          <p v-if="idError" class="text-xs text-red-500">{{ idError }}</p>
        </div>

        <!-- 名称 -->
        <div class="space-y-1.5">
          <Label>显示名称</Label>
          <Input v-model="form.name" placeholder="如：翻译专家"/>
        </div>

        <!-- 描述 -->
        <div class="space-y-1.5">
          <Label>描述</Label>
          <Input v-model="form.description" placeholder="描述技能用途，Agent 据此判断何时调用"/>
        </div>

        <!-- 分类 -->
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1.5">
            <Label>分类（可选）</Label>
            <Input v-model="form.category" placeholder="如：writing、coding"/>
          </div>
          <div class="space-y-1.5">
            <Label>图标（可选）</Label>
            <Input v-model="form.icon" placeholder="Lucide 图标名"/>
          </div>
        </div>

        <!-- 高级配置 -->
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1.5">
            <Label>最大迭代 (maxIters)</Label>
            <Input v-model.number="form.maxIters" type="number" :min="1" :max="10" placeholder="3"/>
          </div>
          <div class="space-y-1.5">
            <Label>工具（可选）</Label>
            <Input v-model="toolsInput" placeholder="FileTools, CalculateTools"/>
            <p class="text-xs text-muted-foreground">逗号分隔</p>
          </div>
        </div>

        <!-- 系统提示词 -->
        <div class="space-y-1.5">
          <Label>系统提示词</Label>
          <Textarea
              v-model="form.systemPrompt"
              :rows="8"
              placeholder="编写子代理的系统提示词..."
              class="font-mono text-sm"
          />
        </div>
      </div>

      <DialogFooter class="gap-2 mt-4">
        <Button variant="outline" @click="$emit('close')">取消</Button>
        <Button :disabled="!isValid || saving" @click="handleSave">
          <Loader2 v-if="saving" :size="14" class="mr-1 animate-spin"/>
          {{ isEdit ? '保存' : '创建' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {Loader2} from 'lucide-vue-next'
import {Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {Input} from '@/components/ui/input'
import {Textarea} from '@/components/ui/textarea'
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {useSkillsStore} from '@/stores/skills'
import type {Skill, SkillFormData} from '@/types/skill'

const props = defineProps<{
  open: boolean
  skill?: Skill | null
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const skillsStore = useSkillsStore()
const saving = ref(false)
const toolsInput = ref('')

const isEdit = computed(() => !!props.skill)

const form = ref<SkillFormData>({
  id: '',
  name: '',
  description: '',
  systemPrompt: '',
  icon: '',
  category: '',
  maxIters: 3,
  tools: []
})

// 编辑时填充表单
watch(() => props.open, (val) => {
  if (val && props.skill) {
    form.value = {
      id: props.skill.id,
      name: props.skill.name,
      description: props.skill.description,
      systemPrompt: props.skill.systemPrompt,
      icon: props.skill.icon || '',
      category: props.skill.category || '',
      maxIters: props.skill.maxIters,
      tools: props.skill.tools
    }
    toolsInput.value = (props.skill.tools || []).join(', ')
  } else if (val) {
    form.value = {id: '', name: '', description: '', systemPrompt: '', icon: '', category: '', maxIters: 3, tools: []}
    toolsInput.value = ''
  }
})

const ID_REGEX = /^[a-z0-9][a-z0-9-]*$/

const idError = computed(() => {
  if (!form.value.id) return ''
  if (!ID_REGEX.test(form.value.id)) return '仅支持小写字母、数字和连字符，不能以连字符开头'
  if (form.value.id.length > 64) return '最长 64 字符'
  return ''
})

const isValid = computed(() =>
    form.value.id.trim() !== '' &&
    form.value.name.trim() !== '' &&
    form.value.description.trim() !== '' &&
    form.value.systemPrompt.trim() !== '' &&
    !idError.value
)

async function handleSave() {
  if (!isValid.value || saving.value) return
  saving.value = true
  try {
    // 解析工具列表
    const tools = toolsInput.value
        .split(',')
        .map(t => t.trim())
        .filter(Boolean)
    form.value.tools = tools

    await skillsStore.saveSkill(form.value)
    emit('saved')
    emit('close')
  } catch (e) {
    console.error('保存技能失败', e)
  } finally {
    saving.value = false
  }
}
</script>
