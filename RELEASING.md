# 发布流程 / Release Guide

本文档描述 AgentDesk 项目的完整发布流程。每次发版请严格按照下列步骤操作。

---

## 📋 发版清单

| 步骤 | 检查项 |
|-----|-------|
| 1 | 撰写本次版本的 `releases/vX.Y.Z.md` 发布说明 |
| 2 | 确认 `frontend/package.json` 的 `version` **不等于**即将发布的版本号 |
| 3 | 本地跑 `npm run typecheck` 与 `npm run lint`，均必须通过 |
| 4 | 先推送 `main` 分支到远程，再基于远程 `main` HEAD 打 tag |
| 5 | 推送 tag，由 GitHub Actions 自动构建安装包并发布 Release |

---

## 🧭 完整步骤

### 1. 撰写版本说明

在 `releases/` 目录下新建 `vX.Y.Z.md`，面向**普通用户**用通俗语言描述本次更新：

```
releases/v1.1.0.md
```

内容结构参考既有文件 `releases/v1.0.0.md` / `releases/v1.1.0.md`，至少包含：

- 🚀 新功能
- ✨ 体验优化
- 🐛 问题修复
- 🔧 工程化改进
- 📋 版本信息

> 💡 CI 会自动拼接所有 `releases/*.md` 生成根目录 `CHANGELOG.md`，无需手动维护。

### 2. 检查 `frontend/package.json` 版本号

CI 中 `Sync version from tag` 步骤会执行 `npm version <tag-version> --no-git-tag-version`。若 `package.json` 当前版本与 tag 相同，此步骤会报 `Version not changed` 并失败。

**规则**：日常开发期间 `package.json` 始终保持为预览版后缀（如 `1.1.0-preview`、`1.2.0-preview`），由 CI 自动升级为正式版本号。

### 3. 本地验证（必做）

在**打 tag 之前**务必本地完成下列校验，避免 CI 反复失败浪费时间：

```powershell
cd W:\AgentDesk\frontend
npm run typecheck
npm run lint
```

- `typecheck` 必须 `0 errors`
- `lint` 允许 warnings，但不能有 errors

### 4. 提交改动并推送 main

```powershell
cd W:\AgentDesk

git add .
git commit -m "chore: prepare release vX.Y.Z"
git push origin main
```

### 5. 打 tag 并推送

**⚠️ 关键：必须先把 main 推送到远程，再打 tag**。CI 有安全校验：

```bash
# workflow 中的校验
if ! git branch -r --contains ${{ github.sha }} | grep -q 'origin/main'; then
  exit 1   # Tag 指向的 commit 必须在 origin/main 上
fi
```

```powershell
# 确保 main 已经推到远程
git checkout main
git pull origin main

# 基于最新 main 打 tag
git tag vX.Y.Z

# 推送 tag 触发 CI
git push origin vX.Y.Z
```

或使用一条命令同步推送：

```powershell
git push origin main --follow-tags
```

---

## 🤖 CI 自动完成的工作

推送 `v*` 格式的 tag 后，[.github/workflows/release.yml](./.github/workflows/release.yml) 会自动执行：

1. **校验 tag 基于 main** — 确保只有 main 分支的 commit 能发版
2. **同步版本号** — 将 `package.json` 的版本号同步为 tag 版本
3. **类型检查 & Lint** — `npm run typecheck` + `npm run lint`
4. **构建 Windows 安装包** — `npm run build:win`（最多重试 3 次）
5. **创建 GitHub Release** — 使用 `releases/vX.Y.Z.md` 作为发布说明，附带 `.exe` 安装包
6. **更新 CHANGELOG.md** — 聚合所有 `releases/*.md`，自动提交到 main（`[skip ci]`）

---

## 🛠️ 常见问题

### ❌ `npm error Version not changed`

**原因**：`frontend/package.json` 当前版本和 tag 版本完全一致。

**修复**：
- 把 `package.json` 版本改回 `X.Y.Z-preview`，提交后重新打 tag；或
- 改用更健壮的 workflow 逻辑（添加版本幂等判断）。

### ❌ `Tag is not on main branch`

**原因**：tag 指向的 commit 在本地 main，但还没推送到 `origin/main`。

**修复**：
```powershell
# 删除远程和本地 tag
git push origin :refs/tags/vX.Y.Z
git tag -d vX.Y.Z

# 先推 main，再重打 tag
git push origin main
git tag vX.Y.Z
git push origin vX.Y.Z
```

### ❌ CI typecheck / lint 失败

**原因**：未在本地验证就直接打 tag。

**修复**：
1. 本地修复问题
2. 删除旧 tag，提交修复，重新打 tag（参考上一节命令）

---

## 📚 相关文件

| 文件 | 说明 |
|-----|------|
| [.github/workflows/release.yml](./.github/workflows/release.yml) | 发布流水线定义 |
| [releases/](./releases/) | 各版本的发布说明 |
| [CHANGELOG.md](./CHANGELOG.md) | CI 自动生成的完整更新日志 |
| [frontend/package.json](./frontend/package.json) | 前端版本号，由 CI 按 tag 同步 |

---

## 🔑 核心口诀

> **本地先验 → 先推 main → 再打 tag → 最后推 tag**

记住这四步顺序，发版基本不会翻车。
