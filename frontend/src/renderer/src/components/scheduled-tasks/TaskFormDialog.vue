<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <DialogContent class="max-w-lg max-h-[85vh] overflow-y-auto !flex flex-col !gap-3 !p-5">
      <DialogHeader>
        <DialogTitle>{{ isEdit ? '编辑定时任务' : '新建定时任务' }}</DialogTitle>
        <DialogDescription>
          {{ isEdit ? '修改定时任务的配置信息' : '创建一个按计划自动执行的任务' }}
        </DialogDescription>
      </DialogHeader>

      <Tabs v-model="activeTab" class="w-full">
        <TabsList class="w-full grid grid-cols-2">
          <TabsTrigger value="content">
            内容
            <span v-if="submitted && !contentValid" class="ml-1.5 inline-block h-1.5 w-1.5 rounded-full bg-destructive"/>
          </TabsTrigger>
          <TabsTrigger value="schedule">
            时间
            <span v-if="submitted && !scheduleValid" class="ml-1.5 inline-block h-1.5 w-1.5 rounded-full bg-destructive"/>
          </TabsTrigger>
        </TabsList>

        <!-- 内容页：名称 + 描述 + 提示词 + 关联技能 -->
        <TabsContent value="content" class="space-y-3 mt-3">
          <!-- 名称 -->
          <div class="space-y-1">
            <Label>
              任务名称
              <span class="text-destructive">*</span>
            </Label>
            <Input
                v-model="form.name"
                placeholder="如：每周竞品动态追踪"
                :class="{ 'border-destructive': submitted && !form.name.trim() }"
            />
            <p v-if="submitted && !form.name.trim()" class="text-xs text-destructive">请输入任务名称</p>
          </div>

          <!-- 描述 -->
          <div class="space-y-1">
            <Label>描述（可选）</Label>
            <Input v-model="form.description" placeholder="简要说明这个任务做什么"/>
          </div>

          <!-- 提示词 -->
          <div class="space-y-1">
            <Label>
              提示词
              <span class="text-destructive">*</span>
            </Label>
            <Textarea
                v-model="form.prompt"
                :rows="12"
                placeholder="输入发送给 Agent 的消息，例如：&#10;&#10;请帮我搜索最近一周的行业新闻，整理成摘要报告..."
                :class="{ 'border-destructive': submitted && !form.prompt.trim() }"
            />
            <p v-if="submitted && !form.prompt.trim()" class="text-xs text-destructive">请输入提示词</p>
          </div>

          <!-- 关联技能 -->
          <div class="space-y-1">
            <Label>关联技能（可选）</Label>
            <Select v-model="skillIdSelect">
              <SelectTrigger>
                <SelectValue placeholder="不关联技能"/>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__none__">不关联技能</SelectItem>
                <SelectItem v-for="skill in skills" :key="skill.id" :value="skill.id">
                  {{ skill.name }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </TabsContent>

        <!-- 时间页：执行计划 -->
        <TabsContent value="schedule" class="space-y-3 mt-3">
          <!-- 频率选择 Tabs -->
          <Tabs :default-value="selectedPreset" @update:model-value="handlePresetChange">
            <TabsList class="w-full grid grid-cols-4">
              <TabsTrigger value="每天">每天</TabsTrigger>
              <TabsTrigger value="工作日">工作日</TabsTrigger>
              <TabsTrigger value="每周">每周</TabsTrigger>
              <TabsTrigger value="自定义">自定义</TabsTrigger>
            </TabsList>
          </Tabs>

          <!-- 时间和星期选择 -->
          <div v-if="selectedPreset !== '自定义'" class="flex items-end gap-3">
            <div class="space-y-1">
              <Label class="text-xs text-muted-foreground">执行时间</Label>
              <input
                  type="time"
                  :value="`${scheduleHour}:${scheduleMinute}`"
                  @input="onTimeChange(($event.target as HTMLInputElement).value)"
                  class="flex h-10 w-[110px] rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              />
            </div>
            <div v-if="selectedPreset === '每周'" class="flex-1 space-y-1">
              <Label class="text-xs text-muted-foreground">星期</Label>
              <Select v-model="weekDay" @update:model-value="updateCron">
                <SelectTrigger>
                  <SelectValue/>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="(label, key) in weekDayLabels" :key="key" :value="key">
                    {{ label }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <!-- 自定义 Cron -->
          <div v-if="selectedPreset === '自定义'" class="space-y-2">
            <Label class="text-xs text-muted-foreground">Cron 表达式（6字段）</Label>
            <div class="flex items-end gap-2">
              <div v-for="(field, idx) in cronFields" :key="idx" class="flex-1 space-y-1">
                <span class="block text-center text-[10px] text-muted-foreground">{{ field.label }}</span>
                <input
                    :ref="(el) => cronInputRefs[idx] = el as HTMLInputElement"
                    v-model="field.value"
                    class="w-full h-10 text-center font-mono text-sm border rounded-md bg-background focus:outline-none focus:ring-2 focus:ring-ring focus:border-transparent"
                    :class="{ 'border-destructive': submitted && !field.value.trim() }"
                    :placeholder="field.placeholder"
                    @input="onCronFieldInput(idx)"
                    @keydown.delete="onCronFieldBackspace(idx, $event)"
                />
              </div>
            </div>
            <p v-if="submitted && cronFields.some(f => !f.value.trim())" class="text-xs text-destructive">请填写完整的 Cron 表达式</p>
            <div class="rounded-md bg-muted/50 p-2 text-xs text-muted-foreground space-y-0.5">
              <p class="font-medium">常用示例：</p>
              <p><code class="bg-muted px-1 rounded">0 0 9 * * *</code> — 每天 09:00</p>
              <p><code class="bg-muted px-1 rounded">0 0 9 * * MON-FRI</code> — 工作日 09:00</p>
              <p><code class="bg-muted px-1 rounded">0 0 */2 * * *</code> — 每 2 小时</p>
            </div>
          </div>

          <!-- 执行计划摘要 -->
          <div v-if="scheduleSummary" class="flex items-center gap-2 rounded-md bg-primary/5 border border-primary/10 px-3 py-1.5">
            <CalendarClock :size="14" class="text-primary shrink-0"/>
            <span class="text-sm text-primary">{{ scheduleSummary }}</span>
          </div>
        </TabsContent>
      </Tabs>

      <DialogFooter class="gap-2 mt-2">
        <Button variant="outline" size="sm" @click="handleClose">取消</Button>
        <Button size="sm" :disabled="saving" @click="handleSave">
          <Loader2 v-if="saving" :size="14" class="mr-1 animate-spin"/>
          {{ isEdit ? '保存' : '创建' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue'
import {CalendarClock, Loader2} from 'lucide-vue-next'
import {Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {Input} from '@/components/ui/input'
import {Textarea} from '@/components/ui/textarea'
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {useScheduledTasksStore} from '@/stores/scheduledTasks'
import {useSkillsStore} from '@/stores/skills'
import type {ScheduledTask, ScheduledTaskFormData} from '@/types/scheduledTask'

const props = defineProps<{
  open: boolean
  task?: ScheduledTask | null
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const store = useScheduledTasksStore()
const skillsStore = useSkillsStore()
const saving = ref(false)
const submitted = ref(false)

const isEdit = computed(() => !!props.task)
const skills = computed(() => skillsStore.skills)

const weekDayLabels: Record<string, string> = {
  MON: '周一', TUE: '周二', WED: '周三', THU: '周四', FRI: '周五', SAT: '周六', SUN: '周日'
}

const presets = [
  {label: '每天', cronTemplate: (h: string, m: string) => `0 ${m} ${h} * * *`},
  {label: '工作日', cronTemplate: (h: string, m: string) => `0 ${m} ${h} * * MON-FRI`},
  {label: '每周', cronTemplate: (h: string, m: string, d: string) => `0 ${m} ${h} * * ${d}`},
  {label: '自定义', cronTemplate: () => ''}
]

const selectedPreset = ref('每天')
const scheduleHour = ref('09')
const scheduleMinute = ref('30')
const weekDay = ref('MON')
const activeTab = ref<'content' | 'schedule'>('content')

function onTimeChange(val: string | number) {
  const s = String(val ?? '09:30')
  const [h, m] = s.split(':')
  scheduleHour.value = (h || '09').padStart(2, '0')
  scheduleMinute.value = (m || '30').padStart(2, '0')
  updateCron()
}

function handleClose() {
  emit('close')
}

function handleOpenChange(val: boolean) {
  if (!val) emit('close')
}

const SKILL_NONE = '__none__'
const skillIdSelect = computed({
  get: () => form.value.skillId || SKILL_NONE,
  set: (v: string) => { form.value.skillId = v === SKILL_NONE ? '' : v }
})

// 自定义 Cron 方块输入
const cronFields = reactive([
  {label: '秒', value: '0', placeholder: '0'},
  {label: '分', value: '0', placeholder: '30'},
  {label: '时', value: '9', placeholder: '9'},
  {label: '日', value: '*', placeholder: '*'},
  {label: '月', value: '*', placeholder: '*'},
  {label: '周', value: '*', placeholder: '*'}
])
const cronInputRefs = ref<(HTMLInputElement | null)[]>([])

function onCronFieldInput(idx: number) {
  syncCronFieldsToForm()
  // 输入空格时自动跳到下一个字段
  const val = cronFields[idx].value
  if (val.endsWith(' ')) {
    cronFields[idx].value = val.trimEnd()
    if (idx < cronFields.length - 1) {
      cronInputRefs.value[idx + 1]?.focus()
    }
  }
}

function onCronFieldBackspace(idx: number, e: KeyboardEvent) {
  if (cronFields[idx].value === '' && idx > 0) {
    e.preventDefault()
    cronInputRefs.value[idx - 1]?.focus()
  }
}

function syncCronFieldsToForm() {
  form.value.cronExpression = cronFields.map(f => f.value || '*').join(' ')
}

function syncFormToCronFields() {
  const parts = form.value.cronExpression.trim().split(/\s+/)
  cronFields.forEach((f, i) => {
    f.value = parts[i] || '*'
  })
}

const form = ref<ScheduledTaskFormData>({
  name: '',
  description: '',
  prompt: '',
  cronExpression: '0 30 9 * * *',
  scheduleLabel: '每天 09:30',
  skillId: ''
})

// 执行计划摘要
const scheduleSummary = computed(() => {
  if (selectedPreset.value === '自定义') {
    return form.value.cronExpression ? `Cron: ${form.value.cronExpression}` : ''
  }
  const time = `${scheduleHour.value}:${scheduleMinute.value}`
  if (selectedPreset.value === '每天') {
    return `每天 ${time} 执行`
  } else if (selectedPreset.value === '工作日') {
    return `工作日（周一至周五）${time} 执行`
  } else if (selectedPreset.value === '每周') {
    return `每${weekDayLabels[weekDay.value]} ${time} 执行`
  }
  return ''
})

function handlePresetChange(value: string | number) {
  selectedPreset.value = value as string
  if (value === '自定义') {
    syncFormToCronFields()
  } else {
    updateCron()
  }
}

function updateCron() {
  const h = scheduleHour.value
  const m = scheduleMinute.value
  const time = `${h}:${m}`
  const preset = presets.find(p => p.label === selectedPreset.value)
  if (!preset || selectedPreset.value === '自定义') return

  form.value.cronExpression = preset.cronTemplate(h, m, weekDay.value)

  if (selectedPreset.value === '每天') {
    form.value.scheduleLabel = `每天 ${time}`
  } else if (selectedPreset.value === '工作日') {
    form.value.scheduleLabel = `工作日 ${time}`
  } else if (selectedPreset.value === '每周') {
    form.value.scheduleLabel = `每${weekDayLabels[weekDay.value]} ${time}`
  }
}

// 编辑时填充表单
watch(() => props.open, (val) => {
  submitted.value = false
  if (val) {
    activeTab.value = 'content'
    // 确保技能列表已加载
    if (skillsStore.skills.length === 0) {
      skillsStore.fetchSkills()
    }
  }
  if (val && props.task) {
    form.value = {
      name: props.task.name,
      description: props.task.description || '',
      prompt: props.task.prompt,
      cronExpression: props.task.cronExpression,
      scheduleLabel: props.task.scheduleLabel || '',
      skillId: props.task.skillId || ''
    }
    // 尝试从 scheduleLabel 恢复预设
    const label = props.task.scheduleLabel || ''
    if (label.startsWith('每天')) {
      selectedPreset.value = '每天'
      const timeMatch = label.match(/(\d{2}):(\d{2})/)
      if (timeMatch) { scheduleHour.value = timeMatch[1]; scheduleMinute.value = timeMatch[2] }
    } else if (label.startsWith('工作日')) {
      selectedPreset.value = '工作日'
      const timeMatch = label.match(/(\d{2}):(\d{2})/)
      if (timeMatch) { scheduleHour.value = timeMatch[1]; scheduleMinute.value = timeMatch[2] }
    } else if (label.startsWith('每周') || label.startsWith('每周')) {
      selectedPreset.value = '每周'
      const timeMatch = label.match(/(\d{2}):(\d{2})/)
      if (timeMatch) { scheduleHour.value = timeMatch[1]; scheduleMinute.value = timeMatch[2] }
      // 尝试恢复星期
      for (const [key, val] of Object.entries(weekDayLabels)) {
        if (label.includes(val)) {
          weekDay.value = key
          break
        }
      }
    } else {
      selectedPreset.value = '自定义'
      syncFormToCronFields()
    }
  } else if (val) {
    form.value = {name: '', description: '', prompt: '', cronExpression: '0 30 9 * * *', scheduleLabel: '每天 09:30', skillId: ''}
    selectedPreset.value = '每天'
    scheduleHour.value = '09'
    scheduleMinute.value = '30'
    weekDay.value = 'MON'
  }
})

const contentValid = computed(() =>
    form.value.name.trim() !== '' &&
    form.value.prompt.trim() !== ''
)

const scheduleValid = computed(() =>
    form.value.cronExpression.trim() !== '' &&
    (selectedPreset.value !== '自定义' || cronFields.every(f => f.value.trim() !== ''))
)

const isValid = computed(() => contentValid.value && scheduleValid.value)

async function handleSave() {
  submitted.value = true
  if (!isValid.value) {
    // 跳到第一个有错的页签
    if (!contentValid.value) activeTab.value = 'content'
    else if (!scheduleValid.value) activeTab.value = 'schedule'
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    if (isEdit.value && props.task) {
      await store.updateTask(props.task.id, form.value)
    } else {
      await store.createTask(form.value)
    }
    emit('saved')
    emit('close')
  } catch (e) {
    console.error('保存定时任务失败', e)
  } finally {
    saving.value = false
  }
}
</script>
