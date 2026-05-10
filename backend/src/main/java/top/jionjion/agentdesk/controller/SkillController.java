package top.jionjion.agentdesk.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.dto.skill.SkillDefinitionDto;
import top.jionjion.agentdesk.dto.skill.SkillEnabledRequest;
import top.jionjion.agentdesk.dto.skill.SkillResponseDto;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.SkillPackageService;
import top.jionjion.agentdesk.service.SkillService;

import java.util.List;
import java.util.Map;

/**
 * 技能控制器: 技能的增删改查、启用/禁用、ZIP包安装
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;
    private final SkillPackageService skillPackageService;
    private final AgentPool agentPool;

    public SkillController(SkillService skillService,
                           SkillPackageService skillPackageService,
                           AgentPool agentPool) {
        this.skillService = skillService;
        this.skillPackageService = skillPackageService;
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
     * 上传并安装 ZIP 技能包
     */
    @PostMapping("/install")
    public SkillResponseDto install(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();

        // 1. 解压安装技能包到文件系统
        SkillPackageService.SkillInstallResult result = skillPackageService.install(file, userId);

        // 2. 注册到数据库
        SkillResponseDto response = skillService.registerInstalledPackage(
                result.id(), result.name(), result.description(), userId);

        // 3. 使 Agent 缓存失效, 重新加载技能
        agentPool.invalidateAll(userId);

        return response;
    }

    /**
     * 获取技能包的资源文件列表
     */
    @GetMapping("/{skillId}/resources")
    public List<String> getResources(@PathVariable String skillId) {
        Long userId = UserContext.getUserId();
        return skillPackageService.getSkillResources(skillId, userId);
    }

    /**
     * 读取技能包中某个资源文件的内容
     */
    @GetMapping("/{skillId}/resources/{*resourcePath}")
    public Map<String, String> readResource(@PathVariable String skillId,
                                            @PathVariable String resourcePath) {
        Long userId = UserContext.getUserId();
        // Spring 6 的 {*path} 会包含前导 /, 需要去除
        String cleanPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        String content = skillPackageService.readResource(skillId, userId, cleanPath);
        return Map.of("content", content);
    }

    /**
     * 从 Electron 同步/上传技能定义（upsert）— 向后兼容 prompt 型
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
     * 删除用户安装的技能（同时删除文件系统上的技能包）
     */
    @DeleteMapping("/{skillId}")
    public Map<String, String> delete(@PathVariable String skillId) {
        Long userId = UserContext.getUserId();
        skillService.deleteSkill(skillId, userId);
        agentPool.invalidateAll(userId);
        return Map.of("message", "技能已删除");
    }
}
