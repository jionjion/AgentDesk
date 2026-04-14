package top.jionjion.agentdesk.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.SkillDefinitionDto;
import top.jionjion.agentdesk.dto.SkillResponseDto;
import top.jionjion.agentdesk.entity.Skill;
import top.jionjion.agentdesk.entity.UserSkillPreference;
import top.jionjion.agentdesk.repository.SkillRepository;
import top.jionjion.agentdesk.repository.UserSkillPreferenceRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 技能业务逻辑
 *
 * @author Jion
 */
@Service
public class SkillService {

    private static final Set<String> ALLOWED_TOOLS = Set.of("FileTools", "CalculateTools");
    private static final int MAX_ENABLED_SKILLS = 20;

    private final SkillRepository skillRepository;
    private final UserSkillPreferenceRepository preferenceRepository;

    public SkillService(SkillRepository skillRepository,
                        UserSkillPreferenceRepository preferenceRepository) {
        this.skillRepository = skillRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * 列出用户可见的所有技能（内置 + 用户安装），含启用状态
     */
    public List<SkillResponseDto> listSkills(Long userId) {
        List<Skill> skills = skillRepository.findByBuiltinTrueOrUserId(userId);
        Map<String, Boolean> prefs = getPreferenceMap(userId);

        return skills.stream()
                .map(s -> toResponse(s, prefs.getOrDefault(s.getId(), s.isBuiltin())))
                .toList();
    }

    /**
     * 获取单个技能详情
     */
    public SkillResponseDto getSkill(String skillId, Long userId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "技能不存在"));

        // 用户只能查看内置技能或自己安装的技能
        if (!skill.isBuiltin() && !userId.equals(skill.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "技能不存在");
        }

        Map<String, Boolean> prefs = getPreferenceMap(userId);
        return toResponse(skill, prefs.getOrDefault(skillId, skill.isBuiltin()));
    }

    /**
     * 获取用户启用的技能列表（AgentFactory 调用）
     */
    public List<Skill> getEnabledSkills(Long userId) {
        List<Skill> allSkills = skillRepository.findByBuiltinTrueOrUserId(userId);
        Map<String, Boolean> prefs = getPreferenceMap(userId);

        return allSkills.stream()
                .filter(s -> prefs.getOrDefault(s.getId(), s.isBuiltin()))
                .toList();
    }

    /**
     * 从 Electron 同步/上传技能定义（upsert）
     */
    @Transactional
    public SkillResponseDto syncSkill(SkillDefinitionDto dto, Long userId) {
        validateSkillDefinition(dto);

        // 不能覆盖内置技能
        if (skillRepository.existsByIdAndBuiltinTrue(dto.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能覆盖内置技能: " + dto.id());
        }

        Skill skill = skillRepository.findById(dto.id()).orElse(null);
        long now = System.currentTimeMillis();

        if (skill != null) {
            // 更新: 只能更新自己的技能
            if (!userId.equals(skill.getUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权修改此技能");
            }
            updateSkillFromDto(skill, dto);
            skill.setUpdatedAt(now);
        } else {
            // 新建
            skill = new Skill();
            skill.setId(dto.id());
            updateSkillFromDto(skill, dto);
            skill.setBuiltin(false);
            skill.setUserId(userId);
            skill.setCreatedAt(now);
            skill.setUpdatedAt(now);
        }

        skillRepository.save(skill);

        // 新技能默认启用
        if (preferenceRepository.findByUserIdAndSkillId(userId, dto.id()).isEmpty()) {
            preferenceRepository.save(new UserSkillPreference(userId, dto.id(), true));
        }

        return toResponse(skill, true);
    }

    /**
     * 启用/禁用技能
     */
    @Transactional
    public void setSkillEnabled(Long userId, String skillId, boolean enabled) {
        // 验证技能存在且用户可见
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "技能不存在"));
        if (!skill.isBuiltin() && !userId.equals(skill.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "技能不存在");
        }

        // 检查启用上限
        if (enabled) {
            long enabledCount = getEnabledSkills(userId).size();
            if (enabledCount >= MAX_ENABLED_SKILLS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "最多启用 " + MAX_ENABLED_SKILLS + " 个技能");
            }
        }

        UserSkillPreference pref = preferenceRepository.findByUserIdAndSkillId(userId, skillId)
                .orElse(new UserSkillPreference(userId, skillId, enabled));
        pref.setEnabled(enabled);
        preferenceRepository.save(pref);
    }

    /**
     * 删除用户安装的技能
     */
    @Transactional
    public void deleteSkill(String skillId, Long userId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "技能不存在"));

        if (skill.isBuiltin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能删除内置技能");
        }
        if (!userId.equals(skill.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除此技能");
        }

        preferenceRepository.deleteByUserIdAndSkillId(userId, skillId);
        skillRepository.delete(skill);
    }

    // ─── 内部方法 ───

    private Map<String, Boolean> getPreferenceMap(Long userId) {
        return preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserSkillPreference::getSkillId, UserSkillPreference::isEnabled));
    }

    private void validateSkillDefinition(SkillDefinitionDto dto) {
        if (dto.id() == null || !dto.id().matches("^[a-z0-9-]+$") || dto.id().length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "技能 ID 只能包含小写字母、数字和连字符, 最长 64 字符");
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "技能名称不能为空");
        }
        if (dto.description() == null || dto.description().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "技能描述不能为空");
        }
        if (dto.systemPrompt() == null || dto.systemPrompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统提示词不能为空");
        }
        if (dto.systemPrompt().length() > 8192) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统提示词最长 8192 字符");
        }
        if (dto.maxIters() != null && (dto.maxIters() < 1 || dto.maxIters() > 10)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxIters 范围 1~10");
        }
        if (dto.tools() != null) {
            for (String tool : dto.tools()) {
                if (!ALLOWED_TOOLS.contains(tool)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的工具: " + tool);
                }
            }
        }
    }

    private void updateSkillFromDto(Skill skill, SkillDefinitionDto dto) {
        skill.setName(dto.name());
        skill.setDescription(dto.description());
        skill.setAuthor(dto.author() != null ? dto.author() : "User");
        skill.setVersion(dto.version() != null ? dto.version() : "1.0.0");
        skill.setCategory(dto.category() != null ? dto.category() : "other");
        skill.setTags(dto.tags() != null ? dto.tags() : List.of());
        skill.setIcon(dto.icon());
        skill.setBgColor(dto.bgColor());
        skill.setSysPrompt(dto.systemPrompt());
        skill.setMaxIters(dto.maxIters() != null ? dto.maxIters() : 3);
        skill.setTools(dto.tools() != null ? dto.tools() : List.of());
    }

    private SkillResponseDto toResponse(Skill skill, boolean enabled) {
        return new SkillResponseDto(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getAuthor(),
                skill.getVersion(),
                skill.getCategory(),
                skill.getTags(),
                skill.getIcon(),
                skill.getBgColor(),
                skill.getSysPrompt(),
                skill.getMaxIters(),
                skill.getTools(),
                skill.isBuiltin(),
                enabled
        );
    }
}
