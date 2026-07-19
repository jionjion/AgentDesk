# AgentDesk 项目实体与本地运行集开发计划

> 状态：待实施  
> 编写日期：2026-07-19  
> 适用版本：AgentDesk `3.0.0-preview` / AgentScope Java `2.0.0`  
> 目标读者：后续负责代码改造的开发者或编码 Agent

## 1. 背景与已确认决策

AgentDesk 当前采用以下部署形态：

- Spring Boot / AgentScope 后端部署在服务器 Docker 容器中。
- Electron/Vue 前端运行在用户 PC 上。
- 用户 PC 通过 WebSocket 接收后端发来的本地执行请求。
- 用户选择的工作区用于定位项目、提供默认上下文，不是安全边界。
- 本地执行可以按用户意图访问项目目录以外的文件。
- 一个项目实体允许绑定多个聊天会话。

本次改造采用以下产品决策：

1. Agent 主执行环境改为用户 PC 上的真实本地运行集。
2. Python 使用用户 PC 上的真实解释器，不再以 Pyodide/WASM 作为 Agent 主执行路径。
3. 服务器端 Harness workspace 与用户本地项目必须保持为两个独立概念。
4. 引入服务器端 `Project` 实体和 `Session -> Project` 绑定。
5. 引入 PC 本地的 `ProjectLocation`，保存项目在具体设备上的路径和解释器配置。
6. 项目根目录只是默认 cwd 和模型上下文，不承担文件系统隔离职责。
7. 真正的 Docker/AgentScope Sandbox 仅作为未来可选能力，不属于本轮核心实现。

## 2. 本轮目标与非目标

### 2.1 目标

- 用户选择目录后，Agent 在每轮调用中明确知道当前项目、根目录、操作系统和 Python 环境。
- 多个会话可以绑定同一个项目，并共享项目元数据和本地运行配置。
- Shell、Python、本地文件工具使用同一份调用级项目上下文。
- Python 代码直接由真实 Python 解释器执行，不经过 shell 字符串拼接和 Pyodide。
- PC 离线、项目未在当前设备绑定、解释器不可用时，返回明确且可恢复的错误。
- 保留现有命令审批、超时、输出截断和进程树回收能力。
- 支持 PC 在线时执行绑定 Project 的定时任务：后端在任务触发时主动通过 WebSocket 获取 runtime snapshot，再构造 `ProjectRuntimeContext`；该链路不能依赖 `ChatRequest`。
- 删除或停用 Agent 侧 `sandbox_exec`、MEMFS 文件同步和 Pyodide 自动预热。

### 2.2 非目标

- 本轮不实现工作区外访问限制。
- 本轮不把本地项目文件上传或同步到服务器。
- 本轮不把服务器端 Harness filesystem 整体替换成客户端文件系统。
- 本轮不实现 Docker、Kubernetes、E2B 等真沙箱后端。
- PC 离线、目标设备不在线、项目未绑定本机路径或 snapshot 请求超时时，依赖本地项目的定时任务本次执行失败并记录明确原因；本轮不提供离线替代运行环境。
- 本轮不实现完整的多设备并发调度；协议应携带 `deviceId`，MVP 至少不能把任务静默发到错误设备。

## 3. 当前实现的问题

### 3.1 两个 workspace 概念被混用

`AgentFactory` 当前通过以下逻辑创建服务器端 Harness workspace：

```text
${agentdesk.workspace.base-dir}/<userId>
```

该目录位于后端服务器容器或其挂载卷中，用于 AgentScope 的技能、记忆、计划和运行数据。它不是用户 PC 上选择的代码项目。

相关文件：

- `backend/src/main/java/top/jionjion/agentdesk/agent/core/AgentFactory.java`
- `backend/src/main/resources/application.yaml`

用户选择的目录则存放在前端全局 `sandboxStore.workdir` 中，二者没有结构化关联。

### 3.2 选择的目录没有进入 AgentScope RuntimeContext

当前链路为：

```text
ChatView 选择目录
  -> sandboxStore.workdir
  -> ChatRequest.workingDir
  -> AgentHandle.setWorkingDirectory()
  -> RemoteExecTool.currentWorkingDir
```

问题包括：

- 工作目录只写入工具实例的可变字段，模型上下文并不知道当前项目是什么。
- `AgentRunContext.of(...)` 当前产生空 attributes。
- 重新生成流程没有重新注入工作目录。
- `RemoteExecTool` 在 Agent 创建时捕获 `userId/sessionId`，难以复用并容易和调用级状态混在一起。
- `cd` 通过字符串解析模拟状态，没有检查目录是否存在，也不能正确处理相对路径基准。

相关文件：

- `frontend/src/renderer/src/views/ChatView.vue`
- `frontend/src/renderer/src/stores/chat.ts`
- `backend/src/main/java/top/jionjion/agentdesk/dto/chat/ChatRequest.java`
- `backend/src/main/java/top/jionjion/agentdesk/service/chat/ChatStreamOrchestrator.java`
- `backend/src/main/java/top/jionjion/agentdesk/agent/runtime/AgentRunContext.java`
- `backend/src/main/java/top/jionjion/agentdesk/agent/core/AgentHandle.java`
- `backend/src/main/java/top/jionjion/agentdesk/agent/tool/RemoteExecTool.java`

### 3.3 Pyodide 与真实本地执行形成双执行体系

当前同时存在：

- `sandbox_exec`：浏览器 Worker 中的 Pyodide/MEMFS。
- `remote_exec`：Electron 主进程中真实 PowerShell/sh/Python。

Pyodide 路径存在以下问题：

- 只能使用部分 WASM/纯 Python 包。
- 用户项目会被筛选、扫描并复制到 `/data`，不是项目真实文件系统。
- 扫描最多 200 个文件、深度最多 5 层，且只包含固定扩展名。
- 引擎是全局实例，状态与文件生命周期没有真正按项目/会话建模。
- 应用启动时无条件预热 Pyodide，增加启动和打包成本。
- Agent 需要在两个执行工具之间判断，提示词和失败回退逻辑复杂。

相关文件：

- `frontend/src/renderer/src/workers/pyodide-worker.ts`
- `frontend/src/renderer/src/composables/usePythonEngine.ts`
- `frontend/src/renderer/src/stores/sandbox.ts`
- `frontend/src/renderer/src/stores/sandbox-tools.ts`
- `frontend/src/renderer/src/constants/sandbox-builtin-tools.ts`
- `backend/src/main/java/top/jionjion/agentdesk/agent/tool/SandboxExecTool.java`
- `backend/src/main/java/top/jionjion/agentdesk/service/chat/PromptContextBuilder.java`

### 3.4 当前 boundary 不是沙箱边界

Electron 主进程当前只检查命令的 cwd 是否位于 `allowedRoots` 中，但 shell/Python 仍可以使用绝对路径访问其他位置。同时前端会用后端请求携带的 `workingDir` 构建 `allowedRoots`，因此它不能作为用户授权根。

本轮产品决策已经明确：项目根不是安全边界。因此应把相关概念改成“默认项目根/默认 cwd”，不要继续使用 `sandbox`、`boundary` 或“只能访问工作区”等会造成错误预期的表述。

相关文件：

- `frontend/src/main/ipc/index.ts`
- `frontend/src/renderer/src/stores/remoteExec.ts`
- `frontend/src/renderer/src/types/remote-exec.ts`
- `backend/src/main/java/top/jionjion/agentdesk/websocket/dto/CommandRequest.java`

## 4. 目标架构

```text
┌──────────────────────── 服务器 ────────────────────────┐
│ Project                                                  │
│   ├─ 项目名称、说明、项目级指令                          │
│   └─ 1:N Session                                         │
│                                                         │
│ Harness AgentWorkspace                                   │
│   ├─ AGENTS.md / skills / memory / plans                │
│   └─ AgentStateStore                                     │
│                                                         │
│ ProjectRuntimeContext -> AgentScope RuntimeContext       │
│                                                         │
│ ClientRuntimeBridge ───────── WebSocket RPC ─────────────┼──┐
└──────────────────────────────────────────────────────────┘  │
                                                              │
┌──────────────────────── 用户 PC ────────────────────────────┐│
│ ProjectLocation                                             ││
│   projectId -> rootPath / Python / shell / env              ││
│                                                              ││
│ LocalRuntime                                                 │◀┘
│   ├─ shell_exec                                              │
│   ├─ python_exec                                             │
│   └─ local file operations                                   │
└──────────────────────────────────────────────────────────────┘
```

### 4.1 概念边界

| 概念 | 保存位置 | 生命周期 | 说明 |
| --- | --- | --- | --- |
| `AgentWorkspace` | 服务器 | 用户/Agent 级 | Harness 技能、记忆、计划和运行文件 |
| `Project` | 服务器数据库 | 用户主动创建/删除 | 逻辑项目，可绑定多个会话 |
| `Session` | 服务器数据库 | 会话级 | 对话和 AgentState，关联一个可选项目 |
| `ProjectLocation` | Electron 主进程本地配置 | 设备级 | 项目在当前 PC 上的真实目录及解释器 |
| `ProjectRuntimeContext` | 单次调用 | 调用级 | 当前项目、设备、cwd、平台和运行能力快照 |
| `LocalRuntime` | Electron 主进程 | 应用级 | 统一执行 Shell、Python 和本地文件操作 |

## 5. 数据模型

### 5.1 服务器 Project 表

建议新增 `agent_desk.projects`：

```sql
CREATE TABLE agent_desk.projects (
    id           VARCHAR(32) PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES agent_desk.users(id) ON DELETE CASCADE,
    name         VARCHAR(256) NOT NULL,
    description  TEXT,
    instructions TEXT,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL
);

CREATE INDEX idx_projects_user_updated
    ON agent_desk.projects(user_id, updated_at DESC);
```

为 `sessions` 增加可空关联：

```sql
ALTER TABLE agent_desk.sessions
    ADD COLUMN project_id VARCHAR(32)
        REFERENCES agent_desk.projects(id) ON DELETE SET NULL;

CREATE INDEX idx_sessions_project
    ON agent_desk.sessions(project_id, last_used_at DESC);
```

为需要访问本地项目的定时任务增加可空绑定：

```sql
ALTER TABLE agent_desk.scheduled_tasks
    ADD COLUMN project_id VARCHAR(32)
        REFERENCES agent_desk.projects(id) ON DELETE SET NULL;

ALTER TABLE agent_desk.scheduled_tasks
    ADD COLUMN device_id VARCHAR(128);

CREATE INDEX idx_scheduled_tasks_project
    ON agent_desk.scheduled_tasks(project_id);
```

要求：

- 所有 Project 查询必须按当前 `userId` 做归属校验。
- 绑定会话时必须同时校验 Session 和 Project 属于当前用户。
- 删除 Project 不删除会话，只将 `sessions.project_id` 置空。
- 删除 Project 不删除定时任务，只将 `scheduled_tasks.project_id` 置空；此后任务按无项目任务运行，不能调用本地项目工具。
- 创建或更新项目型定时任务时必须同时保存 `projectId` 和目标 `deviceId`，不能在触发时猜测任意在线设备。
- 现有 Session 的 `project_id` 默认保持 `NULL`，不强制迁移。

### 5.2 PC 本地 ProjectLocation

ProjectLocation 属于设备本地数据，不进入服务器数据库：

```ts
interface ProjectLocation {
  projectId: string
  deviceId: string
  rootPath: string
  defaultCwd?: string
  pythonExecutable?: string
  shellProfile?: 'powershell' | 'cmd' | 'sh' | 'bash' | 'zsh'
  env?: Record<string, string>
  lastOpenedAt: number
}
```

存储建议：

- 由 Electron 主进程保存到 `app.getPath('userData')/local-settings/projects.json`。
- 不放在 renderer `localStorage` 中。
- 路径写入前通过 `realpath/path.resolve` 规范化。
- `deviceId` 首次启动生成并持久化，不使用 hostname 作为唯一 ID。
- 环境变量中如允许保存敏感信息，后续应接系统凭据存储；MVP 不建议保存 secrets。

### 5.3 ProjectRuntimeContext

后端每次调用使用不可变的调用级对象：

```java
public record ProjectRuntimeContext(
    String projectId,
    String projectName,
    String deviceId,
    String rootPath,
    String cwd,
    String platform,
    String pythonExecutable,
    String pythonVersion,
    boolean runtimeOnline
) {}
```

注意：

- `rootPath` 和解释器信息来自在线客户端本轮提供的 runtime snapshot。
- Project 归属关系以服务器数据库为准，不能只信任请求体中的 `projectId`。
- 项目根不是授权边界，工具允许显式绝对路径。
- 未传 cwd 时使用 rootPath；相对 cwd 基于 rootPath 解析。

## 6. REST API 计划

### 6.1 Project API

建议新增：

```text
POST   /api/projects
GET    /api/projects
GET    /api/projects/{id}
PUT    /api/projects/{id}
DELETE /api/projects/{id}
```

建议 DTO：

```text
ProjectCreateRequest(name, description?, instructions?)
ProjectUpdateRequest(name, description?, instructions?)
ProjectResponse(id, name, description, instructions, createdAt, updatedAt)
```

本地绝对路径不属于 Project API。

### 6.2 Session API

修改：

```text
SessionCreateRequest(title, projectId?)
SessionResponse(..., projectId?, projectName?)
```

新增显式绑定接口，避免把“改标题”和“换项目”塞进同一个请求：

```text
PUT /api/sessions/{sessionId}/project
Body: { "projectId": "..." | null }
```

规则：

- 会话正在流式执行时不允许切换项目。
- 切换成功后使对应内存 AgentHandle 失效或清除旧的调用级执行上下文。
- 不删除该会话历史；下一轮必须注入“当前项目已改变”的清晰上下文。
- 重新生成始终读取 Session 当前绑定的 Project，而不是使用旧消息中残留的路径。

## 7. Electron IPC 与本地项目管理

建议新增主进程模块：

```text
frontend/src/main/projects/projectStore.ts
frontend/src/main/projects/runtimeDiscovery.ts
frontend/src/main/runtime/localRuntime.ts
```

建议 IPC：

```text
projects:getDeviceId
projects:listLocations
projects:getLocation(projectId)
projects:bindLocation(projectId, rootPath)
projects:removeLocation(projectId)
projects:getRuntimeSnapshot(projectId)

runtime:execute(request)
runtime:cancel(requestId)
runtime:readFile(request)
runtime:writeFile(request)
runtime:listFiles(request)
runtime:searchFiles(request)
```

渲染进程只能调用有限 IPC，不直接获得 Node `child_process` 或 `fs`。

### 7.1 Python 发现

Windows 推荐发现顺序：

1. 项目本地已配置的解释器。
2. `<project>/.venv/Scripts/python.exe`。
3. `py -3` 解析出的默认 Python。
4. PATH 中的 `python.exe`。

macOS/Linux 推荐发现顺序：

1. 项目本地已配置的解释器。
2. `<project>/.venv/bin/python`。
3. PATH 中的 `python3`。
4. PATH 中的 `python`。

发现过程只返回候选路径和版本，不自动创建 venv、不自动安装包。

## 8. WebSocket 协议改造

### 8.1 client_ready

扩展 `client_ready`：

```json
{
  "deviceId": "stable-device-id",
  "platform": "win32|x64|...",
  "appVersion": "3.0.0",
  "capabilities": ["shell", "python", "local_files"],
  "pythonCandidates": [
    {"path": "C:\\Python312\\python.exe", "version": "3.12.4"}
  ]
}
```

后端连接索引目标为 `(userId, deviceId)`。如果 MVP 仍限制一个在线设备，也必须记录 deviceId，并在项目绑定设备与当前连接不一致时返回明确错误，不能静默路由。

### 8.2 统一执行请求

建议把内部传输统一为：

```text
local_exec_request
local_exec_result
local_exec_cancel
```

请求示例：

```json
{
  "kind": "python",
  "projectId": "...",
  "deviceId": "...",
  "cwd": ".",
  "code": "print('hello')",
  "scriptPath": null,
  "args": [],
  "timeoutMs": 120000,
  "maxOutputChars": 262144,
  "riskLevel": "LOW"
}
```

Shell 请求使用 `kind: "shell"` 和 `command` 字段。

执行结果统一包含：

```text
success
exitCode
stdout
stderr
durationMs
cancelled
timedOut
```

不要继续为 Pyodide 保留独立的 `sandbox_exec_request/result`。

### 8.3 本地文件 RPC

第一阶段可以为文件操作使用独立的请求/响应类型：

```text
local_fs_request
local_fs_result
```

操作类型：

```text
read
write
edit
list
glob
grep
stat
```

所有请求必须带 `projectId/deviceId/sessionId/requestId`，但路径允许绝对路径；相对路径以项目 rootPath 为基准。

本地文件 RPC 必须有独立于 exec 输出限制的响应预算，客户端和后端都要校验，不能只依赖 WebSocket 容器的帧上限。建议默认值：

| 操作 | 默认上限 | 超限行为 |
| --- | --- | --- |
| `read` | 单次最多扫描 512 KiB、返回 64,000 字符、5,000 行，单行最多 20,000 字符 | 返回截断标记、总大小和可继续读取的 offset；禁止静默截断 |
| `write` | 单次请求体 2 MiB | 超限拒绝，提示改用脚本或分块写入 |
| `edit` | 目标文本文件 2 MiB | 超限拒绝，避免在内存中重写大文件 |
| `list/glob` | 最多 1,000 个条目，且总结果不超过 64,000 字符 | 返回 `truncated=true` 和 continuation/cursor |
| `grep` | 最多 200 个匹配、单条最多 4,000 字符，且总结果不超过 64,000 字符 | 返回 `truncated=true`、实际扫描文件数和匹配数 |

协议要求：

- 配置中显式定义 `maxReadBytes`、`maxOutputChars`、`maxLines`、`maxEntries`、`maxMatches` 和 `maxPayloadBytes`；建议 `maxOutputChars=64_000`、序列化后单个 WebSocket payload 不超过 256 KiB。
- 请求允许传更小的 `maxBytes/maxOutputChars/maxLines/maxEntries/maxMatches`，但不能超过服务端下发的硬上限。
- `read` 支持 `offset/length`，便于 Agent 分块读取大文件。
- 响应统一带 `truncated`；可继续读取时带 `nextOffset` 或 `nextCursor`。
- 客户端在发送前限制序列化后的 payload 大小，后端收到后再次限制；后端把结果交给模型前再次执行 `maxOutputChars` 截断，形成磁盘读取、WebSocket 传输和模型上下文三层限制。
- 文本读取默认只接受 UTF-8/UTF-8 BOM；检测到二进制或无法可靠解码时返回元数据和错误，不把整段二进制转成 base64 塞给模型。
- grep/list 的结果进入模型前仍需服从工具结果卸载和 token 预算策略。

### 8.4 定时任务 runtime snapshot

定时任务没有 `ChatRequest`，必须使用独立的请求/响应协议：

```text
runtime_snapshot_request
runtime_snapshot_result
```

请求至少包含：

```json
{
  "requestId": "...",
  "taskId": 123,
  "projectId": "...",
  "deviceId": "..."
}
```

执行流程：

1. `ScheduledTaskExecutor` 读取任务的 `projectId/deviceId`，并校验 Project 属于任务用户。
2. 未绑定 Project 的任务按服务器侧普通 Agent 任务运行，本地工具应报告“当前任务未绑定项目”。
3. 已绑定 Project 的任务通过 `ClientRuntimeBridge` 向指定设备发送 `runtime_snapshot_request`。
4. Electron 主进程根据 `projectId` 查找本机 `ProjectLocation`，重新检查目录和 Python 是否仍存在，再返回 snapshot。
5. 后端使用 snapshot 构造 `ProjectRuntimeContext`，放入该次一次性 Agent 的 `AgentRunContext`。
6. snapshot 请求使用独立短超时（建议 10 秒，可配置），不能占用完整 Agent 执行超时。
7. PC 离线、目标 deviceId 不在线、ProjectLocation 缺失或请求超时，任务日志标记 `FAILED`，结果使用稳定错误码：`RUNTIME_OFFLINE`、`DEVICE_MISMATCH`、`PROJECT_LOCATION_NOT_FOUND` 或 `SNAPSHOT_TIMEOUT`。

设备变更处理：

- `deviceId` 在用户重装应用或更换设备后会变化，已保存的任务目标设备将永久 `DEVICE_MISMATCH`。
- 定时任务管理 UI 必须允许用户为已有任务重新指定目标设备（默认候选为当前在线设备），不要求删除重建任务。

无人值守审批规则：

- 定时任务不能无限等待桌面审批。
- MVP 中只允许按既有策略自动放行的低风险只读操作；需要人工确认的 Shell/Python/写入操作立即失败并写入 `APPROVAL_REQUIRED`。
- 明确的产品结论：由于 `python_exec` 默认高风险（见 10.3），MVP 中项目型定时任务实际无法执行 Python 和写入类操作，只能完成只读 Shell/文件操作。这是有意为之的限制，需在任务创建 UI 和文档中向用户说明，避免用户配置"定时数据分析"类任务后静默失败。
- 如果以后允许任务级预授权，必须设计独立、可撤销且有范围的策略，不复用聊天会话中的"本次全部允许"。

## 9. AgentScope 与后端工具改造

### 9.1 保留服务器 AgentWorkspace

必须保留：

```java
HarnessAgent.builder().workspace(serverWorkspace)
```

不要把用户 PC 路径传给 AgentScope `LocalFilesystemSpec.project(...)`，因为后端运行在服务器 Docker 中，那样只会访问服务器磁盘。

第一阶段继续使用服务器端 Harness filesystem 管理：

- AGENTS.md
- skills
- subagents
- memory
- plans
- session/task runtime files

用户本地项目通过自定义 Toolkit 工具访问。

### 9.2 调用级上下文

`AgentRunContext` 应承载 `ProjectRuntimeContext`，并由 `AgentHandle.stream()` 放入 AgentScope `RuntimeContext`。

优先实现方式：

- 检查 AgentScope Java `2.0.0` 本地 Maven sources，确认工具调用时读取 `RuntimeContext` 的官方方式。
- 如果官方工具上下文可用，工具直接从当前调用上下文读取。

**Phase 0 调研结论（2026-07-19，已确认采用官方方式）**：

- `RuntimeContext.builder().put(ProjectRuntimeContext.class, ctx)` 写入类型化属性。
- `ToolMethodInvoker` 对无 `@ToolParam` 注解的工具方法参数按类型从 `RuntimeContext` 自动注入；工具方法声明 `ProjectRuntimeContext` 参数即可获得调用级上下文。
- 子 Agent 由 `DefaultAgentManager.createAgent(agentId, parentRc)` 自动继承父 `RuntimeContext`，无需额外传递。
- `ToolExecutionContext` 已被官方标记 `@Deprecated`，禁止使用。
- 因此**不需要**实现回退的 `ExecutionContextRegistry`；`AgentHandle.stream()` 中现有 `RuntimeContext.builder()` 只需追加类型化 put。

禁止：

- 把当前 project/cwd 存在单例字段。
- 把当前 project/cwd 只存在 `RemoteExecTool.currentWorkingDir`。
- 让模型通过工具参数改变当前项目实体。

### 9.3 模型可见上下文

每轮向模型提供短小、明确的项目上下文：

```xml
<project_context>
项目：AgentDesk
项目 ID：...
本地根目录：W:\AgentDesk
默认工作目录：W:\AgentDesk
系统：Windows
Python：3.12.4 (C:\Python312\python.exe)
本地运行环境：已连接
</project_context>
```

要求：

- 数据来自服务器已验证的 Session->Project 关系和在线客户端 snapshot。
- 原始用户消息仍按原文写入 `chat_messages`。
- 重新生成时重新构造当前项目上下文。
- 项目上下文不应被当作用户陈述或长期记忆抽取。实施时需核对 `MemoryService`/mem0 抽取链路（`service/MemoryService.java`、`ChatStreamOrchestrator`）读取的是原始用户消息，而不是注入 `<project_context>` 后的增强消息。
- 工具执行必须依赖结构化上下文，不能仅依赖 prompt 中的路径。

### 9.4 工具表面

目标工具：

```text
shell_exec
python_exec
local_read_file
local_write_file
local_edit_file
local_list_files
local_search_files
```

为降低一次性改动风险，可以分两步：

1. 保留 `remote_exec` 名称作为 `shell_exec` 的兼容别名，但更新描述，不再提 sandbox。
2. 新增 `python_exec`，验证稳定后再决定是否将 `remote_exec` 正式重命名。

`python_exec` 参数建议：

```text
code?: string
script_path?: string
args?: string[]
cwd?: string
timeout_ms?: integer
```

规则：

- `code` 与 `script_path` 二选一。
- code 通过 stdin 传给 `python -`，不要拼接成 `python -c "..."`。
- cwd 为空时使用项目根。
- 相对 cwd 按项目根解析；绝对 cwd 直接使用。
- Python 解释器由 ProjectLocation 决定，不允许模型任意替换可执行文件。

`shell_exec` 的工具描述必须明确：

- cwd 是逐次调用参数，不跨工具调用保持。
- 单独执行 `cd subdir` 只影响该次短生命周期 shell，下一次调用不会继承。
- 后续命令需要在子目录运行时，应传 `cwd="subdir"`，或在同一条命令中使用 `cd subdir && command`。
- 后端和客户端都不要拦截裸 `cd` 并伪造持久目录状态。

`local_edit_file` 使用确定性的精确替换语义，参数建议：

```text
path: string
old_text: string
new_text: string
replace_all?: boolean = false
expected_replacements?: integer = 1
```

规则：

- 默认要求 `old_text` 在文件中精确出现一次；0 次或多次均返回错误，不猜测目标位置。
- `replace_all=true` 时仍校验 `expected_replacements`；实际数量不一致则不写文件。
- 只处理 UTF-8/UTF-8 BOM 文本，并保留 BOM。
- 对统一 CRLF 或 LF 文件，可把匹配文本规范化为 LF 后匹配，写回时恢复原换行风格。
- 检测到混合换行符时拒绝自动规范化并返回诊断，避免无关行产生大面积 diff。
- 写入应尽量采用同目录临时文件加原子替换，并保留原文件权限；失败时不得留下半写文件。
- 文件大小服从本地文件 RPC 的 edit 上限。

### 9.5 子 Agent

以下专家需要获得真实本地工具：

- software-engineer
- code-reviewer
- data-analyst
- system-operator

所有子 Agent 必须继承父调用的 `ProjectRuntimeContext`，不能重新选择项目。

更新对应提示词：

- `backend/src/main/resources/agents/software-engineer.ftl`
- `backend/src/main/resources/agents/code-reviewer.ftl`
- `backend/src/main/resources/agents/data-analyst.ftl`
- `backend/src/main/resources/agents/system-operator.ftl`

删除所有“纯内存计算用 sandbox_exec”的选择说明，改为优先使用 `python_exec` 或 `shell_exec`。

## 10. 本地执行实现要求

### 10.1 Shell

保留现有能力：

- Windows PowerShell 非交互执行。
- macOS/Linux `/bin/sh -c` 或配置 shell。
- 超时终止进程树。
- stdout/stderr 截断。
- 并发执行上限。
- Windows Job Object 内存和进程数限制。

调整：

- 将 `allowedRoots/isolationLevel=boundary` 从安全边界语义中移除。
- cwd 解析改为项目上下文默认值，而不是授权根。
- 不拦截纯 `cd` 并在服务端假装成功。
- 每次调用无状态，cwd 必须由请求明确决定。
- 同步更新工具描述和专家提示词，明确裸 `cd` 不会跨调用生效；模型应使用 `cwd` 参数或单条 `cd x && command`。

### 10.2 Python

建议实现：

```text
spawn(pythonExecutable, ['-', ...args], { cwd, env })
stdin.write(code)
stdin.end()
```

执行现有脚本时：

```text
spawn(pythonExecutable, [scriptPath, ...args], { cwd, env })
```

Windows 和中文环境必须显式统一为 UTF-8：

- spawn 环境至少注入 `PYTHONUTF8=1` 和 `PYTHONIOENCODING=utf-8`，覆盖值由 LocalRuntime 统一管理。
- 可兼容时同时使用 Python 的 UTF-8 mode（如 `-X utf8`），但不能只依赖系统活动代码页。
- `child.stdin` 明确使用 UTF-8 写入代码；stdout/stderr 明确按 UTF-8 解码。
- Node 流使用 `setEncoding('utf8')` 或 `StringDecoder('utf8')`，避免多字节字符跨 chunk 时被破坏；不要对每个 Buffer chunk 独立 `toString()` 后直接拼接。
- Windows 下继续设置 `PYTHONIOENCODING/PYTHONUTF8`，不能假定 PowerShell 的 `[Console]::OutputEncoding` 会影响被直接 spawn 的 Python。
- 增加包含中文源码、中文 stdout/stderr、中文路径和 emoji 的回归测试。

Python 与 Shell 使用同一套：

- 进程并发计数。
- 超时。
- 输出截断。
- Job Object/进程树回收。
- 取消协议。
- 执行历史。

不要自动安装包。缺包时把真实错误返回给 Agent，由 Agent 决定是否提出安装命令并走审批。

### 10.3 风险与审批

项目根不是安全边界，但审批仍用于降低误操作风险：

- 只读命令可以按现有用户设置自动执行。
- 写入、删除、安装依赖、发布、推送、系统配置修改仍为高风险。
- 只有 `local_write_file/local_edit_file` 等具有结构化路径参数的文件工具，才能可靠判断目标是否位于项目目录外；这类项目外写入/删除可自动提升为高风险，但不禁止。
- 任意 Shell 命令和 Python 代码不承诺静态判断是否会写入项目外；不要通过正则或代码扫描声称实现了这一检测。
- `python_exec` 默认按高风险处理，除非后续增加可靠的用户级“本会话允许 Python”策略。
- 不要声称命令分类器可以提供宿主隔离。

## 11. 前端产品改造

### 11.1 Project Store

建议新增：

```text
frontend/src/renderer/src/types/project.ts
frontend/src/renderer/src/api/projects.ts
frontend/src/renderer/src/stores/projects.ts
```

Store 负责：

- 加载服务器项目列表。
- 加载当前设备 ProjectLocation。
- 创建项目并绑定目录。
- 将会话绑定/解绑项目。
- 获取当前项目 runtime snapshot。
- 显示当前设备是否具备该项目位置。

### 11.2 项目选择器

聊天输入区现有“选择工作目录”按钮改成 ProjectSelector：

```text
当前项目：AgentDesk
设备：Jion-PC（在线）
路径：W:\AgentDesk
Python：3.12.4
```

交互：

- “从文件夹创建项目”。
- “绑定已有项目到本机目录”。
- “切换当前会话项目”。
- “解除项目绑定”。
- 项目不存在本机位置时展示“绑定本机目录”。
- 新会话默认继承用户最近使用项目，但必须在创建请求中显式写入 `projectId`。
- 不再在新会话时清空全局 workdir。
- Phase 1 尚未完成 runtime snapshot 和能力发现时，在线状态、Python 版本和 Shell 信息统一显示“未检测/待接入”，不能把这些字段作为 Phase 1 的阻塞依赖；Phase 2/3 接通后再展示真实值。

### 11.3 设置页

将“虚拟机沙盒”改为“本地运行环境”，内容包括：

- 当前设备 ID/名称。
- WebSocket 连接状态。
- Shell 类型。
- 检测到的 Python 列表。
- 默认 Python。
- 命令审批策略。
- 超时、输出上限和可选资源限制。

Pyodide 沙箱工具管理 UI 在完成迁移后删除。

### 11.4 代码块运行按钮

过渡期可保留手动运行按钮，但应改用真实 `python_exec`：

- 明确显示将在当前项目和当前 Python 环境中运行。
- 走与 Agent 工具相同的审批与执行路径。
- 不再直接调用 `sandboxStore.execute()`。

如果暂时无法完成真实 Python 运行按钮，可在移除 Pyodide 时先隐藏按钮，不要保留两套运行语义。

## 12. 文件级改造清单

### 12.1 后端新增

```text
backend/src/main/java/top/jionjion/agentdesk/entity/Project.java
backend/src/main/java/top/jionjion/agentdesk/repository/ProjectRepository.java
backend/src/main/java/top/jionjion/agentdesk/service/ProjectService.java
backend/src/main/java/top/jionjion/agentdesk/controller/ProjectController.java
backend/src/main/java/top/jionjion/agentdesk/dto/project/*
backend/src/main/java/top/jionjion/agentdesk/agent/runtime/ProjectRuntimeContext.java
backend/src/main/java/top/jionjion/agentdesk/agent/tool/PythonExecTool.java
backend/src/main/java/top/jionjion/agentdesk/agent/tool/LocalFileTools.java
backend/src/main/resources/db/migration-project-runtime.sql
```

### 12.2 后端修改

```text
backend/src/main/resources/db/database.sql
backend/src/main/java/top/jionjion/agentdesk/entity/SessionMetadata.java
backend/src/main/java/top/jionjion/agentdesk/dto/session/SessionCreateRequest.java
backend/src/main/java/top/jionjion/agentdesk/dto/session/SessionResponse.java
backend/src/main/java/top/jionjion/agentdesk/repository/SessionRepository.java
backend/src/main/java/top/jionjion/agentdesk/service/SessionService.java
backend/src/main/java/top/jionjion/agentdesk/controller/SessionController.java
backend/src/main/java/top/jionjion/agentdesk/dto/chat/ChatRequest.java
backend/src/main/java/top/jionjion/agentdesk/service/chat/ChatStreamOrchestrator.java
backend/src/main/java/top/jionjion/agentdesk/service/chat/PromptContextBuilder.java
backend/src/main/java/top/jionjion/agentdesk/agent/runtime/AgentRunContext.java
backend/src/main/java/top/jionjion/agentdesk/agent/core/AgentHandle.java
backend/src/main/java/top/jionjion/agentdesk/agent/core/AgentFactory.java
backend/src/main/java/top/jionjion/agentdesk/agent/core/AgentPool.java
backend/src/main/java/top/jionjion/agentdesk/agent/exec/ClientExecutor.java
backend/src/main/java/top/jionjion/agentdesk/agent/tool/RemoteExecTool.java
backend/src/main/java/top/jionjion/agentdesk/agent/tool/CommandRiskClassifier.java
backend/src/main/java/top/jionjion/agentdesk/agent/tool/ToolDefinitions.java
backend/src/main/java/top/jionjion/agentdesk/websocket/RemoteExecBridge.java
backend/src/main/java/top/jionjion/agentdesk/websocket/RemoteExecWebSocketHandler.java
backend/src/main/java/top/jionjion/agentdesk/websocket/dto/CommandRequest.java
backend/src/main/java/top/jionjion/agentdesk/websocket/dto/WsMessage.java
backend/src/main/java/top/jionjion/agentdesk/entity/ScheduledTask.java
backend/src/main/java/top/jionjion/agentdesk/dto/task/ScheduledTaskRequest.java
backend/src/main/java/top/jionjion/agentdesk/dto/task/ScheduledTaskResponse.java
backend/src/main/java/top/jionjion/agentdesk/service/ScheduledTaskService.java
backend/src/main/java/top/jionjion/agentdesk/scheduler/ScheduledTaskExecutor.java
backend/src/main/resources/agents/*.ftl
```

修改说明补充：

- `AgentPool.java`：会话切换 Project 时需要通过 `invalidate(sessionId)` 使内存 AgentHandle 失效（对应 6.2 的规则）；`RemoteExecTool` 随 Agent 创建注册，换项目后必须由 pool 参与清理。
- `CommandRiskClassifier.java`：落实 10.3 的风险规则调整——`python_exec` 默认高风险、结构化文件工具的项目外写入提升风险，并移除任何"分类器提供隔离"的语义。

### 12.3 后端删除或退役

稳定迁移后删除：

```text
backend/src/main/java/top/jionjion/agentdesk/agent/tool/SandboxExecTool.java
backend/src/main/java/top/jionjion/agentdesk/websocket/dto/SandboxResult.java
```

同时删除所有 `sandbox_exec` 工具注册、权限白名单和提示词引用。

现有测试同步改造：

- `backend/src/test/java/top/jionjion/agentdesk/service/chat/PromptContextBuilderTest.java` 覆盖了 `buildSandboxAugmentedMessage`；删除该方法后此测试会编译失败，必须在同一提交中修改或删除对应用例，不能留待 Phase 5 之后。

### 12.4 前端新增

```text
frontend/src/main/projects/projectStore.ts
frontend/src/main/projects/runtimeDiscovery.ts
frontend/src/main/runtime/localRuntime.ts
frontend/src/renderer/src/types/project.ts
frontend/src/renderer/src/api/projects.ts
frontend/src/renderer/src/stores/projects.ts
frontend/src/renderer/src/components/chat/ProjectSelector.vue
frontend/src/renderer/src/components/settings/LocalRuntimeSection.vue
```

### 12.5 前端修改

```text
frontend/src/main/index.ts
frontend/src/main/ipc/index.ts
frontend/src/main/ipc/jobObject.ts
frontend/src/preload/index.ts
frontend/src/renderer/src/types/electron.d.ts
frontend/src/renderer/src/types/chat.ts
frontend/src/renderer/src/types/remote-exec.ts
frontend/src/renderer/src/api/session.ts
frontend/src/renderer/src/api/chat.ts
frontend/src/renderer/src/stores/chat.ts
frontend/src/renderer/src/stores/remoteExec.ts
frontend/src/renderer/src/views/ChatView.vue
frontend/src/renderer/src/views/SettingsView.vue
frontend/src/renderer/src/components/chat/MessageBubble.vue
frontend/src/renderer/src/components/chat/CommandApprovalBubble.vue
frontend/src/renderer/src/types/scheduledTask.ts
frontend/src/renderer/src/api/scheduledTasks.ts
frontend/src/renderer/src/components/scheduled-tasks/TaskFormDialog.vue
frontend/src/renderer/src/components/scheduled-tasks/TaskCard.vue
frontend/src/renderer/src/App.vue
frontend/package.json
frontend/package-lock.json
frontend/electron.vite.config.ts  # 如存在 Pyodide 构建复制配置
```

### 12.6 前端删除或退役

稳定迁移后删除：

```text
frontend/src/renderer/src/workers/pyodide-worker.ts
frontend/src/renderer/src/composables/usePythonEngine.ts
frontend/src/renderer/src/stores/sandbox.ts
frontend/src/renderer/src/stores/sandbox-tools.ts
frontend/src/renderer/src/types/sandbox.ts
frontend/src/renderer/src/types/sandbox-tools.ts
frontend/src/renderer/src/constants/sandbox-builtin-tools.ts
frontend/src/renderer/src/components/settings/SandboxSection.vue
frontend/src/renderer/src/components/settings/SandboxToolsSection.vue
frontend/src/renderer/src/components/sandbox/ExecutionResult.vue  # 若不再复用展示组件
frontend/scripts/copy-pyodide.mjs
frontend/scripts/download-pyodide-packages.mjs
frontend/src/renderer/public/pyodide/  # copy 脚本生成的运行时资源目录（pyodide.asm.js、pyodide-lock.json 等），未入 git，需从开发机和打包产物中一并清除
```

并移除：

- `pyodide` npm dependency。
- `setup:pyodide`、postinstall/dev/build 中的 Pyodide 步骤。
- 打包资源中的 Pyodide 目录。
- `sandbox_settings`、`sandbox_tools` 等废弃 localStorage 逻辑。

## 13. 分阶段实施计划

### Phase 0：建立基线

- 运行现有后端测试、前端 typecheck/lint。
- 记录当前远程命令审批、取消、超时和图表/代码块行为。
- 检查工作区是否有用户未提交改动，禁止覆盖无关内容。
- 阅读 AgentScope `2.0.0` sources jar，确认 RuntimeContext 在工具层的访问方式。

完成标准：现有主分支可以稳定构建，已确定调用级上下文实现路径。

**Phase 0 已完成（2026-07-19）**：前端 typecheck/lint 通过；后端 `mvnw test` 通过；工作区仅有本计划文档为未跟踪文件；RuntimeContext 官方注入路径已确认（见 9.2 调研结论）。

### Phase 1：Project 实体与会话绑定

- 新增数据库迁移和 Project CRUD。
- Session 增加 `projectId/projectName`。
- 新增会话项目绑定接口。
- ScheduledTask 增加可选 `projectId/deviceId`，任务表单可选择 Project 和目标设备。
- 前端新增 Project store、ProjectSelector。
- Electron 主进程新增 ProjectLocation 存储。
- 新会话显式绑定最近项目，不再清空 workdir。

完成标准：创建一个 Project 后，两个不同会话可绑定同一 Project；重启应用后绑定关系和本地路径仍存在。ProjectSelector 在本阶段允许将在线状态、Python 和 Shell 显示为“未检测/待接入”，不要求提前完成 Phase 2/3。

### Phase 2：调用级 ProjectRuntimeContext

- 客户端生成 runtime snapshot。
- ChatRequest 携带 snapshot 或可验证的 runtime reference。
- 新增 `runtime_snapshot_request/result`，使项目型定时任务在没有 ChatRequest 时也能向指定在线设备获取 snapshot。
- `ScheduledTaskExecutor` 获取 snapshot 后构造与聊天相同的 ProjectRuntimeContext；离线、位置缺失和超时写入稳定失败码。
- 后端校验 Session->Project 后构造 `ProjectRuntimeContext`。
- 通过 AgentRunContext/RuntimeContext 传入 Harness。
- 模型每轮看到 project_context。
- 重新生成、子 Agent 和中断流程使用同一上下文模型。
- 清除 `RemoteExecTool.currentWorkingDir` 依赖和伪 `cd` 状态。

完成标准：用户只需选择/绑定一次项目，Agent 在后续所有会话轮次中都能准确报告项目、cwd、OS 和 Python；无需用户在消息中重复路径。

### Phase 3：统一 LocalRuntime 与真实 Python

- 扩展 client_ready/deviceId/capabilities。
- 抽取 Shell 和 Python 共用的进程运行器。
- 实现 `python_exec`。
- 执行请求携带 projectId/deviceId/cwd。
- Python stdin/stdout/stderr 和 spawn 环境统一使用 UTF-8，并覆盖中文、emoji 和中文路径回归场景。
- 保留审批、取消、超时、输出上限、Job Object。
- 将代码块运行按钮切换到真实 Python 或临时隐藏。

完成标准：Agent 可在项目根运行真实 Python，导入本机已安装包、读取真实项目文件，并正确返回 stdout/stderr/退出码。

**Phase 3 已完成（2026-07-20）**：统一 local_exec_request/result/cancel 协议；localRuntime.ts 共用运行器（UTF-8 StringDecoder、Job Object、并发/超时/截断/取消）；python_exec 后端工具（code 经 stdin 传 python -，解释器由 ProjectLocation 决定）；runtimeDiscovery 按配置→.venv→py -3/python3→PATH 顺序发现；client_ready 上报 capabilities 与 pythonCandidates；代码块运行按钮已移除（不保留两套运行语义）。

### Phase 4：本地文件工具

- 实现本地 read/write/edit/list/search RPC。
- 为 read/list/glob/grep/edit 实现字节、行数、条目数和匹配数上限以及 continuation 信息。
- `local_edit_file` 使用精确匹配、匹配次数校验、CRLF/LF 保留和原子写入语义。
- 注册对应 Agent 工具并更新专家白名单。
- 路径为空/相对时以项目根为默认，绝对路径允许。
- 仅结构化文件工具可根据路径将项目外写入/删除提升审批等级；Shell/Python 不承诺检测。

完成标准：代码审查和软件工程 Agent 不需要通过 shell 的 `cat/type` 才能读取本地文件。

**Phase 4 已完成（2026-07-20）**：local_fs_request/result 协议与 localFs.ts 实现（read/write/edit/list/glob/grep/stat，含字节/行/条目/匹配上限与 truncated/nextOffset）；local_read_file/local_write_file/local_edit_file/local_list_files/local_search_files 五个工具注册并加入专家白名单；local_edit_file 精确匹配 + expected_replacements + CRLF/LF 保留 + 原子写入；python_exec 默认高风险，结构化文件工具项目外写入提升为高风险。

### Phase 5：移除 Pyodide Agent 路径

- 删除 `sandbox_exec` 后端工具和 WebSocket 协议。
- 删除 sandbox prompt augmentation。
- 删除 Pyodide Worker、store、工具注入和自动预热。
- 删除 npm 依赖、复制脚本和构建资源。
- 设置页改为本地运行环境。
- 更新 README、CHANGELOG、迁移文档。
- 检查并更新 `docs/reference/SSE_EVENT_PROTOCOL.md`：新增 `python_exec`/`local_*` 工具后，工具调用与审批相关 SSE 事件中的工具名枚举和示例需同步；删除其中的 `sandbox_exec` 引用。

完成标准：代码库中除历史变更记录外不再存在 `sandbox_exec`、Pyodide `/data`、MEMFS 工作目录同步逻辑。

**Phase 5 已完成（2026-07-20）**：删除 SandboxExecTool/SandboxResult/sandbox_exec 协议、buildSandboxAugmentedMessage、ChatRequest.SandboxContext；前端删除 pyodide-worker/usePythonEngine/sandbox stores/Sandbox 设置组件/ExecutionResult/copy-pyodide 脚本与 pyodide 依赖；4 个专家 .ftl 更新为 shell_exec/python_exec/local_* 工具组合；SSE_EVENT_PROTOCOL.md 无 sandbox_exec 引用无需修改。

### Phase 6：收尾与兼容

- 清理旧 `sandbox_settings` 和 `remote_exec_settings.defaultWorkDir`。
- 可选：若旧 defaultWorkDir 存在，首次启动提示用户将其创建为 Project，不要静默迁移。
- 补齐单元测试和手工测试。
- 更新开发与用户文档。

**Phase 6 已完成（2026-07-20）**：settingsVersion 升到 3 并在迁移中丢弃 defaultWorkDir；App 启动清理 sandbox_settings/sandbox_tools；设置页新增 LocalRuntimeSection（连接状态/设备信息/Python 检测/审批策略/执行历史）替代虚拟机沙盒；ChatRequest.workingDir 与前端 workingDir 过渡逻辑移除；后端全部测试与前端 typecheck/lint/build 通过。

## 14. 测试计划

### 14.1 后端单元测试

建议新增：

```text
ProjectServiceTest
ProjectControllerTest
SessionProjectBindingTest
ProjectRuntimeContextTest
PythonExecToolTest
LocalFileToolsTest
RemoteExecBridgeDeviceRoutingTest
ScheduledTaskRuntimeSnapshotTest
LocalFileRpcLimitTest
LocalEditFileSemanticsTest
PythonUtf8ExecutionTest
```

必须覆盖：

- 用户不能读取或绑定其他用户的 Project。
- 删除 Project 后 Session 保留且 projectId 为空。
- 同一 Project 可绑定多个 Session。
- 未绑定 Project 的普通对话不受影响。
- 客户端离线时本地工具返回可理解错误。
- Project 绑定设备与在线设备不一致时拒绝路由。
- 重新生成使用当前 Project，而不是旧 runtime snapshot。
- stream 异常、取消和完成后调用级上下文均被清理。
- 子 Agent 获取与父 Agent 相同的项目上下文。
- AgentFactory 不再注册 `sandbox_exec`。
- 项目型定时任务在目标 PC 在线时能获取 snapshot，并构造与聊天一致的 ProjectRuntimeContext。
- 项目型定时任务在 PC 离线、设备不匹配、位置缺失和 snapshot 超时时记录对应稳定错误码。
- 需要人工审批的无人值守任务立即以 `APPROVAL_REQUIRED` 失败，不无限等待。
- Python 中文源码、中文 stdout/stderr、emoji 和中文路径保持 UTF-8，不产生乱码或截断字符。
- read/glob/grep 超限时返回截断与 continuation 元数据，不发送超大 WebSocket 消息。
- local_edit_file 对 0/1/多次匹配、CRLF/LF、混合换行和原子写入行为符合约定。

### 14.2 前端验证

当前前端没有自动化测试脚本，至少执行：

```powershell
cd frontend
npm run typecheck
npm run lint
npm run build
```

手工矩阵：

1. 从目录创建 Project。
2. 新建两个会话并绑定同一 Project。
3. 切换会话，项目不丢失。
4. 重启 Electron，ProjectLocation 仍可恢复。
5. 执行 `pwd/Get-Location`，结果为项目根。
6. 真实 Python 输出版本和 `sys.executable`。
7. Python 导入项目 venv 已安装包。
8. Python 读取项目内真实文件并写出结果。
9. 按用户指令读取项目外绝对路径。
10. 项目外写入触发高风险确认但允许执行。
11. 中断长时间 Python，确认子进程树被回收。
12. PC 离线时，聊天仍可回复但本地工具明确失败。
13. 重新连接后同一会话继续使用原 Project。
14. 会话中切换 Project 后，下一轮 cwd 和 prompt 立即更新。
15. software-engineer/data-analyst 子 Agent 能调用真实 Python。
16. 重新生成不会恢复旧项目路径。
17. 删除 Project 不删除历史会话。
18. 未绑定 Project 的会话可以正常纯文本聊天。
19. `shell_exec` 单独执行 `cd subdir` 后，下一次调用不继承 cwd；通过 `cwd` 参数可正确运行。
20. Python 执行包含中文源码、中文输出、emoji 和中文路径时无乱码。
21. 读取超大文件、glob 大目录和 grep 大结果时收到截断标记与 continuation，不导致 WS 断开。
22. local_edit_file 在唯一匹配时成功，在 0 次/多次匹配时不写文件；CRLF 文件写回仍为 CRLF。
23. 绑定 Project 的定时任务在 PC 在线时成功获取 snapshot 并执行只读操作。
24. 同一定时任务在 PC 离线时快速失败并记录 `RUNTIME_OFFLINE`。
25. 定时任务触发需要人工审批的操作时以 `APPROVAL_REQUIRED` 失败，不长期挂起。

### 14.3 后端验证命令

```powershell
cd backend
.\mvnw.cmd clean test
```

数据库迁移需在 PostgreSQL 测试实例验证：

- 全新数据库执行 `database.sql`。
- 现有数据库执行增量 migration。
- 回滚前备份 sessions 表。

## 15. 验收标准

以下条件全部满足才算完成：

- [ ] 服务器存在 Project 实体和 Session->Project 关系。
- [ ] 多个会话可以绑定同一 Project。
- [ ] Project 本地路径保存在 Electron 主进程的设备级配置中。
- [ ] 每轮 Agent 调用都包含结构化 ProjectRuntimeContext。
- [ ] 模型能准确知道当前项目根、cwd、平台和 Python。
- [ ] 工具不再依赖可变 `currentWorkingDir`。
- [ ] Python 使用真实本地解释器并通过 stdin/脚本参数执行。
- [ ] Shell/Python 共用超时、取消、输出截断和进程树回收。
- [ ] 本地文件工具与 Shell/Python 使用同一 Project 上下文。
- [ ] 项目外路径允许访问，但风险审批语义清晰。
- [ ] PC 离线和项目未绑定本机目录时有明确错误。
- [ ] 项目型定时任务不依赖 ChatRequest，能通过 WebSocket 主动获取 runtime snapshot。
- [ ] Windows/Python 全链路统一为 UTF-8，中文源码、输出、路径和 emoji 验证通过。
- [ ] 本地文件 RPC 具有明确的字节、行数、条目和匹配数上限，并支持截断/继续读取。
- [ ] local_edit_file 的精确匹配、换行符和原子写入语义通过测试。
- [ ] Shell 工具描述明确 cwd 无状态，裸 cd 不跨调用生效。
- [ ] `sandbox_exec` 不再向 Agent 暴露。
- [ ] Pyodide 不再自动加载，也不再参与 Agent 执行。
- [ ] 后端测试、前端 typecheck、lint、build 全部通过。
- [ ] README/CHANGELOG/迁移说明已更新。

## 16. 实施注意事项

1. 不要把服务器 Harness workspace 改成用户本地项目路径。
2. 不要把整个 Harness `AbstractFilesystem` 立即替换成客户端 RPC；先用独立本地项目工具稳定业务链路。
3. 不要在 Reactor 流中使用 ThreadLocal 保存项目上下文。
4. 不要把项目路径只拼进 prompt；工具必须读取结构化上下文。
5. 不要让工具实例持有会话级可变 cwd。
6. 不要通过拼接 `python -c` 实现 Python 工具。
7. 不要把 cwd 校验称为沙箱或安全隔离。
8. 不要自动把本地项目文件上传到服务器。
9. 不要自动安装 Python 包或创建虚拟环境。
10. 保留用户现有聊天、知识库、技能、记忆和 AgentState 数据。
11. 修改数据库前新增独立 migration，不要只修改 `database.sql`。
12. 工作区存在未提交改动时必须保留用户改动，不得使用破坏性 git 命令。

## 17. 后续可选方向

本轮完成后再评估：

- 使用 AgentScope `abstractFilesystem(...)` 实现 client-backed filesystem。
- 将 Harness 文件工具与本地项目工具做统一路径路由。
- 多设备同时在线和 ProjectLocation 选择。
- 项目级长期记忆、项目摘要和 `PROJECT.md` 指令。
- 项目文件索引与增量上下文发现。
- PC 离线时将定时任务切换到 Docker/AgentScope Sandbox。
- AgentScope `DockerFilesystemSpec`、快照和托管 SandboxRuntime。

## 18. 参考资料

- [AgentScope Java v2 Filesystem](https://java.agentscope.io/v2/zh/docs/harness/filesystem.html)
- [AgentScope Java v2 Sandbox](https://java.agentscope.io/v2/zh/docs/harness/sandbox.html)
- `docs/AGENTSCOPE_V2_MIGRATION.md`
- `docs/reference/SSE_EVENT_PROTOCOL.md`
