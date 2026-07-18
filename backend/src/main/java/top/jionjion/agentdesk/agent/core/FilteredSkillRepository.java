package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only repository view that exposes only skills enabled by the current user. */
final class FilteredSkillRepository implements AgentSkillRepository {

    private final AgentSkillRepository delegate;
    private final Set<String> enabledNames;

    FilteredSkillRepository(AgentSkillRepository delegate, Set<String> enabledNames) {
        this.delegate = Objects.requireNonNull(delegate);
        this.enabledNames = Set.copyOf(enabledNames);
    }

    @Override
    public AgentSkill getSkill(String name) {
        if (!enabledNames.contains(name)) {
            throw new IllegalArgumentException("Skill is disabled: " + name);
        }
        return delegate.getSkill(name);
    }

    @Override
    public List<String> getAllSkillNames() {
        return delegate.getAllSkillNames().stream().filter(enabledNames::contains).toList();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return delegate.getAllSkills().stream()
                .filter(skill -> enabledNames.contains(skill.getName()))
                .toList();
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean force) {
        return false;
    }

    @Override
    public boolean delete(String skillName) {
        return false;
    }

    @Override
    public boolean skillExists(String skillName) {
        return enabledNames.contains(skillName) && delegate.skillExists(skillName);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return delegate.getRepositoryInfo();
    }

    @Override
    public String getSource() {
        return delegate.getSource();
    }

    @Override
    public void setWriteable(boolean writeable) {
        if (writeable) {
            throw new UnsupportedOperationException("Filtered skill repository is read-only");
        }
    }

    @Override
    public boolean isWriteable() {
        return false;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
