package top.jionjion.agentdesk.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.jionjion.agentdesk.dto.*;
import top.jionjion.agentdesk.service.SettingsService;

import java.util.Map;

/**
 * 设置控制器: 个人资料/模型配置/应用偏好
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
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

    @PutMapping("/model")
    public ModelSettingsDto updateModel(@RequestBody ModelSettingsDto request) {
        return settingsService.updateModelSettings(request);
    }

    @PutMapping("/app")
    public AppSettingsDto updateApp(@RequestBody AppSettingsDto request) {
        return settingsService.updateAppSettings(request);
    }
}
