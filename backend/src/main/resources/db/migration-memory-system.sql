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
ALTER TABLE agent_desk.memory_jobs
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_desk.memory_jobs ALTER COLUMN user_message DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_memory_jobs_pending
    ON agent_desk.memory_jobs (status, next_attempt_at, created_at);

ALTER TABLE agent_desk.chat_messages
    ADD COLUMN IF NOT EXISTS memory_refs JSONB DEFAULT '{}';

ALTER TABLE agent_desk.chat_messages
    ADD COLUMN IF NOT EXISTS memory_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE agent_desk.sessions
    ADD COLUMN IF NOT EXISTS memory_mode VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
