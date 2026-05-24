<template>
  <div class="space-y-6">
    <!-- 内置工具 -->
    <div>
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">内置工具</h3>
      </div>
      <div class="space-y-2">
        <div
            v-for="tool in toolsStore.builtinTools"
            :key="tool.id"
            class="flex items-center justify-between px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800"
        >
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <code class="text-xs font-mono text-violet-600 dark:text-violet-400">tools.{{ tool.name }}</code>
            </div>
            <p class="text-xs text-gray-400 mt-0.5 truncate">{{ tool.description }}</p>
          </div>
          <div class="flex items-center gap-2 ml-3">
            <button
                class="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                @click="viewCode(tool)"
            >
              查看
            </button>
            <Switch
                :checked="tool.enabled"
                @update:checked="(v: boolean) => toolsStore.toggleTool(tool.id, v)"
                class="scale-75"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 自定义工具 -->
    <div>
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">自定义工具</h3>
        <div class="flex gap-2">
          <button
              class="text-xs px-2 py-1 rounded text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700"
              @click="handleImport"
          >
            导入
          </button>
          <button
              class="text-xs px-2 py-1 rounded bg-violet-50 dark:bg-violet-900/30 text-violet-700 dark:text-violet-400 hover:bg-violet-100 dark:hover:bg-violet-900/50"
              @click="showAddDialog = true"
          >
            + 添加工具
          </button>
        </div>
      </div>

      <div v-if="toolsStore.customTools.length === 0" class="text-center py-6 text-xs text-gray-400">
        暂无自定义工具
      </div>

      <div v-else class="space-y-2">
        <div
            v-for="tool in toolsStore.customTools"
            :key="tool.id"
            class="group flex items-center justify-between px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800"
        >
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <code class="text-xs font-mono text-violet-600 dark:text-violet-400">tools.{{ tool.name }}</code>
            </div>
            <p class="text-xs text-gray-400 mt-0.5 truncate">{{ tool.description }}</p>
          </div>
          <div class="flex items-center gap-2 ml-3">
            <button
                class="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 opacity-0 group-hover:opacity-100"
                @click="handleExport(tool)"
            >
              导出
            </button>
            <button
                class="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 opacity-0 group-hover:opacity-100"
                @click="editTool(tool)"
            >
              编辑
            </button>
            <button
                class="text-xs text-red-400 hover:text-red-600 opacity-0 group-hover:opacity-100"
                @click="confirmDelete(tool)"
            >
              删除
            </button>
            <Switch
                :checked="tool.enabled"
                @update:checked="(v: boolean) => toolsStore.toggleTool(tool.id, v)"
                class="scale-75"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 查看代码对话框 -->
    <Dialog v-model:open="showCodeDialog">
      <DialogContent class="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ viewingTool?.name }}</DialogTitle>
          <DialogDescription>{{ viewingTool?.signature }}</DialogDescription>
        </DialogHeader>
        <pre class="text-xs font-mono bg-gray-50 dark:bg-gray-900 p-3 rounded-lg overflow-auto max-h-80">{{ viewingTool?.code.trim() }}</pre>
      </DialogContent>
    </Dialog>

    <!-- 添加/编辑对话框 -->
    <Dialog v-model:open="showAddDialog">
      <DialogContent class="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ editingTool ? '编辑工具' : '添加自定义工具' }}</DialogTitle>
        </DialogHeader>
        <div class="space-y-3">
          <div>
            <label class="text-xs text-gray-500 mb-1 block">函数名</label>
            <Input v-model="form.name" placeholder="my_tool" class="font-mono text-sm"/>
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">描述</label>
            <Input v-model="form.description" placeholder="这个工具的用途"/>
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">函数签名</label>
            <Input v-model="form.signature" placeholder="my_tool(path: str) -> dict" class="font-mono text-sm"/>
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">依赖包（逗号分隔）</label>
            <Input v-model="depsInput" placeholder="pypdf, pandas"/>
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">Python 代码</label>
            <Textarea
                v-model="form.code"
                placeholder="def my_tool(path: str) -> dict:&#10;    ..."
                class="font-mono text-xs min-h-[200px]"
            />
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-4">
          <button
              class="px-3 py-1.5 text-xs rounded text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700"
              @click="showAddDialog = false"
          >
            取消
          </button>
          <button
              class="px-3 py-1.5 text-xs rounded bg-violet-600 text-white hover:bg-violet-700"
              @click="handleSave"
          >
            保存
          </button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useSandboxToolsStore } from '@/stores/sandbox-tools'
import type { SandboxTool } from '@/types/sandbox-tools'
import { Switch } from '@/components/ui/switch'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'

const toolsStore = useSandboxToolsStore()

// 查看代码
const showCodeDialog = ref(false)
const viewingTool = ref<SandboxTool | null>(null)

function viewCode(tool: SandboxTool) {
  viewingTool.value = tool
  showCodeDialog.value = true
}

// 添加/编辑
const showAddDialog = ref(false)
const editingTool = ref<SandboxTool | null>(null)
const depsInput = ref('')
const form = reactive({
  name: '',
  description: '',
  signature: '',
  code: ''
})

function resetForm() {
  form.name = ''
  form.description = ''
  form.signature = ''
  form.code = ''
  depsInput.value = ''
  editingTool.value = null
}

function editTool(tool: SandboxTool) {
  editingTool.value = tool
  form.name = tool.name
  form.description = tool.description
  form.signature = tool.signature
  form.code = tool.code
  depsInput.value = tool.dependencies.join(', ')
  showAddDialog.value = true
}

function handleSave() {
  if (!form.name || !form.code) return
  const deps = depsInput.value.split(',').map(s => s.trim()).filter(Boolean)

  if (editingTool.value) {
    toolsStore.updateTool(editingTool.value.id, {
      name: form.name,
      description: form.description,
      signature: form.signature,
      code: form.code,
      dependencies: deps
    })
  } else {
    toolsStore.addTool({
      name: form.name,
      description: form.description,
      signature: form.signature || `${form.name}()`,
      code: form.code,
      dependencies: deps
    })
  }

  showAddDialog.value = false
  resetForm()
}

// 删除
function confirmDelete(tool: SandboxTool) {
  if (confirm(`确定删除工具 "${tool.name}" 吗？`)) {
    toolsStore.removeTool(tool.id)
  }
}

// 导出
function handleExport(tool: SandboxTool) {
  const json = toolsStore.exportTool(tool.id)
  if (!json) return
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${tool.name}.json`
  a.click()
  URL.revokeObjectURL(url)
}

// 导入
async function handleImport() {
  const filePaths = await window.electronAPI.dialog.openFile()
  if (!filePaths || filePaths.length === 0) return
  const data = await window.electronAPI.fs.readFile(filePaths[0])
  const text = new TextDecoder().decode(data)
  const ok = toolsStore.importTool(text)
  if (!ok) {
    alert('导入失败：JSON 格式不正确')
  }
}
</script>
