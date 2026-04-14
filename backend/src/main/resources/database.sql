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

-- 10. 模型定义表
CREATE TABLE IF NOT EXISTS agent_desk.model_definitions
(
    id                 VARCHAR(64) PRIMARY KEY,
    display_name       VARCHAR(128) NOT NULL,
    group_name         VARCHAR(32)  NOT NULL,
    input_modalities   JSONB        NOT NULL DEFAULT '["text"]',
    supports_reasoning BOOLEAN      NOT NULL DEFAULT FALSE,
    max_context_window INTEGER      NOT NULL DEFAULT 131072,
    max_output_tokens  INTEGER      NOT NULL DEFAULT 8192,
    description        VARCHAR(256),
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         BIGINT       NOT NULL,
    updated_at         BIGINT       NOT NULL
);

COMMENT ON TABLE agent_desk.model_definitions IS '可用模型定义表';
COMMENT ON COLUMN agent_desk.model_definitions.id IS '模型ID, 即 DashScope API 的 model 参数';
COMMENT ON COLUMN agent_desk.model_definitions.display_name IS '前端展示名称';
COMMENT ON COLUMN agent_desk.model_definitions.group_name IS '分组名称, 如 旗舰模型、轻量快速';
COMMENT ON COLUMN agent_desk.model_definitions.input_modalities IS '支持的输入模态, JSON数组';
COMMENT ON COLUMN agent_desk.model_definitions.supports_reasoning IS '是否支持深度思考/推理';
COMMENT ON COLUMN agent_desk.model_definitions.max_context_window IS '最大上下文窗口(token)';
COMMENT ON COLUMN agent_desk.model_definitions.max_output_tokens IS '最大输出token数';
COMMENT ON COLUMN agent_desk.model_definitions.description IS '简要说明';
COMMENT ON COLUMN agent_desk.model_definitions.sort_order IS '排序权重, 越小越靠前';
COMMENT ON COLUMN agent_desk.model_definitions.enabled IS '是否启用, 关闭后前端不显示';

CREATE INDEX IF NOT EXISTS idx_model_definitions_enabled ON agent_desk.model_definitions (enabled, sort_order);

-- 初始化模型数据
INSERT INTO agent_desk.model_definitions (id, display_name, group_name, input_modalities, supports_reasoning, max_context_window, max_output_tokens, description, sort_order, enabled, created_at, updated_at)
VALUES
    -- 旗舰模型
    ('qwen3.6-plus',  'Qwen3.6 Plus',  '旗舰模型', '["text","image","video"]', TRUE,  1000000, 65536, '最新旗舰，多模态推理', 100, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen3.5-plus',  'Qwen3.5 Plus',  '旗舰模型', '["text","image","video"]', TRUE,  1000000, 65536, '上代旗舰多模态',     101, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen3-max',     'Qwen3 Max',     '旗舰模型', '["text"]',                TRUE,  262144,  65536, '文本推理最强',       102, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen-plus',     'Qwen Plus',     '旗舰模型', '["text"]',                TRUE,  1000000, 32768, '高性价比',           103, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    -- 轻量快速
    ('qwen3.5-flash', 'Qwen3.5 Flash', '轻量快速', '["text","image","video"]', TRUE,  1000000, 65536, '快速多模态',         200, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen-flash',    'Qwen Flash',    '轻量快速', '["text"]',                TRUE,  1000000, 32768, '极速文本',           201, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen-turbo',    'Qwen Turbo',    '轻量快速', '["text"]',                FALSE, 1000000, 8192,  '最快响应',           202, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    -- 代码专精
    ('qwen3-coder-plus',  'Qwen3 Coder Plus',  '代码专精', '["text"]', FALSE, 1000000, 65536, '代码生成 Plus',  300, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen3-coder-flash', 'Qwen3 Coder Flash', '代码专精', '["text"]', FALSE, 1000000, 65536, '代码生成 Flash', 301, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    -- 视觉理解
    ('qwen3-vl-plus',  'Qwen3 VL Plus',  '视觉理解', '["text","image","video"]', TRUE,  262144, 32768, '视觉理解 Plus',  400, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen3-vl-flash', 'Qwen3 VL Flash', '视觉理解', '["text","image","video"]', TRUE,  262144, 32768, '视觉理解 Flash', 401, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen-vl-max',    'Qwen VL Max',    '视觉理解', '["text","image","video"]', FALSE, 131072, 32768, '视觉旗舰',       402, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    -- 推理专精
    ('qwq-plus',            'QwQ Plus',            '推理专精', '["text"]', TRUE,  131072,  8192,  '深度推理',   500, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen-deep-research',  'Qwen Deep Research',  '推理专精', '["text"]', FALSE, 1000000, 32768, '深度研究',   501, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    -- 全模态
    ('qwen3.5-omni-plus',  'Qwen3.5 Omni Plus',  '全模态', '["text","image","video","audio"]', FALSE, 262144, 65536, '全模态 Plus',  600, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('qwen3.5-omni-flash', 'Qwen3.5 Omni Flash', '全模态', '["text","image","video","audio"]', FALSE, 262144, 65536, '全模态 Flash', 601, TRUE, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000)
ON CONFLICT (id) DO NOTHING;
