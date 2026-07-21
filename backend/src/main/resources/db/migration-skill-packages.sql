-- 将历史内置 prompt 技能迁移为 AgentScope SKILL.md 能力包。
-- 用户自己创建的旧版 prompt 技能继续保留兼容，不再提供新的创建入口。
UPDATE agent_desk.skills
SET skill_type = 'package',
    install_path = 'classpath:skills/' || id,
    sys_prompt = ''
WHERE builtin = TRUE
  AND id IN ('file-analyzer', 'writer', 'coder', 'data-analyst');

COMMENT ON COLUMN agent_desk.skills.sys_prompt IS '旧版 prompt 技能兼容字段';
COMMENT ON COLUMN agent_desk.skills.max_iters IS '旧版 prompt 技能兼容字段';
