package top.jionjion.agentdesk.agent.core;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilteredSkillRepositoryTest {

    @Test
    void exposesOnlySkillsEnabledByUserPreference() {
        AgentSkillRepository delegate = mock(AgentSkillRepository.class);
        AgentSkill coder = mock(AgentSkill.class);
        AgentSkill writer = mock(AgentSkill.class);
        when(coder.getName()).thenReturn("coder");
        when(writer.getName()).thenReturn("writer");
        when(delegate.getAllSkillNames()).thenReturn(List.of("coder", "writer"));
        when(delegate.getAllSkills()).thenReturn(List.of(coder, writer));

        FilteredSkillRepository repository = new FilteredSkillRepository(
                delegate, Set.of("coder"));

        assertEquals(List.of("coder"), repository.getAllSkillNames());
        assertEquals(List.of(coder), repository.getAllSkills());
        assertThrows(IllegalArgumentException.class, () -> repository.getSkill("writer"));
    }
}
