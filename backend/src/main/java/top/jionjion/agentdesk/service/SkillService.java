package top.jionjion.agentdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.skill.SkillDefinitionDto;
import top.jionjion.agentdesk.dto.skill.SkillResponseDto;
import top.jionjion.agentdesk.entity.Skill;
import top.jionjion.agentdesk.entity.UserSkillPreference;
import top.jionjion.agentdesk.repository.SkillRepository;
import top.jionjion.agentdesk.repository.UserSkillPreferenceRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 技能业务逻辑
 * <p>
 * 支持两种技能类型:
 * - prompt 型: 传统提示词驱动的技能（向后兼容）
 * - package 型: 脚本化技能包，存储在文件系统，由 SkillBox 加载
 *
 * @author Jion
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private static final Set<String> ALLOWED_TOOLS = Set.of("FileTools", "CalculateTools");
    private static final int MAX_ENABLED_SKILLS = 20;
    private static final int MAX_ITERS = 10;
    private static final String SKILL_TYPE_PACKAGE = "package";
    private static final String SKILL_TYPE_PROMPT = "prompt";
    private static final String SKILL_ID_PATTERN = "^[a-z0-9-]+$";
    private static final int MAX_SKILL_ID_LENGTH = 64;
    private static final int MAX_SYS_PROMPT_LENGTH = 8192;

    private final SkillRepository skillRepository;
    private final UserSkillPreferenceRepository preferenceRepository;
    private final String skillsBaseDir;

    public SkillService(SkillRepository skillRepository,
                        UserSkillPreferenceRepository preferenceRepository,
                        @Value("${agentdesk.skills.base-dir}") String skillsBaseDir) {
        this.skillRepository = skillRepository;
        this.preferenceRepository = preferenceRepository;
        this.skillsBaseDir = skillsBaseDir;
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
     * 注册已安装的技能包到数据库（由 SkillPackageService 安装后调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillResponseDto registerInstalledPackage(String skillId, String name, String description, Long userId) {
        // 不能覆盖内置技能
        if (skillRepository.existsByIdAndBuiltinTrue(skillId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能覆盖内置技能: " + skillId);
        }

        Skill skill = skillRepository.findById(skillId).orElse(null);
        long now = System.currentTimeMillis();

        if (skill != null) {
            if (!userId.equals(skill.getUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权修改此技能");
            }
            skill.setName(name != null ? name : skill.getName());
            skill.setDescription(description != null ? description : skill.getDescription());
            skill.setSkillType(SKILL_TYPE_PACKAGE);
            skill.setInstallPath(Path.of(skillsBaseDir, String.valueOf(userId), skillId).toString());
            skill.setUpdatedAt(now);
        } else {
            skill = new Skill();
            skill.setId(skillId);
            skill.setName(name != null ? name : skillId);
            skill.setDescription(description != null ? description : "用户安装的技能包");
            skill.setAuthor("User");
            skill.setVersion("1.0.0");
            skill.setCategory("other");
            skill.setTags(List.of());
            // package 型技能不需要 sysPrompt, 内容在 SKILL.md 中
            skill.setSysPrompt("");
            skill.setMaxIters(5);
            skill.setTools(List.of());
            skill.setSkillType(SKILL_TYPE_PACKAGE);
            skill.setInstallPath(Path.of(skillsBaseDir, String.valueOf(userId), skillId).toString());
            skill.setBuiltin(false);
            skill.setUserId(userId);
            skill.setCreatedAt(now);
            skill.setUpdatedAt(now);
        }

        skillRepository.save(skill);

        // 新技能默认启用
        if (preferenceRepository.findByUserIdAndSkillId(userId, skillId).isEmpty()) {
            preferenceRepository.save(new UserSkillPreference(userId, skillId, true));
        }

        return toResponse(skill, true);
    }

    /**
     * 从 Electron 同步/上传技能定义（upsert）— 向后兼容 prompt 型技能
     */
    @Transactional(rollbackFor = Exception.class)
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
            skill.setSkillType(SKILL_TYPE_PROMPT);
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
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
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

        // 如果是 package 型, 同时删除文件系统上的技能目录
        if (SKILL_TYPE_PACKAGE.equals(skill.getSkillType())) {
            Path skillDir = Path.of(skillsBaseDir, String.valueOf(userId), skillId);
            if (Files.exists(skillDir)) {
                try {
                    deleteDirectory(skillDir);
                } catch (Exception e) {
                    log.warn("删除技能目录失败: {}", skillDir, e);
                }
            }
        }
    }

    // ─── 内部方法 ───

    private Map<String, Boolean> getPreferenceMap(Long userId) {
        return preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserSkillPreference::getSkillId, UserSkillPreference::isEnabled));
    }

    private void validateSkillDefinition(SkillDefinitionDto dto) {
        if (dto.id() == null || !dto.id().matches(SKILL_ID_PATTERN) || dto.id().length() > MAX_SKILL_ID_LENGTH) {
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
        if (dto.systemPrompt().length() > MAX_SYS_PROMPT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统提示词最长 8192 字符");
        }
        if (dto.maxIters() != null && (dto.maxIters() < 1 || dto.maxIters() > MAX_ITERS)) {
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
                enabled,
                skill.getSkillType() != null ? skill.getSkillType() : "prompt",
                skill.getInstallPath()
        );
    }

    private void deleteDirectory(Path dir) throws Exception {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }
}
