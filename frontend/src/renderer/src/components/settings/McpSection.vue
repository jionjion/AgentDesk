<template>
  <div class="space-y-6">
    <!-- 头部: 说明 + 添加按钮 -->
    <div class="flex items-center justify-between">
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">
          连接外部 MCP 服务器，扩展 AI 助手的工具能力
        </p>
      </div>
      <Button size="sm" @click="openCreateDialog">
        <Plus :size="14" class="mr-1"/>
        添加
      </Button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="py-8 text-center">
      <Loader2 :size="20" class="animate-spin text-gray-400 mx-auto"/>
    </div>

    <!-- 空状态 -->
    <div
        v-else-if="servers.length === 0"
        class="py-12 text-center text-sm text-gray-400 dark:text-gray-500"
    >
      <Cpu :size="28" class="mx-auto mb-2 text-gray-300 dark:text-gray-600"/>
      <p>暂无 MCP 服务器配置</p>
      <p class="mt-1 text-xs">点击「添加」连接你的第一个 MCP 服务器</p>
    </div>

    <!-- 服务器列表 -->
    <div v-else class="rounded-lg border border-gray-200 dark:border-gray-700 divide-y divide-gray-100 dark:divide-gray-800">
      <div
          v-for="server in servers"
          :key="server.id"
          class="group relative px-4 py-3 hover:bg-gray-50/80 dark:hover:bg-gray-800/40 transition-colors"
      >
        <div class="flex items-center justify-between">
          <!-- 左侧信息 -->
          <div class="flex-1 min-w-0 pr-4">
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{{ server.name }}</span>
              <Badge :variant="server.type === 'sse' ? 'info' : 'secondary'" class="text-[10px] px-1.5 py-0">
                {{ server.type === 'sse' ? 'SSE' : 'StdIO' }}
              </Badge>
            </div>
            <p v-if="server.description" class="text-xs text-gray-500 dark:text-gray-400 mt-0.5 truncate">
              {{ server.description }}
            </p>
            <p class="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">
              {{ server.type === 'sse' ? (server.config as McpSseConfig).url : (server.config as McpStdioConfig).command.join(' ') }}
            </p>
            <!-- 测试结果 -->
            <div class="mt-1.5 min-h-4">
              <template v-if="testResults[server.id]">
                <div v-if="testResults[server.id].success">
                  <div class="flex items-center gap-1 text-[11px] text-green-600 dark:text-green-400">
                    <CheckCircle :size="12"/>
                    <span>连接成功 ({{ testResults[server.id].availableTools.length }} 个工具, {{ testResults[server.id].latencyMs }}ms)</span>
                  </div>
                  <div v-if="testResults[server.id].availableTools.length" class="flex flex-wrap gap-1 mt-1">
                    <Badge
                        v-for="tool in testResults[server.id].availableTools"
                        :key="tool"
                        variant="secondary"
                        class="text-[10px] px-1.5 py-0 font-mono"
                    >
                      {{ tool }}
                    </Badge>
                  </div>
                </div>
                <div v-else class="flex items-center gap-1 text-[11px] text-red-500">
                  <XCircle :size="12"/>
                  <span class="truncate">{{ testResults[server.id].message }}</span>
                </div>
              </template>
            </div>
          </div>

          <!-- 右侧操作 -->
          <div class="flex items-center gap-2">
            <Switch
                :checked="server.enabled"
                @update:checked="handleToggleEnabled(server)"
            />
            <div class="flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                  class="p-1.5 rounded-md text-gray-400 hover:text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
                  title="测试连接"
                  :disabled="testingId === server.id"
                  @click="handleTest(server)"
              >
                <Loader2 v-if="testingId === server.id" :size="13" class="animate-spin"/>
                <Wifi v-else :size="13"/>
              </button>
              <button
                  class="p-1.5 rounded-md text-gray-400 hover:text-violet-500 hover:bg-violet-50 dark:hover:bg-violet-900/20 transition-colors"
                  title="编辑"
                  @click="openEditDialog(server)"
              >
                <Pencil :size="13"/>
              </button>
              <button
                  class="p-1.5 rounded-md text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                  title="删除"
                  @click="confirmDelete(server)"
              >
                <Trash2 :size="13"/>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 添加/编辑弹窗 -->
    <Dialog :open="dialogOpen" @update:open="dialogOpen = $event">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ editingServer ? '编辑 MCP 服务器' : '添加 MCP 服务器' }}</DialogTitle>
          <DialogDescription>配置 MCP 服务器的连接信息</DialogDescription>
        </DialogHeader>

        <div class="space-y-4 py-2">
          <!-- 名称 -->
          <div class="space-y-1.5">
            <Label>名称</Label>
            <Input v-model="form.name" placeholder="例如: my-mcp-server"/>
          </div>

          <!-- 描述 -->
          <div class="space-y-1.5">
            <Label>描述 (可选)</Label>
            <Input v-model="form.description" placeholder="简短描述该服务器的功能"/>
          </div>

          <!-- 类型选择 -->
          <div class="space-y-1.5">
            <Label>传输类型</Label>
            <Select v-model="form.type" @update:model-value="onTypeChange">
              <SelectTrigger>
                <SelectValue placeholder="选择传输类型"/>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="sse">SSE (远程服务器)</SelectItem>
                <SelectItem value="stdio">StdIO (本地进程)</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <!-- SSE 配置 -->
          <template v-if="form.type === 'sse'">
            <div class="space-y-1.5">
              <Label>服务器 URL</Label>
              <Input v-model="sseUrl" placeholder="http://127.0.0.1:8001/sse"/>
            </div>
            <!-- Headers -->
            <div class="space-y-1.5">
              <div class="flex items-center justify-between">
                <Label>Headers (可选)</Label>
                <button
                    class="text-xs text-violet-500 hover:text-violet-600 transition-colors"
                    @click="addHeader"
                >
                  + 添加
                </button>
              </div>
              <div v-for="(h, idx) in sseHeaders" :key="idx" class="flex gap-2">
                <Input v-model="h.key" placeholder="Key" class="flex-1"/>
                <Input v-model="h.value" placeholder="Value" class="flex-1"/>
                <button
                    class="p-1.5 text-gray-400 hover:text-red-500 transition-colors"
                    @click="sseHeaders.splice(idx, 1)"
                >
                  <X :size="14"/>
                </button>
              </div>
            </div>
          </template>

          <!-- StdIO 配置 -->
          <template v-if="form.type === 'stdio'">
            <div class="space-y-1.5">
              <Label>启动命令</Label>
              <Input v-model="stdioCommand" placeholder="npx -y @modelcontextprotocol/server-filesystem /tmp"/>
              <p class="text-[10px] text-gray-400">用空格分隔命令和参数</p>
            </div>
            <!-- 环境变量 -->
            <div class="space-y-1.5">
              <div class="flex items-center justify-between">
                <Label>环境变量 (可选)</Label>
                <button
                    class="text-xs text-violet-500 hover:text-violet-600 transition-colors"
                    @click="addEnvVar"
                >
                  + 添加
                </button>
              </div>
              <div v-for="(e, idx) in stdioEnv" :key="idx" class="flex gap-2">
                <Input v-model="e.key" placeholder="Key" class="flex-1"/>
                <Input v-model="e.value" placeholder="Value" class="flex-1"/>
                <button
                    class="p-1.5 text-gray-400 hover:text-red-500 transition-colors"
                    @click="stdioEnv.splice(idx, 1)"
                >
                  <X :size="14"/>
                </button>
              </div>
            </div>
            <!-- 工作目录 -->
            <div class="space-y-1.5">
              <Label>工作目录 (可选)</Label>
              <Input v-model="stdioWorkDir" placeholder="/home/user/projects"/>
            </div>
          </template>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="dialogOpen = false">取消</Button>
          <Button :disabled="!isFormValid || saving" @click="handleSave">
            <Loader2 v-if="saving" :size="14" class="mr-1 animate-spin"/>
            {{ editingServer ? '保存' : '添加' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- 删除确认 -->
    <AlertDialog :open="deleteDialogOpen" @update:open="deleteDialogOpen = $event">
      <AlertDialogContent>
        <div class="space-y-2 mb-4">
          <AlertDialogTitle>确认删除？</AlertDialogTitle>
          <AlertDialogDescription>
            将删除 MCP 服务器「{{ deletingServer?.name }}」的配置，此操作不可撤销。
          </AlertDialogDescription>
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction class="bg-red-600 hover:bg-red-700" @click="handleDelete">
            确认删除
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import type {McpServer, McpSseConfig, McpStdioConfig, McpTestResult, McpTransportType} from '@/types/mcp'
import {createMcpServer, deleteMcpServer, getMcpServers, setMcpServerEnabled, testMcpServer, updateMcpServer} from '@/api/mcp'
import {Button} from '@/components/ui/button'
import {Label} from '@/components/ui/label'
import {Input} from '@/components/ui/input'
import {Switch} from '@/components/ui/switch'
import {Badge} from '@/components/ui/badge'
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select'
import {Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle} from '@/components/ui/dialog'
import {AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle} from '@/components/ui/alert-dialog'
import {CheckCircle, Cpu, Loader2, Pencil, Plus, Trash2, Wifi, X, XCircle} from 'lucide-vue-next'

// 状态
const loading = ref(false)
const servers = ref<McpServer[]>([])
const testResults = ref<Record<number, McpTestResult>>({})
const testingId = ref<number | null>(null)

// 弹窗状态
const dialogOpen = ref(false)
const editingServer = ref<McpServer | null>(null)
const saving = ref(false)

// 表单
const form = reactive({
  name: '',
  description: '',
  type: 'sse' as McpTransportType
})

// SSE 配置
const sseUrl = ref('')
const sseHeaders = ref<{ key: string; value: string }[]>([])

// StdIO 配置
const stdioCommand = ref('')
const stdioEnv = ref<{ key: string; value: string }[]>([])
const stdioWorkDir = ref('')

// 删除确认
const deleteDialogOpen = ref(false)
const deletingServer = ref<McpServer | null>(null)

// 表单验证
const isFormValid = computed(() => {
  if (!form.name.trim()) return false
  if (form.type === 'sse') {
    return !!sseUrl.value.trim()
  } else {
    return !!stdioCommand.value.trim()
  }
})

onMounted(() => {
  loadServers()
})

async function loadServers() {
  loading.value = true
  try {
    const res = await getMcpServers()
    servers.value = res.data
  } catch {
    servers.value = []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingServer.value = null
  form.name = ''
  form.description = ''
  form.type = 'sse'
  sseUrl.value = ''
  sseHeaders.value = []
  stdioCommand.value = ''
  stdioEnv.value = []
  stdioWorkDir.value = ''
  dialogOpen.value = true
}

function openEditDialog(server: McpServer) {
  editingServer.value = server
  form.name = server.name
  form.description = server.description || ''
  form.type = server.type

  if (server.type === 'sse') {
    const config = server.config as McpSseConfig
    sseUrl.value = config.url
    sseHeaders.value = config.headers
        ? Object.entries(config.headers).map(([key, value]) => ({key, value}))
        : []
    stdioCommand.value = ''
    stdioEnv.value = []
    stdioWorkDir.value = ''
  } else {
    const config = server.config as McpStdioConfig
    stdioCommand.value = config.command.join(' ')
    stdioEnv.value = config.env
        ? Object.entries(config.env).map(([key, value]) => ({key, value}))
        : []
    stdioWorkDir.value = config.workingDirectory || ''
    sseUrl.value = ''
    sseHeaders.value = []
  }

  dialogOpen.value = true
}

function onTypeChange() {
  // 切换类型时清空对应配置
  sseUrl.value = ''
  sseHeaders.value = []
  stdioCommand.value = ''
  stdioEnv.value = []
  stdioWorkDir.value = ''
}

function addHeader() {
  sseHeaders.value.push({key: '', value: ''})
}

function addEnvVar() {
  stdioEnv.value.push({key: '', value: ''})
}

function buildFormData() {
  const config = form.type === 'sse'
      ? {
        url: sseUrl.value.trim(),
        headers: sseHeaders.value
            .filter(h => h.key.trim())
            .reduce((acc, h) => ({...acc, [h.key.trim()]: h.value}), {} as Record<string, string>)
      }
      : {
        command: stdioCommand.value.trim().split(/\s+/),
        env: stdioEnv.value
            .filter(e => e.key.trim())
            .reduce((acc, e) => ({...acc, [e.key.trim()]: e.value}), {} as Record<string, string>),
        workingDirectory: stdioWorkDir.value.trim() || undefined
      }

  return {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    type: form.type,
    config
  }
}

async function handleSave() {
  saving.value = true
  try {
    const data = buildFormData()
    if (editingServer.value) {
      const res = await updateMcpServer(editingServer.value.id, data)
      const idx = servers.value.findIndex(s => s.id === editingServer.value!.id)
      if (idx >= 0) servers.value[idx] = res.data
    } else {
      const res = await createMcpServer(data)
      servers.value.push(res.data)
    }
    dialogOpen.value = false
  } finally {
    saving.value = false
  }
}

async function handleToggleEnabled(server: McpServer) {
  const newEnabled = !server.enabled
  try {
    await setMcpServerEnabled(server.id, newEnabled)
    server.enabled = newEnabled
  } catch { /* handled by interceptor */ }
}

async function handleTest(server: McpServer) {
  testingId.value = server.id
  delete testResults.value[server.id]
  try {
    const res = await testMcpServer(server.id)
    testResults.value[server.id] = res.data
  } catch {
    testResults.value[server.id] = {success: false, message: '请求超时或网络错误', availableTools: [], latencyMs: 0}
  } finally {
    testingId.value = null
  }
}

function confirmDelete(server: McpServer) {
  deletingServer.value = server
  deleteDialogOpen.value = true
}

async function handleDelete() {
  if (!deletingServer.value) return
  try {
    await deleteMcpServer(deletingServer.value.id)
    servers.value = servers.value.filter(s => s.id !== deletingServer.value!.id)
    delete testResults.value[deletingServer.value.id]
  } catch { /* handled by interceptor */ }
  deleteDialogOpen.value = false
  deletingServer.value = null
}
</script>
