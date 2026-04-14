package top.jionjion.agentdesk.controller;

import org.springframework.web.bind.annotation.*;
import top.jionjion.agentdesk.agent.AgentPool;
import top.jionjion.agentdesk.dto.SkillDefinitionDto;
import top.jionjion.agentdesk.dto.SkillEnabledRequest;
import top.jionjion.agentdesk.dto.SkillResponseDto;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.SkillService;

import java.util.List;
import java.util.Map;

/**
 * 技能控制器: 技能的增删改查与启用/禁用
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;
    private final AgentPool agentPool;

    public SkillController(SkillService skillService, AgentPool agentPool) {
        this.skillService = skillService;
        this.agentPool = agentPool;
    }

    /**
     * 列出所有技能（内置 + 用户安装），含启用状态
     */
    @GetMapping
    public List<SkillResponseDto> list() {
        return skillService.listSkills(UserContext.getUserId());
    }

    /**
     * 获取技能详情
     */
    @GetMapping("/{skillId}")
    public SkillResponseDto get(@PathVariable String skillId) {
        return skillService.getSkill(skillId, UserContext.getUserId());
    }

    /**
     * 从 Electron 同步/上传技能定义（upsert）
     */
    @PostMapping("/sync")
    public SkillResponseDto sync(@RequestBody SkillDefinitionDto request) {
        Long userId = UserContext.getUserId();
        SkillResponseDto result = skillService.syncSkill(request, userId);
        agentPool.invalidateAll(userId);
        return result;
    }

    /**
     * 启用/禁用技能
     */
    @PutMapping("/{skillId}/enabled")
    public Map<String, String> setEnabled(@PathVariable String skillId,
                                          @RequestBody SkillEnabledRequest request) {
        Long userId = UserContext.getUserId();
        skillService.setSkillEnabled(userId, skillId, request.enabled());
        agentPool.invalidateAll(userId);
        return Map.of("message", request.enabled() ? "技能已启用" : "技能已禁用");
    }

    /**
     * 删除用户安装的技能
     */
    @DeleteMapping("/{skillId}")
    public Map<String, String> delete(@PathVariable String skillId) {
        Long userId = UserContext.getUserId();
        skillService.deleteSkill(skillId, userId);
        agentPool.invalidateAll(userId);
        return Map.of("message", "技能已删除");
    }
}
