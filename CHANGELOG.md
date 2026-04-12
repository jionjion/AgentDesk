# Changelog

## AgentDesk v1.0.0

> 首个正式版本发布！AgentDesk 是一款智能体本地工作台，支持与 AI 智能体进行多轮对话、任务规划与工具调用。

### 🚀 新功能

- **智能体对话** — 支持流式输出、思维链展示、多轮上下文对话
- **任务规划系统** — 子任务创建、修改、状态跟踪，支持计划可视化展示
- **工具调用** — Agent as Tool 能力，工具执行过程与结果可视化
- **文件上传** — 对话中支持上传附件，AI 可读取文件内容
- **会话管理** — 创建、删除、置顶会话，支持全文检索历史对话
- **聊天记录导出** — 支持导出为 Markdown 格式
- **自定义头像** — 用户个性化头像设置
- **夜间模式** — 深色/浅色主题自由切换
- **系统托盘** — 关闭窗口时最小化到托盘区，后台常驻
- **邀请码** — 支持邀请码注册机制
- **设置界面** — 集中管理应用配置

### 🐛 问题修复

- 修复聊天消息重复显示问题
- 修复用户头像加载异常
- 修复 TypeScript 类型检查错误（联合类型断言、未使用导入等）

### 🔧 工程化

- 基于 Electron + Vue 3 + TypeScript 构建桌面应用
- 集成 ESLint 代码规范
- 集成 GitHub Actions 自动构建 Windows 安装包并发布到 Releases
- 构建失败自动重试（最多 3 次）
- 版本发布说明通过 `releases/` 目录管理，CI 自动生成 CHANGELOG

---

## AgentDesk v0.1.0

### 构建与发布
- 修复 Windows runner 下 PowerShell 兼容性问题（retry 步骤添加 `shell: bash`）
- 新增版本发布说明管理：通过 `releases/` 目录按版本维护发布说明，构建时自动读取
- 移除无用的 PR label 分类模板（`.github/release.yml`）

### 代码质量
- 修复 `MessageBubble.vue` 类型断言错误（ChatMessage 联合类型）
- 修复 `ContextMenuContent.vue` 中 `sideOffset` 默认值类型不匹配
- 移除 `use-toast.ts` 未使用的 `Component` 导入
- 修复 `chat.ts` 未使用参数警告及 `PendingFile` 接口私有名称导出问题

---

