import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SandboxTool, SandboxToolFormData } from '@/types/sandbox-tools'
import { BUILTIN_TOOLS } from '@/constants/sandbox-builtin-tools'

const STORAGE_KEY = 'sandbox_custom_tools'

function loadCustomTools(): SandboxTool[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch { /* ignore */ }
  return []
}

function saveCustomTools(tools: SandboxTool[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tools))
}

export const useSandboxToolsStore = defineStore('sandbox-tools', () => {
  const tools = ref<SandboxTool[]>([])
  const loading = ref(false)

  /** 加载所有工具（内置 + 自定义） */
  function loadTools() {
    const custom = loadCustomTools()
    tools.value = [...BUILTIN_TOOLS, ...custom]
  }

  /** 已启用的工具 */
  const enabledTools = computed(() => tools.value.filter(t => t.enabled))

  /** 内置工具 */
  const builtinTools = computed(() => tools.value.filter(t => t.builtin))

  /** 自定义工具 */
  const customTools = computed(() => tools.value.filter(t => !t.builtin))

  /** 添加自定义工具 */
  function addTool(data: SandboxToolFormData) {
    const tool: SandboxTool = {
      id: `custom_${Date.now()}`,
      ...data,
      builtin: false,
      enabled: true,
      version: '1.0.0',
      createdAt: Date.now(),
      updatedAt: Date.now()
    }
    tools.value.push(tool)
    saveCustomTools(customTools.value)
  }

  /** 更新工具 */
  function updateTool(id: string, updates: Partial<SandboxToolFormData>) {
    const tool = tools.value.find(t => t.id === id)
    if (!tool) return
    Object.assign(tool, updates, { updatedAt: Date.now() })
    if (!tool.builtin) {
      saveCustomTools(customTools.value)
    }
  }

  /** 删除自定义工具 */
  function removeTool(id: string) {
    const idx = tools.value.findIndex(t => t.id === id && !t.builtin)
    if (idx === -1) return
    tools.value.splice(idx, 1)
    saveCustomTools(customTools.value)
  }

  /** 启用/禁用工具 */
  function toggleTool(id: string, enabled: boolean) {
    const tool = tools.value.find(t => t.id === id)
    if (!tool) return
    tool.enabled = enabled
    tool.updatedAt = Date.now()
    if (!tool.builtin) {
      saveCustomTools(customTools.value)
    }
  }

  /** 获取所有启用工具的 Python 注入代码 */
  function getEnabledToolsCode(): string {
    const enabled = enabledTools.value
    if (enabled.length === 0) return ''

    // 收集所有需要预装的依赖包
    const allDeps = new Set<string>()
    for (const t of enabled) {
      for (const dep of t.dependencies) {
        allDeps.add(dep)
      }
    }

    // 生成依赖安装代码（失败时打印警告，不中断其他工具）
    const installCode = allDeps.size > 0
      ? `
import micropip
_failed_deps = []
_index_urls = ['https://pypi.tuna.tsinghua.edu.cn/simple', 'https://pypi.org/simple']
for _dep in [${[...allDeps].map(d => `'${d}'`).join(', ')}]:
    try:
        await micropip.install(_dep, index_urls=_index_urls)
    except Exception as _install_err:
        _failed_deps.append(f"{_dep}: {_install_err}")
if _failed_deps:
    print(f"[沙箱警告] 以下依赖安装失败，相关工具可能不可用: {'; '.join(_failed_deps)}", file=__import__('sys').stderr)
del _failed_deps, _index_urls
`
      : ''

    const lines = enabled.map(t =>
      `${t.code}\ntools.${t.name} = ${t.name}`
    )

    // 注意：用 __sandbox_tools 避免被 import tools 覆盖
    // 最后把 __sandbox_tools 赋值到 __user_globals 中
    return `
${installCode}
class _ToolNamespace:
    """沙箱工具命名空间"""
    pass

tools = _ToolNamespace()

${lines.join('\n\n')}
`
  }

  /** 获取工具描述（用于 AI Prompt） */
  function getToolDescriptions(): string {
    const enabled = enabledTools.value
    if (enabled.length === 0) return ''

    const items = enabled.map(t =>
      `- \`tools.${t.signature}\` — ${t.description}`
    )

    return `## 沙箱可用工具函数

以下工具函数已预装在沙箱环境中，可直接通过 \`tools.\` 命名空间调用：

${items.join('\n')}

使用示例：
\`\`\`python
files = tools.list_files()
df = tools.to_dataframe('/data/sales.xlsx')
tools.save_file(df, 'result.csv')
\`\`\``
  }

  /** 导出工具为 JSON */
  function exportTool(id: string): string | null {
    const tool = tools.value.find(t => t.id === id)
    if (!tool) return null
    const { builtin, createdAt, updatedAt, ...exportData } = tool
    return JSON.stringify(exportData, null, 2)
  }

  /** 导入工具 */
  function importTool(json: string): boolean {
    try {
      const data = JSON.parse(json)
      if (!data.name || !data.code) return false
      addTool({
        name: data.name,
        description: data.description || '',
        signature: data.signature || `${data.name}()`,
        code: data.code,
        dependencies: data.dependencies || []
      })
      return true
    } catch {
      return false
    }
  }

  // 初始化加载
  loadTools()

  return {
    tools,
    loading,
    enabledTools,
    builtinTools,
    customTools,
    loadTools,
    addTool,
    updateTool,
    removeTool,
    toggleTool,
    getEnabledToolsCode,
    getToolDescriptions,
    exportTool,
    importTool
  }
})
