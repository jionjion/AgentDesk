package top.jionjion.agentdesk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.jionjion.agentdesk.dto.*;
import top.jionjion.agentdesk.entity.User;
import top.jionjion.agentdesk.entity.UserSettings;
import top.jionjion.agentdesk.repository.UserRepository;
import top.jionjion.agentdesk.repository.UserSettingsRepository;
import top.jionjion.agentdesk.security.UserContext;

/**
 * 设置业务逻辑
 */
@Service
public class SettingsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    public SettingsService(UserRepository userRepository,
                           UserSettingsRepository userSettingsRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 获取当前用户的全部设置, 不存在则返回默认值
     */
    public SettingsResponse getAllSettings() {
        Long userId = UserContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        ProfileDto profile = ProfileDto.from(user);

        UserSettings entity = userSettingsRepository.findByUserId(userId).orElse(null);
        ModelSettingsDto model = (entity != null) ? parseModel(entity.getSettings()) : ModelSettingsDto.defaults();
        AppSettingsDto app = (entity != null) ? parseApp(entity.getSettings()) : AppSettingsDto.defaults();

        return new SettingsResponse(profile, model, app);
    }

    /**
     * 修改个人资料 (昵称/头像)
     */
    @Transactional
    public ProfileDto updateProfile(UpdateProfileRequest request) {
        if (request.nickname() == null || request.nickname().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称不能为空");
        }
        if (request.nickname().length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称最长128字符");
        }
        if (request.avatar() != null && request.avatar().length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像URL最长512字符");
        }

        Long userId = UserContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        user.setNickname(request.nickname());
        user.setAvatar(request.avatar());
        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);

        return ProfileDto.from(user);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        if (request.oldPassword() == null || request.oldPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不能为空");
        }
        if (request.newPassword() == null || request.newPassword().length() < 6 || request.newPassword().length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码长度需为6-64字符");
        }

        Long userId = UserContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);
    }

    /**
     * 修改模型配置
     */
    @Transactional
    public ModelSettingsDto updateModelSettings(ModelSettingsDto dto) {
        validateModelSettings(dto);

        Long userId = UserContext.getUserId();
        UserSettings entity = getOrCreateSettings(userId);

        ObjectNode root = parseSettingsJson(entity.getSettings());
        root.set("model", MAPPER.valueToTree(dto));
        entity.setSettings(toJson(root));
        entity.setUpdatedAt(System.currentTimeMillis());
        userSettingsRepository.save(entity);

        return dto;
    }

    /**
     * 修改应用偏好
     */
    @Transactional
    public AppSettingsDto updateAppSettings(AppSettingsDto dto) {
        validateAppSettings(dto);

        Long userId = UserContext.getUserId();
        UserSettings entity = getOrCreateSettings(userId);

        ObjectNode root = parseSettingsJson(entity.getSettings());
        root.set("app", MAPPER.valueToTree(dto));
        entity.setSettings(toJson(root));
        entity.setUpdatedAt(System.currentTimeMillis());
        userSettingsRepository.save(entity);

        return dto;
    }

    /**
     * 获取指定用户的模型配置 (供 AgentFactory 调用)
     */
    public ModelSettingsDto getModelSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .map(entity -> parseModel(entity.getSettings()))
                .orElse(ModelSettingsDto.defaults());
    }

    // ==================== 内部方法 ====================

    private UserSettings getOrCreateSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> new UserSettings(userId, "{}"));
    }

    private ObjectNode parseSettingsJson(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
            return MAPPER.createObjectNode();
        } catch (JsonProcessingException e) {
            return MAPPER.createObjectNode();
        }
    }

    private ModelSettingsDto parseModel(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode modelNode = root.get("model");
            if (modelNode != null) {
                return MAPPER.treeToValue(modelNode, ModelSettingsDto.class);
            }
        } catch (JsonProcessingException ignored) {
        }
        return ModelSettingsDto.defaults();
    }

    private AppSettingsDto parseApp(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode appNode = root.get("app");
            if (appNode != null) {
                return MAPPER.treeToValue(appNode, AppSettingsDto.class);
            }
        } catch (JsonProcessingException ignored) {
        }
        return AppSettingsDto.defaults();
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private void validateModelSettings(ModelSettingsDto dto) {
        if (dto.modelName() == null || dto.modelName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型名称不能为空");
        }
        if (dto.modelName().length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型名称最长64字符");
        }
        if (dto.temperature() != null && (dto.temperature() < 0.0 || dto.temperature() > 2.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "temperature 范围: 0.0 ~ 2.0");
        }
        if (dto.maxTokens() != null && (dto.maxTokens() < 1 || dto.maxTokens() > 32768)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxTokens 范围: 1 ~ 32768");
        }
        if (dto.topP() != null && (dto.topP() < 0.0 || dto.topP() > 1.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topP 范围: 0.0 ~ 1.0");
        }
        if (dto.systemPrompt() != null && dto.systemPrompt().length() > 4096) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统提示词最长4096字符");
        }
    }

    private void validateAppSettings(AppSettingsDto dto) {
        if (dto.theme() != null && !dto.theme().matches("^(light|dark|auto)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "theme 可选值: light, dark, auto");
        }
        if (dto.language() != null && !dto.language().matches("^(zh-CN|en-US)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "language 可选值: zh-CN, en-US");
        }
        if (dto.sendKey() != null && !dto.sendKey().matches("^(Enter|Ctrl\\+Enter)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sendKey 可选值: Enter, Ctrl+Enter");
        }
        if (dto.fontSize() != null && (dto.fontSize() < 12 || dto.fontSize() > 24)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fontSize 范围: 12 ~ 24");
        }
    }
}
