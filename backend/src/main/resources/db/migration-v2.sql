CREATE SCHEMA IF NOT EXISTS agent_desk;

CREATE TABLE IF NOT EXISTS agent_desk.agent_state_v2
(
    user_id    VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    state_key  VARCHAR(128) NOT NULL,
    state_data JSONB        NOT NULL DEFAULT '{}',
    updated_at BIGINT       NOT NULL,
    PRIMARY KEY (user_id, session_id, state_key)
);

CREATE INDEX IF NOT EXISTS idx_agent_state_v2_session
    ON agent_desk.agent_state_v2 (user_id, session_id);

COMMENT ON TABLE agent_desk.agent_state_v2 IS 'AgentScope Java v2 state store';
COMMENT ON COLUMN agent_desk.agent_state_v2.user_id IS 'RuntimeContext userId; __anonymous__ for null';
COMMENT ON COLUMN agent_desk.agent_state_v2.session_id IS 'RuntimeContext sessionId';
COMMENT ON COLUMN agent_desk.agent_state_v2.state_key IS 'AgentScope state slot key';
COMMENT ON COLUMN agent_desk.agent_state_v2.state_data IS 'Serialized AgentScope State JSON';
COMMENT ON COLUMN agent_desk.agent_state_v2.updated_at IS 'Last update epoch milliseconds';
