-- =============================================
-- AgentDesk 数据库初始化脚本
-- PostgreSQL 14+
-- 在默认 postgres 数据库下, 创建 agent_desk schema
-- =============================================

-- 1. 创建 Schema
CREATE SCHEMA IF NOT EXISTS agent_desk;

-- 2. 会话表
CREATE TABLE IF NOT EXISTS agent_desk.sessions (
    id          VARCHAR(32)  PRIMARY KEY,
    title       VARCHAR(256) NOT NULL DEFAULT '新对话',
    created_at  BIGINT       NOT NULL,
    last_used_at BIGINT      NOT NULL
);

COMMENT ON TABLE  agent_desk.sessions IS '会话元数据';
COMMENT ON COLUMN agent_desk.sessions.id IS '会话ID, 16位十六进制字符串';
COMMENT ON COLUMN agent_desk.sessions.title IS '会话标题';
COMMENT ON COLUMN agent_desk.sessions.created_at IS '创建时间戳(毫秒)';
COMMENT ON COLUMN agent_desk.sessions.last_used_at IS '最后使用时间戳(毫秒)';

CREATE INDEX IF NOT EXISTS idx_sessions_last_used ON agent_desk.sessions (last_used_at DESC);

-- 3. 对话消息表
CREATE TABLE IF NOT EXISTS agent_desk.chat_messages (
    id          BIGSERIAL    PRIMARY KEY,
    session_id  VARCHAR(32)  NOT NULL REFERENCES agent_desk.sessions(id) ON DELETE CASCADE,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT,
    tool_name   VARCHAR(128),
    tool_id     VARCHAR(128),
    arguments   JSONB,
    result      TEXT,
    created_at  BIGINT       NOT NULL
);

COMMENT ON TABLE  agent_desk.chat_messages IS '对话消息记录';
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
CREATE TABLE IF NOT EXISTS agent_desk.agent_state (
    session_id  VARCHAR(32)  NOT NULL REFERENCES agent_desk.sessions(id) ON DELETE CASCADE,
    state_key   VARCHAR(128) NOT NULL,
    state_data  JSONB        NOT NULL DEFAULT '{}',
    updated_at  BIGINT       NOT NULL,
    PRIMARY KEY (session_id, state_key)
);

COMMENT ON TABLE  agent_desk.agent_state IS 'Agent 序列化状态';
COMMENT ON COLUMN agent_desk.agent_state.session_id IS '会话ID';
COMMENT ON COLUMN agent_desk.agent_state.state_key IS '状态键名 (如 memory, toolkit 等)';
COMMENT ON COLUMN agent_desk.agent_state.state_data IS 'AgentScope 序列化的状态数据(JSON)';
COMMENT ON COLUMN agent_desk.agent_state.updated_at IS '最后更新时间戳(毫秒)';
