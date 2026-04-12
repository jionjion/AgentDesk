-- =============================================
-- AgentDesk 数据库初始化脚本
-- PostgreSQL 14+
-- 在默认 postgres 数据库下, 创建 agent_desk schema
-- =============================================

-- 1. 创建 Schema
CREATE SCHEMA IF NOT EXISTS agent_desk;

-- 2. 会话表
CREATE TABLE IF NOT EXISTS agent_desk.sessions
(
    id           VARCHAR(32) PRIMARY KEY,
    title        VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at   BIGINT       NOT NULL,
    last_used_at BIGINT       NOT NULL
);

COMMENT ON TABLE agent_desk.sessions IS '会话元数据';
COMMENT ON COLUMN agent_desk.sessions.id IS '会话ID, 16位十六进制字符串';
COMMENT ON COLUMN agent_desk.sessions.title IS '会话标题';
COMMENT ON COLUMN agent_desk.sessions.created_at IS '创建时间戳(毫秒)';
COMMENT ON COLUMN agent_desk.sessions.last_used_at IS '最后使用时间戳(毫秒)';

CREATE INDEX IF NOT EXISTS idx_sessions_last_used ON agent_desk.sessions (last_used_at DESC);

-- 3. 对话消息表
CREATE TABLE IF NOT EXISTS agent_desk.chat_messages
(
    id         BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(32) NOT NULL REFERENCES agent_desk.sessions (id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL,
    content    TEXT,
    tool_name  VARCHAR(128),
    tool_id    VARCHAR(128),
    arguments  JSONB,
    result     TEXT,
    created_at BIGINT      NOT NULL
);

COMMENT ON TABLE agent_desk.chat_messages IS '对话消息记录';
COMMENT ON COLUMN agent_desk.chat_messages.session_id IS '所属会话ID';
COMMENT ON COLUMN agent_desk.chat_messages.role IS '消息角色: user / assistant / tool';
COMMENT ON COLUMN agent_desk.chat_messages.content IS '消息文本内容';
COMMENT ON COLUMN agent_desk.chat_messages.tool_name IS '工具名称(仅tool角色)';
COMMENT ON COLUMN agent_desk.chat_messages.tool_id IS '工具调用ID(仅tool角色)';
COMMENT ON COLUMN agent_desk.chat_messages.arguments IS '工具调用参数(JSON)';
COMMENT ON COLUMN agent_desk.chat_messages.result IS '工具执行结果(仅tool角色)';
COMMENT ON COLUMN agent_desk.chat_messages.created_at IS '消息创建时间戳(毫秒)';

CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON agent_desk.chat_messages (session_id, created_at);

-- 4. Agent 状态表 (存储 AgentScope 序列化的状态)
CREATE TABLE IF NOT EXISTS agent_desk.agent_state
(
    session_id VARCHAR(32)  NOT NULL REFERENCES agent_desk.sessions (id) ON DELETE CASCADE,
    state_key  VARCHAR(128) NOT NULL,
    state_data JSONB        NOT NULL DEFAULT '{}',
    updated_at BIGINT       NOT NULL,
    PRIMARY KEY (session_id, state_key)
);

COMMENT ON TABLE agent_desk.agent_state IS 'Agent 序列化状态';
COMMENT ON COLUMN agent_desk.agent_state.session_id IS '会话ID';
COMMENT ON COLUMN agent_desk.agent_state.state_key IS '状态键名 (如 memory, toolkit 等)';
COMMENT ON COLUMN agent_desk.agent_state.state_data IS 'AgentScope 序列化的状态数据(JSON)';
COMMENT ON COLUMN agent_desk.agent_state.updated_at IS '最后更新时间戳(毫秒)';

-- 5. 用户表
CREATE TABLE IF NOT EXISTS agent_desk.users
(
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL UNIQUE,
    password   VARCHAR(256) NOT NULL,
    nickname   VARCHAR(128),
    avatar     VARCHAR(512),
    created_at BIGINT       NOT NULL,
    updated_at BIGINT       NOT NULL
);

COMMENT ON TABLE agent_desk.users IS '用户表';
COMMENT ON COLUMN agent_desk.users.username IS '登录用户名, 唯一';
COMMENT ON COLUMN agent_desk.users.password IS '密码, BCrypt 加密';
COMMENT ON COLUMN agent_desk.users.nickname IS '显示昵称';
COMMENT ON COLUMN agent_desk.users.avatar IS '头像 URL';

-- 6. 文件表
CREATE TABLE IF NOT EXISTS agent_desk.files
(
    id            BIGSERIAL PRIMARY KEY,
    original_name VARCHAR(512)  NOT NULL,
    oss_key       VARCHAR(1024) NOT NULL,
    content_type  VARCHAR(128),
    size          BIGINT        NOT NULL DEFAULT 0,
    session_id    VARCHAR(32)   REFERENCES agent_desk.sessions (id) ON DELETE SET NULL,
    created_at    BIGINT        NOT NULL
);

COMMENT ON TABLE agent_desk.files IS '文件上传记录';
COMMENT ON COLUMN agent_desk.files.original_name IS '原始文件名';
COMMENT ON COLUMN agent_desk.files.oss_key IS 'OSS 对象键';
COMMENT ON COLUMN agent_desk.files.content_type IS 'MIME 类型';
COMMENT ON COLUMN agent_desk.files.size IS '文件大小(字节)';
COMMENT ON COLUMN agent_desk.files.session_id IS '关联会话ID(可为空, 空表示通用上传)';
COMMENT ON COLUMN agent_desk.files.created_at IS '创建时间戳(毫秒)';

CREATE INDEX IF NOT EXISTS idx_files_session ON agent_desk.files (session_id);
CREATE INDEX IF NOT EXISTS idx_files_oss_key ON agent_desk.files (oss_key);

-- 7. 现有表增加 user_id 关联
ALTER TABLE agent_desk.sessions
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES agent_desk.users (id);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON agent_desk.sessions (user_id);

ALTER TABLE agent_desk.files
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES agent_desk.users (id);
CREATE INDEX IF NOT EXISTS idx_files_user ON agent_desk.files (user_id);

-- 8. chat_messages 增加 file_ids 字段 (存储用户消息附带的文件ID列表)
ALTER TABLE agent_desk.chat_messages
    ADD COLUMN IF NOT EXISTS file_ids JSONB DEFAULT NULL;
COMMENT ON COLUMN agent_desk.chat_messages.file_ids IS '附件文件ID列表, JSON数组格式, 仅user角色消息使用';

-- 9. 用户设置表
CREATE TABLE IF NOT EXISTS agent_desk.user_settings
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES agent_desk.users (id),
    settings   JSONB  NOT NULL DEFAULT '{}',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

COMMENT ON TABLE agent_desk.user_settings IS '用户设置表';
COMMENT ON COLUMN agent_desk.user_settings.user_id IS '用户 ID, 一对一';
COMMENT ON COLUMN agent_desk.user_settings.settings IS '设置内容, JSON 格式';

CREATE INDEX IF NOT EXISTS idx_user_settings_user ON agent_desk.user_settings (user_id);
