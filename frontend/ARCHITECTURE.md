# Frontend 项目结构说明

本项目基于 **Electron + Vue 3 + TypeScript** 构建，使用 [electron-vite](https://electron-vite.org/) 作为构建工具。

## 整体架构

Electron 应用由三个层组成：

```
src/
├── main/           # Electron 主进程 (Node.js 环境)
├── preload/        # 预加载脚本 (安全桥梁层)
└── renderer/       # 渲染进程 (浏览器环境，即 UI 界面)
```

- **main** — 后台进程，负责窗口创建、系统 API 调用、应用生命周期管理
- **preload** — 在渲染进程加载前执行，安全地向前端暴露受限的 Node.js 能力（如 `window.electronAPI.window.minimize()`）
- **renderer** — 前端页面，本质上是一个标准的 Vue 3 SPA 应用

## 渲染进程目录结构

```
src/renderer/src/
├── api/            # 后端 HTTP 请求封装
├── assets/         # 静态资源（CSS 入口 main.css）
├── components/     # Vue 组件
│   ├── chat/       #   聊天相关子组件（MessageBubble, ToolCallCard, PlanCard 等）
│   └── ui/         #   shadcn-vue 基础 UI 组件（Button, Input, Tabs 等）
├── layouts/        # 页面布局组件
├── lib/            # 工具函数（cn() 等）
├── router/         # Vue Router 路由配置
├── stores/         # Pinia 状态管理
├── styles/         # 全局样式（markdown.css）
├── types/          # TypeScript 类型定义
└── views/          # 页面级组件（ChatView, SkillsView, ScheduledTasksView 等）
```

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Vue 3 + TypeScript |
| 桌面端 | Electron (electron-vite) |
| UI 组件 | shadcn-vue (基于 reka-ui) |
| 样式 | Tailwind CSS 4 (@tailwindcss/vite) |
| 图标 | lucide-vue-next |
| 状态管理 | Pinia |
| 路由 | Vue Router |
| HTTP | Axios |
| Markdown 渲染 | marked + marked-highlight + highlight.js |

## 路径别名

`@/` 指向 `src/renderer/src/`，在 `electron.vite.config.ts` 和 `tsconfig.web.json` 中配置。

```ts
// 示例
import { Button } from '@/components/ui/button'
import { useChatStore } from '@/stores/chat'
```
