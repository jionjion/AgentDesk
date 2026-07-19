# AgentScope Java v2 升级方案与接手手册

更新日期：2026-07-16  
目标版本：AgentScope Java `2.0.0`  
策略：直接升级 v2，不保留 v1 双栈、灰度开关或旧 Session 兼容层。

## 1. 目标

AgentDesk 的 Agent 运行时升级为 AgentScope Java v2 `HarnessAgent`，并把桌面定位为“用户自己电脑上的专家团”：

- `assistant` 是首席助理，负责理解目标、维护任务、委派专家和汇总结论。
- 专家使用 Harness 原生 `SubagentDeclaration`，不再以旧 Agent-as-Tool 方式注册。
- 每次调用使用 `RuntimeContext(userId, sessionId)` 隔离状态。
- PostgreSQL 实现 v2 `AgentStateStore`，由框架在调用前后自动加载和保存。
- 后端消费 `streamEvents()` 的强类型 `AgentEvent`，转换为现有前端 SSE 协议。
- 本机命令仍只通过 AgentDesk 自己的 `RemoteExecTool` / `SandboxExecTool`，Harness 内置 shell 被禁用。

官方依据：

- [v2 快速开始](https://java.agentscope.io/v2/zh/docs/quickstart.html)
- [v2 迁移指南](https://java.agentscope.io/v2/zh/docs/change-log.html)
- [Harness 架构](https://java.agentscope.io/v2/zh/docs/harness/architecture.html)
- [Permission System](https://java.agentscope.io/v2/zh/docs/building-blocks/permission-system.html)
- [子 Agent](https://java.agentscope.io/v2/zh/docs/harness/subagent.html)

## 2. 已实施变更

### 依赖

`backend/pom.xml` 已切换到：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>2.0.0</version>
</dependency>
```

DashScope 模型类已从 core 包迁到：

```java
io.agentscope.extensions.model.dashscope.DashScopeChatModel
```

### Agent 运行时

主要入口：

- `backend/src/main/java/top/jionjion/agentdesk/agent/core/AgentFactory.java`
- `backend/src/main/java/top/jionjion/agentdesk/agent/core/AgentHandle.java`
- `backend/src/main/java/top/jionjion/agentdesk/agent/runtime/AgentEventBridge.java`
- `backend/src/main/java/top/jionjion/agentdesk/agent/runtime/AgentInput.java`
- `backend/src/main/java/top/jionjion/agentdesk/agent/runtime/AgentRunContext.java`

`AgentFactory` 当前构建一个 v2 `HarnessAgent`，启用：

- Task List
- Plan Mode
- Workspace memory / context
- classpath 与用户文件系统技能仓库
- MCP 工具
- 原有 API、文件、联网研究、本机执行和沙箱工具
- 原生 Harness 子智能体

旧的 `SseStreamingHook`、`DynamicAgentTool`、`DatabaseSession` 已删除。

定时任务（`ScheduledTaskExecutor`）也已对接 v2：每次执行通过 `AgentFactory.createAgent()` 创建一次性 Agent（会话 ID 形如 `sched-{taskId}-{startTime}`），以 `AgentRunContext.of(userId, sessionId)` 阻塞式运行并用 try-with-resources 关闭，不经过 `AgentPool` 缓存。

### 专家团队

当前以代码内声明方式注册：

| 专家 | 职责 | 持久会话 |
| --- | --- | --- |
| `researcher` | 联网研究、交叉验证、来源整理 | 否 |
| `software-engineer` | 实现、调试、验证代码变更 | 否 |
| `code-reviewer` | 独立代码审查 | 否 |
| `data-analyst` | 数据清洗、统计与解释 | 否 |
| `writer` | 文档撰写与编辑 | 否 |
| `knowledge-curator` | 知识整理、去重与沉淀 | 否 |
| `system-operator` | 经授权执行本机操作 | 否 |

每个专家现在都有独立提示词和工具白名单，不再继承主 Agent 的全部工具。暂时关闭持久子 Agent 会话，避免“重新生成”复用旧分支中的专家状态。下一阶段可以把这些定义迁到用户 workspace 的 `subagents/*.md`，使角色提示词和权限可由用户编辑。当前使用代码声明是为了让升级后无需额外文件部署即可运行。

### 状态模型

v1 `Session` / `SessionKey` 已移除。新实现：

```text
DatabaseAgentStateStore implements AgentStateStore
              │
              └── agent_desk.agent_state_v2
                  PK(user_id, session_id, state_key)
```

匿名调用使用内部命名空间 `__anonymous__`。普通聊天使用登录用户 ID 的字符串形式。

执行现有数据库升级：

```powershell
psql -f backend/src/main/resources/db/migration-v2.sql
```

也可以重新执行 `backend/src/main/resources/db/database.sql`；脚本使用 `IF NOT EXISTS`。

旧 `agent_state` 不再读取，可以在确认新版本稳定后人工归档或删除。聊天消息表不受影响。

### 事件协议

完整 SSE 事件契约见 `docs/reference/SSE_EVENT_PROTOCOL.md`。

后端链路：

```text
HarnessAgent.streamEvents()
    -> AgentEventBridge
    -> ChatEventDto
    -> SSE
```

前端继续接收 `agent_start`、`text_chunk`、`thinking_chunk`、`tool_call_start`、`tool_call_end`、`reasoning_complete`、`agent_complete` 和 `error`，并新增 `subagent_event`。`tool_call_start` 现在等待 v2 `ToolCallEndEvent` 后发送，确保参数 JSON 已完整聚合，不再长期显示空参数；数据型工具结果、模型轮次结束、权限拒绝、提示块和停止原因也已映射。

AgentScope 2.0.0 的 `HarnessAgent.streamEvents()` **会**把子智能体的内部事件转发进同一事件流：`AgentSpawnTool` 通过 `event.withSource(sourcePath)` 打标（主 Agent 事件 `getSource()` 为 null，子智能体为斜杠路径如 `main/researcher`）。`AgentEventBridge` 据此分流：source 非 null 的事件不再混入主回复，而是翻译为独立的 `subagent_event`（payload `subagent: { source, agentId, displayName, eventType, content, toolName, toolId, arguments, result }`，`displayName` 为专家中文显示名（如 `研究员`，未注册时回退 agentId），eventType 为 `start` / `text_chunk` / `thinking_chunk` / `tool_call_start` / `tool_call_end` / `complete`），由前端按 source 分组渲染为可折叠面板。子智能体事件不影响主流程状态（lastReply、停止原因、最大轮次标记等）。

Task List 进度通过 `task_progress` 事件推送。v2 框架没有 TaskList 专用事件（任务清单只由内置 `todo_write` 工具以全量替换语义维护），因此 `AgentEventBridge` 在 `ToolResultEndEvent` 处拦截 `todo_write` 的聚合参数，由 `TaskProgressTracker` 与上一次列表 diff 后翻译为 `plan_created` / `plan_revised` / `task_updated` / `task_completed` / `plan_finished` 增量事件（状态映射 `pending→todo`、`completed→done`；任务 ID 由 tracker 按内容维护稳定 UUID）。工具校验失败（结果以 `Error` 开头）时不推送。

## 3. 安全边界

- Harness 内置 shell 已通过 `disableShellTool()` 禁用。
- 本机命令继续走 `RemoteExecTool`，沿用 AgentDesk 风险分类、WebSocket 和桌面审批。
- `SandboxExecTool` 继续使用隔离执行路径。
- 已配置 v2 `PermissionContextState`，因此调用经过 `PermissionEngine`；使用 `DONT_ASK` 模式和显式工具 ALLOW 清单，未分类工具默认拒绝，Harness 内置 `execute` 有显式 DENY。
- 工具自身安全检查仍先于 ALLOW 规则执行；`RemoteExecTool` 的命令风险分类和 Electron 审批仍是最终执行边界。
- 子智能体设置 `inheritParentPermissions(true)`，并通过 `SubagentDeclaration.tools(...)` 使用角色级工具白名单。

## 4. 2026-07-16 遗漏修复（1–8）

1. **精确中断**：`AgentHandle` 调用 `ReActAgent.interrupt(userId, sessionId)`，不再调用落入默认槽位的弃用无参方法；中断接口不会为了中断而创建新 Agent。
2. **完整事件参数**：工具参数在 `ToolCallDeltaEvent` 聚合完毕后解析，补充模型轮次、数据结果、权限拒绝和停止原因映射。
3. **研究工具**：配置 `TAVILY_API_KEY` 后同时注册 `web_search`、`url_fetch` 和 `batch_web_researcher`；研究专家只获得研究所需白名单。
4. **Mem0 接通对话**：启用用户记忆设置后，每轮调用前通过 Mem0 `/search` 召回相关事实并注入当前消息，完成后异步写回该轮对话；失败自动降级，不阻断聊天。v2 已弃用旧 `LongTermMemory`，因此该集成位于应用编排层而不是旧 Agent API。
5. **权限与工具隔离**：启用 `PermissionContextState`，禁止 Harness shell，并为七个专家配置最小工具集合。
6. **专家提示词**：新增软件工程、数据分析、专业写作、知识管理、系统操作五份专用提示词；不再错误复用规划或摘要提示词。
7. **重新生成回滚**：删除目标助手回复及其后的消息分支，删除旧 `agent_state_v2`，从目标用户消息之前的持久化聊天记录重建状态，再执行一次该用户消息。
8. **技能偏好生效**：prompt 型已启用技能注入系统提示词；classpath / 用户文件系统技能仓库通过 `FilteredSkillRepository` 只暴露用户已启用的技能。注意 `FilteredSkillRepository` 是只读包装器：`save()` / `delete()` 静默返回 false，`setWriteable(true)` 抛异常，技能的安装与卸载必须走 `SkillService`，不要经由该仓库写入。启停技能仍会使用户 Agent 缓存失效。

## 5. 验证命令

后端：

```powershell
cd backend
.\mvnw.cmd clean test
```

前端：

```powershell
cd frontend
npm run typecheck
npm run lint
```

差异检查：

```powershell
git diff --check
git status --short
```

需要真实 DashScope、PostgreSQL、MCP 和桌面 WebSocket 的测试仍属于集成测试，需要完整环境变量后单独运行：

```powershell
cd backend
.\mvnw.cmd test -Dtest.excludedGroups=
```

## 6. 上线前检查清单

- 执行 `migration-v2.sql`。
- 确认 `DASHSCOPE_API_KEY` 和数据库连接参数有效。
- 启动后创建两个用户、两个会话，确认状态互不串线。
- 重启后继续同一会话，确认 Harness 状态恢复。
- 验证普通文本、图片、文件、MCP、远程命令审批和沙箱执行。
- 验证模型切换会重建 Agent，但不会删除同一会话的 v2 状态。
- 验证删除会话同时删除对应 `agent_state_v2` 数据。
- 检查长任务中断、SSE 断线和达到最大迭代次数的提示。
- 启用 Mem0 后确认聊天触发 `/search`、前端显示召回数量，并在完成后新增记忆。
- 禁用任意内置或用户技能后，新建/重建 Agent，确认技能目录不再暴露给模型。
- 对历史中间回复执行“重新生成”，确认该回复之后的旧分支被删除。

## 7. 已知后续工作

这些不阻塞 v2 编译与基础运行，但属于专家团产品化的下一阶段：

1. 将每会话 `AgentHandle` 缓存进一步改为按“用户 + 模型配置版本”复用 Harness；目前远程执行工具仍携带 sessionId，因此暂时保留每会话实例。
2. 把工作目录和 userId/sessionId 改为工具从 `RuntimeContext` 读取，解除工具与 Agent 实例的会话绑定。
3. 将专家定义迁到 `workspace/subagents/*.md`，允许用户编辑角色策略。
4. 在现有 DONT_ASK + ALLOW/DENY 清单基础上增加用户可配置规则和通用 ASK/HITL 响应端点。
5. ~~把 Harness Task List 事件完整映射到现有 `task_progress` UI。~~ 已完成：`AgentEventBridge` 拦截 `todo_write` + `TaskProgressTracker` diff 推送（见"事件协议"一节）。
6. 为 PostgreSQL `AgentStateStore` 增加 Testcontainers 集成测试和并发会话测试。
7. 评估将聊天消息、工具轨迹和 AgentState 做统一版本快照，以便重新生成时恢复比聊天文本更完整的历史执行轨迹。

## 8. Claude Code / 其他代理接手顺序

如果当前改动中断，按以下顺序接手：

1. 阅读本文件和 `git diff --stat`，不要恢复 v1 依赖或旧 Session API。
2. 运行 `cd backend; .\mvnw.cmd clean test`，先解决编译或单测问题。
3. 检查 `AgentFactory`、`AgentHandle`、`AgentEventBridge` 和 `DatabaseAgentStateStore`。
4. 执行数据库脚本，在真实环境做一次文本聊天和重启恢复测试。
5. 再运行前端 typecheck/lint；本次 SSE 事件名应保持兼容。
6. 不要删除用户现有聊天表、知识库、技能或设置数据。只有旧 `agent_state` 已明确不再使用。

出现框架 API 不确定时，以本地 Maven sources jar 和上述官方文档为准，不要根据 v1 经验猜接口。
