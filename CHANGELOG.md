# Changelog

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

## AgentDesk v0.0.9

### 构建与发布
- 新增 GitHub Actions 自动构建 Windows 安装包并发布到 Releases
- 添加 main 分支保护，仅允许主分支 tag 触发构建
- 构建失败自动重试，最多 3 次（依赖安装、应用打包）
- 新增 `releases/` 目录管理版本发布说明，支持按版本读取

### 代码质量
- 修复 `MessageBubble.vue` 类型断言错误（ChatMessage 联合类型）
- 修复 `ContextMenuContent.vue` 中 `sideOffset` 默认值类型不匹配
- 移除 `use-toast.ts` 未使用的 `Component` 导入
- 修复 `chat.ts` 中未使用的参数警告
- 修复 `PendingFile` 接口私有名称导出问题

### 文档
- 添加 CHANGELOG.md 变更记录

---

