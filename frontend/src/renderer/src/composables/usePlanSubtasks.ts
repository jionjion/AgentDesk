import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import type { PlanMessage } from '@/types/chat'
import type { PlanState, Subtask } from '@/types/chat'

/**
 * 尝试将值解析为数组（处理 JSON 字符串的情况）
 */
function toArray(val: unknown): unknown[] | null {
  if (Array.isArray(val)) return val
  if (typeof val === 'string') {
    try {
      const parsed = JSON.parse(val)
      if (Array.isArray(parsed)) return parsed
    } catch { /* ignore */ }
  }
  return null
}

/**
 * 从 create_plan 的 arguments 中提取子任务列表
 */
function parseSubtasksFromArgs(args: Record<string, unknown>): Subtask[] {
  // AgentScope create_plan 参数名为 subtasks，也兼容其他命名
  const candidates = [args.subtasks, args.steps, args.tasks, args.sub_tasks]
  for (const val of candidates) {
    const arr = toArray(val)
    if (arr && arr.length > 0) {
      return arr.map((item: any) => ({
        name: typeof item === 'string'
          ? item
          : (item.name || item.title || item.description || item.content || String(item)),
        state: mapState(typeof item === 'object' ? (item.state || item.status || 'todo') : 'todo')
      }))
    }
  }
  return []
}

/**
 * 从 result 文本中解析子任务列表
 */
function parseSubtasksFromResult(result: string): Subtask[] {
  if (!result) return []

  // 尝试 JSON 解析
  try {
    const parsed = JSON.parse(result)
    if (Array.isArray(parsed)) {
      return parsed.map((item: any) => ({
        name: typeof item === 'string' ? item : (item.name || item.title || item.description || String(item)),
        state: mapState(item.state || item.status || 'todo')
      }))
    }
    if (parsed.subtasks && Array.isArray(parsed.subtasks)) {
      return parsed.subtasks.map((item: any) => ({
        name: typeof item === 'string' ? item : (item.name || item.title || item.description || String(item)),
        state: mapState(item.state || item.status || 'todo')
      }))
    }
  } catch {
    // 非 JSON，走文本解析
  }

  // 文本解析：匹配编号列表或状态标记
  const lines = result.split('\n')
  const subtasks: Subtask[] = []
  for (const line of lines) {
    const trimmed = line.trim()
    const match = trimmed.match(/^(?:\d+\.\s*|[-*]\s+)(.+)$/)
    if (match) {
      let name = match[1].trim()
      let state: Subtask['state'] = 'todo'

      // 匹配 [x], [done] 等状态标记
      const stateMatch = name.match(/^\[([^\]]*)\]\s*(.+)$/)
      if (stateMatch) {
        const marker = stateMatch[1].toLowerCase().trim()
        name = stateMatch[2].trim()
        if (marker === 'x' || marker === 'done' || marker === '完成' || marker === '✓') state = 'done'
        else if (marker === 'in_progress' || marker === '进行中') state = 'in_progress'
        else if (marker === 'abandoned' || marker === '放弃') state = 'abandoned'
      }

      // 去除 (todo)/(done) 后缀
      const suffixMatch = name.match(/^(.+?)\s*\((todo|in_progress|done|abandoned)\)\s*$/)
      if (suffixMatch) {
        name = suffixMatch[1].trim()
        state = mapState(suffixMatch[2])
      }

      if (name) subtasks.push({ name, state })
    }
  }

  return subtasks
}

function mapState(raw: string): Subtask['state'] {
  const s = (raw || '').toLowerCase().trim()
  if (s === 'done' || s === 'completed' || s === 'finished') return 'done'
  if (s === 'in_progress' || s === 'doing' || s === 'running') return 'in_progress'
  if (s === 'abandoned' || s === 'cancelled' || s === 'skipped') return 'abandoned'
  return 'todo'
}

/**
 * 从当前消息中解析计划状态
 */
export function usePlanSubtasks() {
  const chatStore = useChatStore()

  const planState = computed<PlanState>(() => {
    const messages = chatStore.currentMessages
    let title = '任务计划'
    let subtasks: Subtask[] = []
    let isFinished = false

    for (const msg of messages) {
      if (msg.role !== 'plan') continue
      const planMsg = msg as PlanMessage

      switch (planMsg.toolName) {
        case 'create_plan':
        case 'revise_current_plan': {
          const args = planMsg.arguments || {}

          // 优先从 arguments 提取子任务
          const fromArgs = parseSubtasksFromArgs(args)
          if (fromArgs.length > 0) {
            subtasks = fromArgs
          }

          // 也尝试从 result 中提取（可能有更完整的状态信息）
          if (planMsg.result) {
            const fromResult = parseSubtasksFromResult(planMsg.result)
            if (fromResult.length > 0) {
              subtasks = fromResult
            }
          }

          // 提取计划标题
          const argTitle = args.title || args.plan_title || args.name
          if (argTitle && typeof argTitle === 'string') {
            title = argTitle
          }
          break
        }

        case 'get_subtask_count': {
          // 从 result 中提取计划标题
          if (planMsg.result) {
            const titleMatch = planMsg.result.match(/plan\s+'([^']+)'/)
            if (titleMatch) title = titleMatch[1]
          }
          break
        }

        case 'update_subtask_state': {
          const args = planMsg.arguments || {}
          const index = args.subtask_idx ?? args.subtask_index ?? args.index
          const newState = args.state ?? args.new_state
          const subtaskName = args.subtask_name ?? args.name

          if (typeof index === 'number' && index >= 0 && newState) {
            // 如果 index 超出范围，动态扩展
            while (subtasks.length <= index) {
              subtasks = [...subtasks, { name: `子任务 ${subtasks.length + 1}`, state: 'todo' as const }]
            }
            subtasks = subtasks.map((s, i) =>
              i === index
                ? { ...s, state: mapState(String(newState)), name: (subtaskName && typeof subtaskName === 'string') ? subtaskName : s.name }
                : s
            )
          }
          break
        }

        case 'finish_subtask': {
          const args = planMsg.arguments || {}
          const index = args.subtask_idx ?? args.subtask_index ?? args.index

          if (typeof index === 'number' && index >= 0) {
            // 如果 index 超出范围，动态扩展
            while (subtasks.length <= index) {
              subtasks = [...subtasks, { name: `子任务 ${subtasks.length + 1}`, state: 'todo' as const }]
            }
            subtasks = subtasks.map((s, i) =>
              i === index ? { ...s, state: 'done' as const } : s
            )
            // 自动将下一个 todo 子任务标为 in_progress
            const nextTodo = subtasks.findIndex(s => s.state === 'todo')
            if (nextTodo >= 0) {
              subtasks = subtasks.map((s, i) =>
                i === nextTodo ? { ...s, state: 'in_progress' as const } : s
              )
            }
          }
          // result 可能包含最新全量状态
          if (planMsg.result) {
            const fromResult = parseSubtasksFromResult(planMsg.result)
            if (fromResult.length > 0) subtasks = fromResult
          }
          break
        }

        case 'finish_plan': {
          isFinished = true
          if (planMsg.result) {
            const fromResult = parseSubtasksFromResult(planMsg.result)
            if (fromResult.length > 0) subtasks = fromResult
          }
          break
        }

        case 'view_subtasks': {
          if (planMsg.result) {
            const fromResult = parseSubtasksFromResult(planMsg.result)
            if (fromResult.length > 0) subtasks = fromResult
          }
          break
        }
      }
    }

    return { title, subtasks, isFinished }
  })

  const hasPlan = computed(() => planState.value.subtasks.length > 0)

  return { planState, hasPlan }
}
