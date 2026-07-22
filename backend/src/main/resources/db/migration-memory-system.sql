-- 适用于现有安装的AgentDesk标准内存系统迁移。

CREATE TABLE IF NOT EXISTS agent_desk.memory_entries
(
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES agent_desk.users (id) ON DELETE CASCADE,
    scope_type VARCHAR(16) NOT NULL DEFAULT 'USER',
    scope_id VARCHAR(64),
    category VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    content TEXT NOT NULL,
    canonical_content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sensitivity VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    write_policy VARCHAR(16) NOT NULL DEFAULT 'EXPLICIT',
    subject_key VARCHAR(128),
    supersedes_id BIGINT REFERENCES agent_desk.memory_entries (id) ON DELETE SET NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    importance DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    provider_name VARCHAR(32),
    provider_ref VARCHAR(128),
    provider_sync_pending BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from BIGINT,
    valid_until BIGINT,
    last_recalled_at BIGINT,
    recall_count BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT
);

-- ==================== memory_entries 表注释 ====================
COMMENT ON TABLE agent_desk.memory_entries IS '内存条目表：存储用户/项目的记忆条目，支持版本控制、过期、置信度评估';
COMMENT ON COLUMN agent_desk.memory_entries.id IS '主键ID';
COMMENT ON COLUMN agent_desk.memory_entries.version IS '乐观锁版本号';
COMMENT ON COLUMN agent_desk.memory_entries.user_id IS '所属用户ID';
COMMENT ON COLUMN agent_desk.memory_entries.scope_type IS '作用域类型：USER-用户级记忆，PROJECT-项目级记忆';
COMMENT ON COLUMN agent_desk.memory_entries.scope_id IS '作用域ID，scope_type=PROJECT时关联项目ID，USER时为NULL';
COMMENT ON COLUMN agent_desk.memory_entries.category IS '记忆分类：PREFERENCE-偏好，FACT-事实，SKILL-技能，CONTEXT-上下文，OTHER-其他';
COMMENT ON COLUMN agent_desk.memory_entries.content IS '原始记忆内容';
COMMENT ON COLUMN agent_desk.memory_entries.canonical_content IS '规范化后的内容，用于去重比对';
COMMENT ON COLUMN agent_desk.memory_entries.content_hash IS '内容哈希值，用于唯一索引去重';
COMMENT ON COLUMN agent_desk.memory_entries.status IS '状态：ACTIVE-活跃，ARCHIVED-已归档，EXPIRED-已过期，SUPERSEDED-已被取代';
COMMENT ON COLUMN agent_desk.memory_entries.sensitivity IS '敏感级别：LOW-低，NORMAL-普通，HIGH-高，CRITICAL-关键';
COMMENT ON COLUMN agent_desk.memory_entries.write_policy IS '写入策略：EXPLICIT-用户显式写入，AUTO-系统自动提取，INFERRED-系统推断';
COMMENT ON COLUMN agent_desk.memory_entries.subject_key IS '主题键，用于按主题分组查询关联记忆';
COMMENT ON COLUMN agent_desk.memory_entries.supersedes_id IS '取代的旧记忆ID，指向被本条记忆替换的旧记录';
COMMENT ON COLUMN agent_desk.memory_entries.confidence IS '置信度，范围0.0到1.0，表示系统对该记忆的确定程度';
COMMENT ON COLUMN agent_desk.memory_entries.importance IS '重要程度，范围0.0到1.0，值越大表示记忆越重要';
COMMENT ON COLUMN agent_desk.memory_entries.pinned IS '是否置顶，置顶记忆不会被自动清理';
COMMENT ON COLUMN agent_desk.memory_entries.provider_name IS '外部提供者名称，如mem0等第三方记忆服务';
COMMENT ON COLUMN agent_desk.memory_entries.provider_ref IS '外部提供者引用ID，用于关联外部系统的记忆记录';
COMMENT ON COLUMN agent_desk.memory_entries.provider_sync_pending IS '是否待同步到外部提供者';
COMMENT ON COLUMN agent_desk.memory_entries.valid_from IS '有效期起始时间戳，毫秒';
COMMENT ON COLUMN agent_desk.memory_entries.valid_until IS '有效期截止时间戳，毫秒，过期后状态标记为EXPIRED';
COMMENT ON COLUMN agent_desk.memory_entries.last_recalled_at IS '最后召回时间戳，毫秒，记录最近一次被检索使用的时间';
COMMENT ON COLUMN agent_desk.memory_entries.recall_count IS '累计召回次数';
COMMENT ON COLUMN agent_desk.memory_entries.created_at IS '创建时间戳，毫秒';
COMMENT ON COLUMN agent_desk.memory_entries.updated_at IS '更新时间戳，毫秒';
COMMENT ON COLUMN agent_desk.memory_entries.deleted_at IS '软删除时间戳，毫秒，非NULL表示已删除';

ALTER TABLE agent_desk.memory_entries ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_desk.memory_entries ADD COLUMN IF NOT EXISTS sensitivity VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE agent_desk.memory_entries ADD COLUMN IF NOT EXISTS write_policy VARCHAR(16) NOT NULL DEFAULT 'EXPLICIT';
ALTER TABLE agent_desk.memory_entries ADD COLUMN IF NOT EXISTS subject_key VARCHAR(128);
ALTER TABLE agent_desk.memory_entries ADD COLUMN IF NOT EXISTS supersedes_id BIGINT;
ALTER TABLE agent_desk.memory_entries ADD COLUMN IF NOT EXISTS valid_from BIGINT;
ALTER TABLE agent_desk.memory_entries ADD COLUMN IF NOT EXISTS provider_sync_pending BOOLEAN NOT NULL DEFAULT FALSE;
DO $$ BEGIN
    ALTER TABLE agent_desk.memory_entries ADD CONSTRAINT fk_memory_entries_supersedes
        FOREIGN KEY (supersedes_id) REFERENCES agent_desk.memory_entries (id) ON DELETE SET NULL;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE agent_desk.memory_entries ADD CONSTRAINT chk_memory_entries_scope
        CHECK ((scope_type = 'USER' AND scope_id IS NULL)
            OR (scope_type = 'PROJECT' AND scope_id IS NOT NULL));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE INDEX IF NOT EXISTS idx_memory_entries_user_active
    ON agent_desk.memory_entries (user_id, status, pinned DESC, updated_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_entries_active_hash
    ON agent_desk.memory_entries (user_id, scope_type, COALESCE(scope_id, ''), content_hash)
    WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_memory_entries_subject
    ON agent_desk.memory_entries (user_id, scope_type, scope_id, subject_key, status)
    WHERE subject_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_memory_entries_expiry
    ON agent_desk.memory_entries (status, valid_until)
    WHERE valid_until IS NOT NULL;

CREATE TABLE IF NOT EXISTS agent_desk.memory_sources
(
    id BIGSERIAL PRIMARY KEY,
    memory_id BIGINT NOT NULL REFERENCES agent_desk.memory_entries (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES agent_desk.users (id) ON DELETE CASCADE,
    source_type VARCHAR(24) NOT NULL,
    source_id VARCHAR(128),
    session_id VARCHAR(64),
    project_id VARCHAR(64),
    evidence_excerpt TEXT,
    trust_level VARCHAR(16) NOT NULL DEFAULT 'USER',
    created_at BIGINT NOT NULL
);

-- ==================== memory_sources 表注释 ====================
COMMENT ON TABLE agent_desk.memory_sources IS '记忆来源表：记录每条记忆的出处信息，支持溯源和可信度评估';
COMMENT ON COLUMN agent_desk.memory_sources.id IS '主键ID';
COMMENT ON COLUMN agent_desk.memory_sources.memory_id IS '关联的记忆条目ID';
COMMENT ON COLUMN agent_desk.memory_sources.user_id IS '所属用户ID';
COMMENT ON COLUMN agent_desk.memory_sources.source_type IS '来源类型：USER_INPUT-用户输入，CONVERSATION-对话提取，INFERENCE-系统推断，IMPORT-外部导入';
COMMENT ON COLUMN agent_desk.memory_sources.source_id IS '来源标识，如消息ID或文件路径';
COMMENT ON COLUMN agent_desk.memory_sources.session_id IS '关联的会话ID，记录记忆从哪次对话中产生';
COMMENT ON COLUMN agent_desk.memory_sources.project_id IS '关联的项目ID';
COMMENT ON COLUMN agent_desk.memory_sources.evidence_excerpt IS '证据摘录，保留原始对话片段以便追溯';
COMMENT ON COLUMN agent_desk.memory_sources.trust_level IS '信任级别：USER-用户直接提供（最高），SYSTEM-系统提取，EXTERNAL-外部导入（最低）';
COMMENT ON COLUMN agent_desk.memory_sources.created_at IS '创建时间戳，毫秒';

CREATE INDEX IF NOT EXISTS idx_memory_sources_memory ON agent_desk.memory_sources (memory_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_sources_session ON agent_desk.memory_sources (user_id, session_id);

CREATE TABLE IF NOT EXISTS agent_desk.memory_revisions
(
    id BIGSERIAL PRIMARY KEY,
    memory_id BIGINT NOT NULL REFERENCES agent_desk.memory_entries (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES agent_desk.users (id) ON DELETE CASCADE,
    old_content TEXT,
    new_content TEXT,
    reason VARCHAR(32) NOT NULL,
    actor VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL
);

-- ==================== memory_revisions 表注释 ====================
COMMENT ON TABLE agent_desk.memory_revisions IS '记忆修订表：记录每次记忆内容的修改历史，支持审计追溯';
COMMENT ON COLUMN agent_desk.memory_revisions.id IS '主键ID';
COMMENT ON COLUMN agent_desk.memory_revisions.memory_id IS '关联的记忆条目ID';
COMMENT ON COLUMN agent_desk.memory_revisions.user_id IS '操作用户ID';
COMMENT ON COLUMN agent_desk.memory_revisions.old_content IS '修改前的内容，首次创建时为NULL';
COMMENT ON COLUMN agent_desk.memory_revisions.new_content IS '修改后的内容';
COMMENT ON COLUMN agent_desk.memory_revisions.reason IS '修改原因：UPDATE-更新，CORRECTION-纠正，SUPERSEDE-替代，MERGE-合并';
COMMENT ON COLUMN agent_desk.memory_revisions.actor IS '修改者：USER-用户，SYSTEM-系统，AGENT-智能体';
COMMENT ON COLUMN agent_desk.memory_revisions.created_at IS '修订时间戳，毫秒';

CREATE INDEX IF NOT EXISTS idx_memory_revisions_memory ON agent_desk.memory_revisions (memory_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_desk.memory_usage_events
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES agent_desk.users (id) ON DELETE CASCADE,
    memory_id BIGINT NOT NULL REFERENCES agent_desk.memory_entries (id) ON DELETE CASCADE,
    project_id VARCHAR(64),
    reason VARCHAR(32) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    elapsed_ms BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

-- ==================== memory_usage_events 表注释 ====================
COMMENT ON TABLE agent_desk.memory_usage_events IS '记忆使用事件表：记录每次记忆被召回/使用的详情，用于统计分析';
COMMENT ON COLUMN agent_desk.memory_usage_events.id IS '主键ID';
COMMENT ON COLUMN agent_desk.memory_usage_events.user_id IS '用户ID';
COMMENT ON COLUMN agent_desk.memory_usage_events.memory_id IS '被使用的记忆条目ID';
COMMENT ON COLUMN agent_desk.memory_usage_events.project_id IS '使用时的项目ID';
COMMENT ON COLUMN agent_desk.memory_usage_events.reason IS '召回原因：CHAT-对话中自动召回，AUTO_RECALL-后台自动召回，MANUAL_SEARCH-手动搜索';
COMMENT ON COLUMN agent_desk.memory_usage_events.score IS '相关性评分，表示该记忆与查询的匹配程度';
COMMENT ON COLUMN agent_desk.memory_usage_events.elapsed_ms IS '检索耗时，毫秒';
COMMENT ON COLUMN agent_desk.memory_usage_events.created_at IS '事件时间戳，毫秒';

CREATE INDEX IF NOT EXISTS idx_memory_usage_user_time
    ON agent_desk.memory_usage_events (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_usage_memory
    ON agent_desk.memory_usage_events (memory_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_desk.memory_jobs
(
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL REFERENCES agent_desk.users (id) ON DELETE CASCADE,
    session_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64),
    user_message_id BIGINT NOT NULL,
    user_message TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at BIGINT NOT NULL,
    last_error TEXT,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

-- ==================== memory_jobs 表注释 ====================
COMMENT ON TABLE agent_desk.memory_jobs IS '记忆异步任务表：管理后台记忆提取与处理的异步任务队列';
COMMENT ON COLUMN agent_desk.memory_jobs.id IS '主键ID';
COMMENT ON COLUMN agent_desk.memory_jobs.version IS '乐观锁版本号，防止并发更新冲突';
COMMENT ON COLUMN agent_desk.memory_jobs.user_id IS '用户ID';
COMMENT ON COLUMN agent_desk.memory_jobs.session_id IS '关联的会话ID，任务从哪次会话中产生';
COMMENT ON COLUMN agent_desk.memory_jobs.project_id IS '关联的项目ID';
COMMENT ON COLUMN agent_desk.memory_jobs.user_message_id IS '关联的用户消息ID，指向触发任务的原始消息';
COMMENT ON COLUMN agent_desk.memory_jobs.user_message IS '用户消息内容快照';
COMMENT ON COLUMN agent_desk.memory_jobs.status IS '任务状态：PENDING-等待处理，PROCESSING-处理中，COMPLETED-已完成，FAILED-失败';
COMMENT ON COLUMN agent_desk.memory_jobs.attempts IS '已重试次数';
COMMENT ON COLUMN agent_desk.memory_jobs.next_attempt_at IS '下次重试时间戳，毫秒，用于延迟重试队列';
COMMENT ON COLUMN agent_desk.memory_jobs.last_error IS '最后一次失败的错误信息';
COMMENT ON COLUMN agent_desk.memory_jobs.idempotency_key IS '幂等键，防止重复提交同一任务';
COMMENT ON COLUMN agent_desk.memory_jobs.created_at IS '创建时间戳，毫秒';
COMMENT ON COLUMN agent_desk.memory_jobs.updated_at IS '更新时间戳，毫秒';

ALTER TABLE agent_desk.memory_jobs
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_desk.memory_jobs ALTER COLUMN user_message DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_memory_jobs_pending
    ON agent_desk.memory_jobs (status, next_attempt_at, created_at);

ALTER TABLE agent_desk.chat_messages
    ADD COLUMN IF NOT EXISTS memory_refs JSONB DEFAULT '{}';

COMMENT ON COLUMN agent_desk.chat_messages.memory_refs IS '记忆引用：JSONB格式存储消息关联的记忆ID列表及元数据';

ALTER TABLE agent_desk.chat_messages
    ADD COLUMN IF NOT EXISTS memory_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

COMMENT ON COLUMN agent_desk.chat_messages.memory_mode IS '消息级记忆模式：NORMAL-正常使用记忆，NOMEMORY-不使用记忆，MEMORYONLY-仅使用记忆不容纳新知识';

ALTER TABLE agent_desk.sessions
    ADD COLUMN IF NOT EXISTS memory_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

COMMENT ON COLUMN agent_desk.sessions.memory_mode IS '会话级记忆模式：NORMAL-正常使用记忆，NOMEMORY-不使用记忆，MEMORYONLY-仅使用记忆不容纳新知识';
