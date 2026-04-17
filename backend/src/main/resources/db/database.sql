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

-- 11. 技能表
CREATE TABLE IF NOT EXISTS agent_desk.skills
(
    id         VARCHAR(64) PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    description VARCHAR(512) NOT NULL,
    author     VARCHAR(128) NOT NULL DEFAULT 'AgentDesk',
    version    VARCHAR(32)  NOT NULL DEFAULT '1.0.0',
    category   VARCHAR(32)  NOT NULL DEFAULT 'other',
    tags       JSONB        NOT NULL DEFAULT '[]',
    icon       VARCHAR(64),
    bg_color   VARCHAR(16),
    sys_prompt TEXT         NOT NULL,
    max_iters  INTEGER      NOT NULL DEFAULT 3,
    tools      JSONB        NOT NULL DEFAULT '[]',
    builtin    BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id    BIGINT       REFERENCES agent_desk.users (id),
    created_at BIGINT       NOT NULL,
    updated_at BIGINT       NOT NULL
);

COMMENT ON TABLE agent_desk.skills IS '技能定义表';
COMMENT ON COLUMN agent_desk.skills.id IS '技能唯一标识, 小写字母+数字+连字符';
COMMENT ON COLUMN agent_desk.skills.name IS '显示名称';
COMMENT ON COLUMN agent_desk.skills.description IS '技能描述, 也是 Agent 委派依据';
COMMENT ON COLUMN agent_desk.skills.sys_prompt IS '子代理系统提示词';
COMMENT ON COLUMN agent_desk.skills.max_iters IS 'ReAct 最大迭代次数';
COMMENT ON COLUMN agent_desk.skills.tools IS '工具类名列表, JSON数组';
COMMENT ON COLUMN agent_desk.skills.builtin IS '是否内置技能';
COMMENT ON COLUMN agent_desk.skills.user_id IS '所属用户ID, 内置技能为 NULL';

CREATE INDEX IF NOT EXISTS idx_skills_user ON agent_desk.skills (user_id);
CREATE INDEX IF NOT EXISTS idx_skills_builtin ON agent_desk.skills (builtin);

-- 12. 用户技能偏好表
CREATE TABLE IF NOT EXISTS agent_desk.user_skill_preferences
(
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT      NOT NULL REFERENCES agent_desk.users (id),
    skill_id VARCHAR(64) NOT NULL,
    enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (user_id, skill_id)
);

COMMENT ON TABLE agent_desk.user_skill_preferences IS '用户技能启用/禁用偏好';

CREATE INDEX IF NOT EXISTS idx_user_skill_pref ON agent_desk.user_skill_preferences (user_id);

-- 初始化内置技能数据
INSERT INTO agent_desk.skills (id, name, description, author, version, category, tags, icon, bg_color, sys_prompt, max_iters, tools, builtin, user_id, created_at, updated_at)
VALUES
    ('file-analyzer', '文件分析专家', '文件分析专家。擅长读取和分析上传的文件：CSV结构解析、代码审查、配置文件校验、日志分析。当用户上传文件并需要分析时调用。',
     'AgentDesk', '1.0.0', 'file', '["文件", "分析"]', 'FileSearch', '#F3F4F6',
     '你是文件分析专家。你的职责是读取用户上传的文件并进行深入分析。
请先使用 read_file 工具获取文件内容，然后根据文件类型进行分析：
- CSV文件：描述列结构、数据类型、行数、数据质量问题
- 代码文件：进行代码审查，指出问题和改进建议
- 配置文件：校验格式正确性，指出潜在问题
- 日志文件：提取关键信息、错误模式、时间线
请用中文回答，分析要具体、有条理。',
     5, '["FileTools"]', TRUE, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),

    ('writer', '写作助手', '写作助手。擅长邮件撰写、文本润色、周报生成、技术文档编写。当用户需要写作相关帮助时调用。',
     'AgentDesk', '1.0.0', 'writing', '["写作", "邮件", "文档"]', 'FileEdit', '#F3F4F6',
     '你是专业写作助手。你的职责是帮助用户完成各类写作任务：
- 邮件撰写：根据场景调整语气，正式/非正式皆可
- 文本润色：改善表达、修正语法、提升可读性
- 周报生成：结构化、突出重点成果和计划
- 技术文档：清晰准确，适当使用示例
请用中文回答，注重文字质量和结构。',
     3, '[]', TRUE, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),

    ('coder', '编程助手', '编程助手。擅长代码生成、Bug分析、算法讲解、代码优化。当用户需要编程相关帮助时调用。',
     'AgentDesk', '1.0.0', 'coding', '["编程", "代码", "算法"]', 'Code', '#F3F4F6',
     '你是编程助手。你的职责是帮助用户解决编程问题：
- 代码生成：根据需求生成高质量代码，包含注释
- Bug分析：分析代码问题，给出修复方案
- 算法讲解：用清晰的方式解释算法原理和复杂度
- 代码优化：改善性能、可读性和最佳实践
你可以使用 calculate 工具验证数学计算。
请用中文回答，代码块使用合适的语言标记。',
     5, '["CalculateTools"]', TRUE, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),

    ('data-analyst', '数据分析师', '数据分析师。擅长数据统计、趋势分析、异常检测、生成分析结论。当用户上传数据并需要分析洞察时调用。',
     'AgentDesk', '1.0.0', 'data', '["数据", "统计", "分析"]', 'BarChart3', '#F3F4F6',
     '你是数据分析师。你的职责是分析用户提供的数据并给出洞察：
- 数据统计：计算均值、中位数、分布等基本统计量
- 趋势分析：识别数据中的趋势和模式
- 异常检测：发现异常值和数据质量问题
- 分析结论：基于数据给出可执行的建议
请先使用 read_file 工具获取数据内容，然后进行分析。
请用中文回答，使用结构化的格式呈现分析结果。',
     5, '["FileTools"]', TRUE, NULL, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000)
ON CONFLICT (id) DO NOTHING;

-- 13. 定时任务表
CREATE TABLE IF NOT EXISTS agent_desk.scheduled_tasks
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES agent_desk.users (id),
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    prompt          TEXT         NOT NULL,
    cron_expression VARCHAR(64)  NOT NULL,
    schedule_label  VARCHAR(64),
    skill_id        VARCHAR(64),
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL
);

COMMENT ON TABLE agent_desk.scheduled_tasks IS '定时任务表';
COMMENT ON COLUMN agent_desk.scheduled_tasks.user_id IS '所属用户ID';
COMMENT ON COLUMN agent_desk.scheduled_tasks.name IS '任务名称';
COMMENT ON COLUMN agent_desk.scheduled_tasks.description IS '任务描述';
COMMENT ON COLUMN agent_desk.scheduled_tasks.prompt IS '发送给 Agent 的提示词';
COMMENT ON COLUMN agent_desk.scheduled_tasks.cron_expression IS 'Spring 6字段 cron 表达式';
COMMENT ON COLUMN agent_desk.scheduled_tasks.schedule_label IS '人类可读的调度描述, 如 每天 09:30';
COMMENT ON COLUMN agent_desk.scheduled_tasks.skill_id IS '可选关联技能ID';
COMMENT ON COLUMN agent_desk.scheduled_tasks.enabled IS '是否启用';

CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_user ON agent_desk.scheduled_tasks (user_id);

-- 14. 定时任务执行记录表
CREATE TABLE IF NOT EXISTS agent_desk.scheduled_task_logs
(
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT      NOT NULL REFERENCES agent_desk.scheduled_tasks (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES agent_desk.users (id),
    status     VARCHAR(16) NOT NULL,
    result     TEXT,
    duration   BIGINT      NOT NULL DEFAULT 0,
    started_at BIGINT      NOT NULL,
    ended_at   BIGINT
);

COMMENT ON TABLE agent_desk.scheduled_task_logs IS '定时任务执行记录';
COMMENT ON COLUMN agent_desk.scheduled_task_logs.task_id IS '所属定时任务ID';
COMMENT ON COLUMN agent_desk.scheduled_task_logs.user_id IS '所属用户ID';
COMMENT ON COLUMN agent_desk.scheduled_task_logs.status IS '执行状态: SUCCESS / FAILED / RUNNING';
COMMENT ON COLUMN agent_desk.scheduled_task_logs.result IS 'Agent 回复内容或错误信息';
COMMENT ON COLUMN agent_desk.scheduled_task_logs.duration IS '执行耗时(毫秒)';
COMMENT ON COLUMN agent_desk.scheduled_task_logs.started_at IS '开始时间戳(毫秒)';
COMMENT ON COLUMN agent_desk.scheduled_task_logs.ended_at IS '结束时间戳(毫秒)';

CREATE INDEX IF NOT EXISTS idx_task_logs_task ON agent_desk.scheduled_task_logs (task_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_logs_user ON agent_desk.scheduled_task_logs (user_id, started_at DESC);
