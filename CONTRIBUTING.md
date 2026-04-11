# Contributing / 贡献指南

感谢你对 AgentDesk 项目的关注！欢迎提交 Issue 和 Pull Request。

## 开发环境搭建

1. Fork 本仓库
2. 克隆你的 Fork：
   ```bash
   git clone https://github.com/<your-username>/AgentDesk.git
   ```
3. 按照 [README](README.md#快速开始) 中的步骤启动前后端

## 分支规范

- `main` — 主分支，保持稳定可发布状态
- 功能开发请从 `main` 创建新分支，命名格式：`feature/<功能描述>`
- Bug 修复：`fix/<问题描述>`

## 提交规范

提交信息使用中文，格式简洁明了，例如：

```
聊天标题功能开发
全文检索功能开发
头像问题修复
```

## Pull Request

1. 确保代码通过类型检查：
   ```bash
   cd frontend && npm run typecheck
   ```
2. 确保后端编译通过：
   ```bash
   cd backend && mvn compile
   ```
3. 在 PR 描述中说明改动内容和原因

## 报告问题

请通过 [GitHub Issues](https://github.com/jionjion/AgentDesk/issues) 提交问题，包含以下信息：

- 问题描述
- 复现步骤
- 期望行为
- 实际行为
- 环境信息（操作系统、Node.js 版本、Java 版本等）
