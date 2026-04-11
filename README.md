<p align="center">
  <img src="frontend/resources/icon_255_rounded.png" alt="AgentDesk" width="120">
</p>

<h1 align="center">AgentDesk (搭子)</h1>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/jionjion/AgentDesk" alt="License"></a>
  <a href="https://github.com/jionjion/AgentDesk/stargazers"><img src="https://img.shields.io/github/stars/jionjion/AgentDesk" alt="Stars"></a>
  <a href="https://github.com/jionjion/AgentDesk/issues"><img src="https://img.shields.io/github/issues/jionjion/AgentDesk" alt="Issues"></a>
  <a href="https://github.com/jionjion/AgentDesk/commits/main"><img src="https://img.shields.io/github/last-commit/jionjion/AgentDesk" alt="Last Commit"></a>
  <br>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-25-orange" alt="Java"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen" alt="Spring Boot"></a>
  <a href="https://vuejs.org/"><img src="https://img.shields.io/badge/Vue-3-4FC08D" alt="Vue"></a>
  <a href="https://www.electronjs.org/"><img src="https://img.shields.io/badge/Electron-33-47848F" alt="Electron"></a>
</p>

<p align="center">你的本地 AI 智能体工作台</p>

<p align="center">
  <a href="https://jionjion.github.io/AgentDesk/">&#x1f310; 项目主页</a>
</p>

AgentDesk 是一款桌面端智能体应用，让你通过自然语言对话驱动 AI Agent 完成各种任务。

## 你能用它做什么

**智能对话** — 与 AI Agent 自由对话，支持 Markdown 渲染和代码高亮，获得清晰易读的回答。

**工具调用** — Agent 可以调用外部工具执行操作，能力可按需扩展。Agent 之间也能相互协作（Agent as Tool）。

**技能管理** — 为 Agent 配置不同技能，让它胜任更多场景。

**定时任务** — 设置定时任务，让 Agent 在指定时间自动工作。

**全文检索** — 快速搜索历史对话，随时找回所需信息。

**聊天导出** — 一键导出聊天记录，方便归档和分享。

**桌面体验** — 原生桌面应用，支持系统托盘、自定义头像，开机即用。

## 快速开始

### 环境要求

- **Node.js** >= 18
- **Java** >= 25
- **PostgreSQL** >= 14
- **Maven** >= 3.6

### 1. 初始化数据库

在 PostgreSQL 中执行初始化脚本，创建 schema 和所有表：

```bash
psql -U postgres -f backend/src/main/resources/database.sql
```

### 2. 启动后端

```bash
cd backend

# 配置环境变量 (数据库连接、DashScope API Key、OSS 等)
cp .env.example .env
# 编辑 .env 文件填入实际配置

# 启动
mvn spring-boot:run
```

也可以用 Docker 部署后端：

```bash
cd backend
mvn clean package -DskipTests
docker build -f .docker/Dockerfile -t agentdesk-backend .
docker run -d -p 8080:8080 --name agentdesk-backend agentdesk-backend
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

构建 Windows 安装包：

```bash
cd frontend
npm run build:win
```

## 许可证

[Apache License 2.0](LICENSE)

## 作者

**Jion Jion** — [GitHub](https://github.com/jionjion)
