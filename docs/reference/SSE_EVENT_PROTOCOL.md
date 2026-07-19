# AgentDesk SSE 事件协议参考

更新日期：2026-07-19
适用版本：AgentScope Java v2 (2.0.0) 迁移后

## 1. 概述

聊天流式接口通过 SSE（Server-Sent Events）向前端推送事件。每个事件由**事件名**（`event:` 行）与 **JSON 载荷**（`data:` 行）组成，载荷统一为 `ChatEventDto` 结构（`@JsonInclude(NON_NULL)`，null 字段不序列化）。

```
event: text_chunk
data: {"type":"text_chunk","content":"你好"}
```

### 接口入口

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/chat/stream` | POST (`text/event-stream`) | 发起对话 |
| `/api/chat/regenerate` | GET (`text/event-stream`) | 重新生成指定助手回复 |
| `/api/chat/{sessionId}/interrupt` | POST | 中断当前执行（非 SSE） |

### 事件发送方

- **`AgentEventBridge`**（`agent/runtime/`）：将 AgentScope v2 `streamEvents()` 的强类型 `AgentEvent` 翻译为 SSE。主 Agent 与子智能体事件按 `AgentEvent.getSource()` 分流（见 §4）。
- **`SseEmitterManager`**（`service/chat/`）：编排层事件（心跳、知识检索、记忆召回、消息落库、错误）。

## 2. ChatEventDto 载荷结构

```jsonc
{
  "type": "string",          // 事件类型（必有）
  "content": "string",       // 文本内容
  "toolName": "string",      // 工具名称
  "toolId": "string",        // 工具调用 ID（v2 全局唯一 UUID）
  "arguments": {},           // 工具调用参数（JSON 对象）
  "result": "string",        // 工具调用结果
  "reason": "string",        // 完成原因
  "error": "string",         // 错误信息
  "messageId": 123,          // 数据库消息 ID
  "memoryCount": 3,          // 记忆召回数量
  "taskProgress": {},        // 任务进度载荷（见 §5）
  "subagent": {}             // 子智能体事件载荷（见 §4）
}
```

## 3. 主流程事件

按一次典型对话的时序排列：

| 事件名 | 载荷关键字段 | 触发时机 |
|---|---|---|
| `memory_recalled` | `memoryCount` | 启用 Mem0 且召回数 > 0 时，流开始前发送 |
| `knowledge_retrieved` | `count` / `sources[]` / `references[]`（独立结构，非 ChatEventDto） | 命中知识库检索时，流开始前发送 |
| `agent_start` | — | 主 Agent 开始执行（v2 `AgentStartEvent`） |
| `thinking_chunk` | `content` | 思考增量（`ThinkingBlockDeltaEvent`）；提示块（`HintBlockEvent`）与权限拒绝提示也复用此事件 |
| `text_chunk` | `content` | 主回复文本增量（`TextBlockDeltaEvent`，仅主 Agent） |
| `tool_call_start` | `toolName` / `toolId` / `arguments` | 工具参数 JSON 聚合完毕后宣告（`ToolCallEndEvent` 或 `ToolResultStartEvent` 先到者，去重） |
| `tool_call_end` | `toolName` / `toolId` / `result` | 工具执行结束（`ToolResultEndEvent`）；结果为空时回填 `[工具执行状态: xxx]` |
| `reasoning_complete` | `content`（"true"/"false" = 本轮是否含工具调用） | 每个模型轮次结束（`ModelCallEndEvent`） |
| `task_progress` | `taskProgress` | 主 Agent 调用 `todo_write` 成功后（见 §5） |
| `subagent_event` | `subagent` | 子智能体内部事件（见 §4） |
| `agent_complete` | `content` / `reason` | 主 Agent 结束（`AgentResultEvent`）。`reason`: 模型停止原因枚举名 / `MAX_ITERATIONS` / `STOP_REQUESTED`，后两者会在 content 尾部追加中文警示 |
| `message_saved` | `{"messageId": n}`（独立结构） | 助手回复持久化后 |
| `error` | `error` | 流异常 |
| `heartbeat` | 空 data | 周期性保活 |

注意：

- `tool_call_start` 刻意延迟到参数聚合完毕才发送，前端不会看到空参数的工具卡。
- `agent_complete` 的 `messageId` 为 null；落库 ID 通过随后的 `message_saved` 补发（前端按会话关联）。
- `title_generated` 工厂方法存在但当前无发送路径（标题异步生成，不走本 SSE 流），前端不应依赖。

## 4. subagent_event（子智能体事件隔离）

### 背景

AgentScope v2 会把子智能体（`SubagentDeclaration` 专家团）的内部事件经 `event.withSource(sourcePath)` 打标后转发进主 Agent 的同一 `streamEvents()` 流。`AgentEventBridge` 按 `getSource()` 分流：

- `source == null` → 主 Agent 事件，走 §3 主流程
- `source != null`（如 `"main/researcher"`）→ 翻译为 `subagent_event`，**不混入主回复、不影响主流程状态**（lastReply / 停止原因 / 最大轮次标记）

### 载荷 `subagent` 字段

```jsonc
{
  "source": "main/researcher",   // 完整来源路径, 前端分组键
  "agentId": "researcher",       // source 末段
  "displayName": "研究员",        // 中文显示名, 未注册时回退 agentId
  "eventType": "start",          // 见下表
  "content": "...",              // 文本类事件的内容
  "toolName": "...", "toolId": "...",
  "arguments": {}, "result": "..."
}
```

### eventType 子类型

| eventType | 携带字段 | 对应 v2 事件 |
|---|---|---|
| `start` | `content` = 专家名 | `AgentStartEvent` |
| `text_chunk` | `content` = 文本增量 | `TextBlockDeltaEvent` |
| `thinking_chunk` | `content` = 思考增量 | `ThinkingBlockDeltaEvent` |
| `tool_call_start` | `toolName` / `toolId` / `arguments`（聚合完毕） | `ToolCallEndEvent` |
| `tool_call_end` | `toolName` / `toolId` / `result` | `ToolResultEndEvent` |
| `complete` | `content` = 最终结果文本 | `AgentResultEvent` |

子智能体的模型轮次、提示块、停止等事件不进入 SSE 协议。

### 前端渲染建议

按 `source` 分组渲染可折叠面板：`start` 创建面板（标题用 `displayName`）→ 文本/工具事件追加 → `complete` 标记完成并允许折叠。

### 专家显示名映射

由 `AgentFactory.EXPERT_DISPLAY_NAMES` 维护：

| agentId | displayName |
|---|---|
| researcher | 研究员 |
| software-engineer | 软件工程师 |
| code-reviewer | 代码审查员 |
| data-analyst | 数据分析师 |
| writer | 撰稿人 |
| knowledge-curator | 知识管理员 |
| system-operator | 系统操作员 |

## 5. task_progress（任务清单进度）

### 背景

v2 框架无 TaskList 专用事件；任务清单只由内置 `todo_write` 工具（全量替换语义）维护。`AgentEventBridge` 在 `ToolResultEndEvent` 处拦截主 Agent 的 `todo_write` 参数，由 `TaskProgressTracker` 与上一次列表 diff 后生成增量事件。被权限拒绝（state=DENIED）或校验失败（结果以 `Error` 开头）的调用不推送。

### 载荷 `taskProgress` 字段

```jsonc
{
  "eventType": "plan_created",   // 见下表
  "planTitle": null,             // 预留, 当前恒为 null
  "subtasks": [                  // 仅 plan_created / plan_revised 携带完整列表
    { "id": "uuid", "title": "步骤一", "state": "in_progress" }
  ],
  "subtaskId": "uuid",           // 仅 task_updated / task_completed
  "subtaskTitle": "步骤一",
  "newState": "done",            // todo / in_progress / done / abandoned
  "completedCount": 1,
  "totalCount": 4
}
```

### eventType 子类型

| eventType | 触发条件 | 携带字段 |
|---|---|---|
| `plan_created` | 首次出现非空任务列表 | `subtasks` + counts |
| `plan_revised` | 任务条目集合变化（增删） | `subtasks` + counts |
| `task_updated` | 单任务状态变化（非完成） | `subtaskId` / `subtaskTitle` / `newState` + counts |
| `task_completed` | 单任务变为 done | 同上 |
| `plan_finished` | 全部任务 done（每计划仅一次，revise 后可重发） | counts |

### 状态映射

`todo_write` 状态 → 前端契约：`pending→todo`、`in_progress→in_progress`、`completed→done`。`abandoned` 为前端契约预留值，当前后端不产生。

任务 ID 由 `TaskProgressTracker` 按任务内容维护稳定 UUID（`todo_write` 参数本身不含 ID），同一任务跨调用 ID 不变；条目被移除再加回会分配新 ID，前端可按标题匹配兜底。

## 6. 关键源码索引

| 组件 | 路径 |
|---|---|
| 事件桥（v2 事件 → SSE） | `backend/src/main/java/top/jionjion/agentdesk/agent/runtime/AgentEventBridge.java` |
| 任务进度 diff | `backend/src/main/java/top/jionjion/agentdesk/agent/runtime/TaskProgressTracker.java` |
| 编排层 SSE | `backend/src/main/java/top/jionjion/agentdesk/service/chat/SseEmitterManager.java` |
| 载荷 DTO | `backend/src/main/java/top/jionjion/agentdesk/dto/chat/ChatEventDto.java`（另见 `TaskProgressDto` / `SubagentEventDto`） |
| 前端消费 | `frontend/src/renderer/src/stores/chat.ts` |
