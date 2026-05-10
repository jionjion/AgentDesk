package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.agent.core.AgentPool;
import top.jionjion.agentdesk.dto.auth.ChangePasswordRequest;
import top.jionjion.agentdesk.dto.auth.ProfileDto;
import top.jionjion.agentdesk.dto.auth.UpdateProfileRequest;
import top.jionjion.agentdesk.dto.settings.AppSettingsDto;
import top.jionjion.agentdesk.dto.settings.KnowledgeSettingsDto;
import top.jionjion.agentdesk.dto.settings.ModelSettingsDto;
import top.jionjion.agentdesk.dto.settings.ObsidianSettingsDto;
import top.jionjion.agentdesk.dto.settings.SettingsResponse;
import top.jionjion.agentdesk.entity.KnowledgeSettings;
import top.jionjion.agentdesk.repository.KnowledgeSettingsRepository;
import top.jionjion.agentdesk.security.UserContext;
import top.jionjion.agentdesk.service.SettingsService;

import java.util.Map;

/**
 * 设置控制器: 个人资料/模型配置/应用偏好
 *
 * @author Jion
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final AgentPool agentPool;
    private final KnowledgeSettingsRepository knowledgeSettingsRepository;

    public SettingsController(SettingsService settingsService, AgentPool agentPool,
                              KnowledgeSettingsRepository knowledgeSettingsRepository) {
        this.settingsService = settingsService;
        this.agentPool = agentPool;
        this.knowledgeSettingsRepository = knowledgeSettingsRepository;
    }

    @GetMapping
    public SettingsResponse getAll() {
        return settingsService.getAllSettings();
    }

    @PutMapping("/profile")
    public ProfileDto updateProfile(@RequestBody UpdateProfileRequest request) {
        return settingsService.updateProfile(request);
    }

    @PostMapping("/avatar")
    public ProfileDto uploadAvatar(@RequestParam("file") MultipartFile file) {
        return settingsService.uploadAvatar(file);
    }

    @PutMapping("/password")
    public Map<String, String> changePassword(@RequestBody ChangePasswordRequest request) {
        settingsService.changePassword(request);
        return Map.of("message", "密码修改成功");
    }

    /**
     * 更新模型设置
     * 切换模型后使所有 Agent 失效, 下次对话时用新模型重建。
     */
    @PutMapping("/model")
    public ModelSettingsDto updateModel(@RequestBody ModelSettingsDto request) {
        ModelSettingsDto result = settingsService.updateModelSettings(request);
        // 使所有会话的 Agent 失效
        agentPool.invalidateAll(UserContext.getUserId());
        return result;
    }

    @PutMapping("/app")
    public AppSettingsDto updateApp(@RequestBody AppSettingsDto request) {
        return settingsService.updateAppSettings(request);
    }

    @PutMapping("/obsidian")
    public ObsidianSettingsDto updateObsidian(@RequestBody ObsidianSettingsDto request) {
        return settingsService.updateObsidianSettings(request);
    }

    /**
     * 获取脱敏的 DashScope API Key
     */
    @GetMapping("/provider-keys/dashscope")
    public Map<String, String> getDashScopeKey() {
        String masked = settingsService.getMaskedDashScopeKey(UserContext.getUserId());
        return Map.of("maskedKey", masked != null ? masked : "");
    }

    /**
     * 更新 DashScope API Key
     */
    @PutMapping("/provider-keys/dashscope")
    public Map<String, String> updateDashScopeKey(@RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API Key 不能为空");
        }
        settingsService.updateDashScopeApiKey(UserContext.getUserId(), apiKey);
        // Key 变更后也需要重建 Agent
        agentPool.invalidateAll(UserContext.getUserId());
        return Map.of("message", "API Key 已更新");
    }

    /**
     * 删除用户自定义 DashScope API Key（回退到系统默认）
     */
    @DeleteMapping("/provider-keys/dashscope")
    public Map<String, String> deleteDashScopeKey() {
        settingsService.updateDashScopeApiKey(UserContext.getUserId(), "");
        agentPool.invalidateAll(UserContext.getUserId());
        return Map.of("message", "已恢复使用系统默认密钥");
    }

    /**
     * 获取知识库检索设置
     */
    @GetMapping("/knowledge")
    public KnowledgeSettingsDto getKnowledgeSettings() {
        Long userId = UserContext.getUserId();
        return knowledgeSettingsRepository.findById(userId)
                .map(s -> new KnowledgeSettingsDto(s.isEnabled(), s.getTopK(), s.getScoreThreshold()))
                .orElse(KnowledgeSettingsDto.defaults());
    }

    /**
     * 更新知识库检索设置
     */
    @PutMapping("/knowledge")
    public KnowledgeSettingsDto updateKnowledgeSettings(@RequestBody KnowledgeSettingsDto dto) {
        if (dto.topK() != null && (dto.topK() < 1 || dto.topK() > 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topK 范围: 1 ~ 20");
        }
        if (dto.scoreThreshold() != null && (dto.scoreThreshold() < 0.0 || dto.scoreThreshold() > 1.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scoreThreshold 范围: 0.0 ~ 1.0");
        }

        Long userId = UserContext.getUserId();
        KnowledgeSettings entity = knowledgeSettingsRepository.findById(userId)
                .orElseGet(() -> new KnowledgeSettings(userId));

        if (dto.enabled() != null) entity.setEnabled(dto.enabled());
        if (dto.topK() != null) entity.setTopK(dto.topK());
        if (dto.scoreThreshold() != null) entity.setScoreThreshold(dto.scoreThreshold());
        entity.setUpdatedAt(System.currentTimeMillis());

        knowledgeSettingsRepository.save(entity);
        return new KnowledgeSettingsDto(entity.isEnabled(), entity.getTopK(), entity.getScoreThreshold());
    }
}
