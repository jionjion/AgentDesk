-- 本地运行集与项目实体改造: 增量迁移脚本
-- 适用: 已存在 database.sql 基础结构的现有数据库
-- 见 docs/LOCAL_RUNTIME_PROJECT_DEVELOPMENT_PLAN.md 第 5 章

-- 1. 项目表
CREATE TABLE IF NOT EXISTS agent_desk.projects
(
    id           VARCHAR(32) PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES agent_desk.users (id) ON DELETE CASCADE,
    name         VARCHAR(256) NOT NULL,
    description  TEXT,
    instructions TEXT,
    created_at   BIGINT       NOT NULL,
    updated_at   BIGINT       NOT NULL
);

COMMENT ON TABLE agent_desk.projects IS '项目实体, 可绑定多个会话; 本地路径保存在设备端, 不入库';
COMMENT ON COLUMN agent_desk.projects.id IS '项目ID, 16位十六进制字符串';
COMMENT ON COLUMN agent_desk.projects.user_id IS '所属用户ID';
COMMENT ON COLUMN agent_desk.projects.name IS '项目名称';
COMMENT ON COLUMN agent_desk.projects.description IS '项目说明';
COMMENT ON COLUMN agent_desk.projects.instructions IS '项目级指令, 注入 Agent 上下文';
COMMENT ON COLUMN agent_desk.projects.created_at IS '创建时间戳(毫秒)';
COMMENT ON COLUMN agent_desk.projects.updated_at IS '更新时间戳(毫秒)';

CREATE INDEX IF NOT EXISTS idx_projects_user_updated
    ON agent_desk.projects (user_id, updated_at DESC);

-- 2. 会话关联项目 (可空; 删除项目时置空, 不删除会话)
ALTER TABLE agent_desk.sessions
    ADD COLUMN IF NOT EXISTS project_id VARCHAR(32)
        REFERENCES agent_desk.projects (id) ON DELETE SET NULL;

COMMENT ON COLUMN agent_desk.sessions.project_id IS '可选关联项目ID';

CREATE INDEX IF NOT EXISTS idx_sessions_project
    ON agent_desk.sessions (project_id, last_used_at DESC);

-- 3. 定时任务关联项目与目标设备 (可空)
ALTER TABLE agent_desk.scheduled_tasks
    ADD COLUMN IF NOT EXISTS project_id VARCHAR(32)
        REFERENCES agent_desk.projects (id) ON DELETE SET NULL;

ALTER TABLE agent_desk.scheduled_tasks
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(128);

COMMENT ON COLUMN agent_desk.scheduled_tasks.project_id IS '可选关联项目ID; 置空后任务按无项目任务运行';
COMMENT ON COLUMN agent_desk.scheduled_tasks.device_id IS '目标设备ID; 与 project_id 同时保存, 不在触发时猜测在线设备';

CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_project
    ON agent_desk.scheduled_tasks (project_id);
