package top.jionjion.agentdesk.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.agent.AgentPool;
import top.jionjion.agentdesk.dto.*;
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

    public SettingsController(SettingsService settingsService, AgentPool agentPool) {
        this.settingsService = settingsService;
        this.agentPool = agentPool;
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
}
